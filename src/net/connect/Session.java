package net.connect;

import java.io.*;
import java.net.InetAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Session {

    private static final long RESEND_DELAY = 4096;
    private static final int BUFFER_SIZE = 1024 * 10;

    protected SessionedSocket socket;

    private final int sessionID;

    private final InetAddress remote;
    private final int remotePort;

    // objects to use as locks for synchronizing things when necessary
    protected final Object sendLock;
    protected final Object receiveLock;

    // Queues for messages.
    // sendQueue is a queue that stores send packets until they are acknowledged
    // receiveQueue is a SynchronousQueue used to hand off packets from SessionedSocket
    //  receiver thread to Session receiver thread
    protected BlockingQueue<SessionPacket> sendQueue;
    protected SynchronousQueue<SessionPacket> receiveQueue;

    // Threads for sending and receiving data
    protected Thread sender;
    protected Thread receiver;

    private long lastKeepAlive;
    private AtomicLong receivedKeepAlive;

    // AtomicIntegers to store sending acknowledge and window
    protected AtomicInteger sendAck;    // tracks IDs for packets to send
    protected AtomicInteger sendWindow; // stores number of packets that can be sent at once

    // AtomicIntegers to store receiving acknowledge
    protected AtomicInteger receiveAck;     // stores id of last packet received

    // AtomicBooleans to determine if remote and local 'sockets' are opened
    protected AtomicBoolean sendOpened;
    protected AtomicBoolean receiveOpened;
    protected AtomicBoolean remoteOpened;

    private AtomicBoolean sentInit;     // true if a SESSION_INIT packet was sent
    private AtomicBoolean connected;    // true if connected to remote

    // piped streams for turning input into output and buffering data
    private PipedInputStream outputBufferStream;
    private PipedOutputStream outputStream;

    private PipedInputStream inputStream;
    private PipedOutputStream inputBufferStream;

    public Session(SessionedSocket socket, int id, InetAddress remote, int remotePort) {
        this.socket = socket;
        this.sessionID = id;
        this.remote = remote;
        this.remotePort = remotePort;

        this.sendLock = new Object();
        this.outputBufferStream = new PipedInputStream(BUFFER_SIZE);
        try {
            this.outputStream = new PipedOutputStream(this.outputBufferStream);
        } catch (IOException e) {
            System.err.println("[Session] IOException while linking piped streams");
            e.printStackTrace();
        }
        this.sendQueue = new PriorityBlockingQueue<>(10, (p1, p2) -> p1.getMessageID() - p2.getMessageID());
        this.sender = new Thread(this::sender);
        this.sendAck = new AtomicInteger(0);
        this.sendWindow = new AtomicInteger(1);
        this.remoteOpened = new AtomicBoolean(true);
        this.sendOpened = new AtomicBoolean(true);

        // connection metrics
        this.receivedKeepAlive = new AtomicLong();
        this.connected = new AtomicBoolean(false);
        this.sentInit = new AtomicBoolean(false);

        this.receiveLock = new Object();
        this.inputStream = new PipedInputStream(BUFFER_SIZE);
        this.receiveQueue = new SynchronousQueue<>();
        this.receiver = new Thread(this::receiver);
        this.receiveAck = new AtomicInteger(0);
        this.receiveOpened = new AtomicBoolean(true);
        try {
            this.inputBufferStream = new PipedOutputStream(this.inputStream);
        } catch (IOException e) {
            System.err.println("[Session] IOException while linking piped streams");
            e.printStackTrace();
        }

        this.sender.setName("[Session][sender]");
        this.receiver.setName("[Session][receiver]");
        this.sender.setDaemon(true);
        this.receiver.setDaemon(true);
    }

    public void open() {
        this.receiver.start();
    }

    public void close() throws IOException {
        this.sendOpened.set(false);
        this.outputStream.close();
    }

    public boolean isInputOpened() {
        return this.receiveOpened.get();
    }

    public boolean isInputShutdown() {
        return !(this.receiveOpened.get() || this.receiver.isAlive());
    }

    public boolean isOutputOpened() {
        return this.sendOpened.get();
    }

    public boolean isOutputShutdown() {
        return !(this.sendOpened.get() || this.sender.isAlive());
    }

    public boolean isConnected() {
        return this.connected.get();
    }

    private void sender() {
        // while connected
        byte[] temp = new byte[SessionedSocket.PACKET_LENGTH - SessionPacket.HEADER_OVERHEAD];
        while (this.remoteOpened.get() && (this.connected.get() || this.sentInit.get())) {
            if (System.currentTimeMillis() - this.receivedKeepAlive.get() > 5 * RESEND_DELAY) {
                // other side probably disconnected
                // do something about that
            }

            this.sendKeepAlive();

            // if there are unacknowledged packets that were sent, resend them
            // if sent packets were acknowledged, remove them from the queue
            synchronized (this.sendLock) {
                int i = 0; // i is the amount of data sent
                for (SessionPacket packet : this.sendQueue) {
                    if (packet.getMessageID() <= this.sendAck.get()) {
                        this.sendQueue.remove(packet);
                        continue;
                    }
                    if (i + packet.getPayloadSize() >= this.sendWindow.get()) break;
                    // resend packet, without adding it to the queue again
                    this.sendPacket(packet, false);
                    i += packet.getPayloadSize();
                }
            }

            // if data available, break it up into packets and send the packets
            // only queue up to 5 packets, to prevent memory leaks
            try {
                while (this.outputBufferStream.available() > 0 && sendQueue.size() < 5) {
                    int size = this.outputBufferStream.read(temp, 0, temp.length);
                    this.sendPacket(this.createPacket(SessionPacket.PacketType.MESSAGE, temp, size));
                }

                // if closed and output buffer is empty
                if (this.outputBufferStream.available() == 0 && !this.sendOpened.get()) {
                    System.out.print("[Session][sender] Buffered data: ");
                    System.out.println(this.outputBufferStream.available());
                    System.out.print("\tsendOpened = ");
                    System.out.println(this.sendOpened.get());
                    this.sendPacket(this.createPacket(SessionPacket.PacketType.CLOSE, true));
                }

            } catch (IOException e) {
                System.err.println("[Session][sender] IOException while building packets");
                e.printStackTrace();
            }

            // wait for packet resend delay, or until interrupted
            try {
                synchronized (this.sendLock) {
                    this.sendLock.wait(RESEND_DELAY);
                }

            } catch (InterruptedException e) {
                if (!this.remoteOpened.get()) {
                    // remote closed
                    break;
                }
                System.err.println("[Session][sender] Interrupted while waiting");
            }
        }

        // if remote closed, dump queue
        if (!this.remoteOpened.get()) {
            this.sendQueue.clear();
        }
    }

    protected SessionPacket createPacket(SessionPacket.PacketType type, boolean order) {
        return new SessionPacket(this.sessionID, order ? this.sendAck.incrementAndGet() : 0,
                type, 0, 0, new byte[]{}, this.remote, this.remotePort);
    }

    protected SessionPacket createPacket(SessionPacket.PacketType type, byte[] data) {
        // only message and close packets need to have ordering constraints
        return new SessionPacket(this.sessionID, type == SessionPacket.PacketType.MESSAGE ||
                type == SessionPacket.PacketType.CLOSE ? this.sendAck.incrementAndGet() : 0,
                type, 0, 0, data, this.remote, this.remotePort);
    }

    protected SessionPacket createPacket(SessionPacket.PacketType type, byte[] data, int len) {
        // only message and close packets need to have ordering constraints
        return new SessionPacket(this.sessionID, type == SessionPacket.PacketType.MESSAGE ||
                type == SessionPacket.PacketType.CLOSE ? this.sendAck.incrementAndGet() : 0,
                type, 0, 0, data, this.remote, this.remotePort, len);
    }

    protected void sendPacket(SessionPacket packet) {
        this.sendPacket(packet, packet.getType() == SessionPacket.PacketType.MESSAGE);
    }

    protected void sendPacket(SessionPacket packet, boolean queue) {
        if (queue && this.sendOpened.get()) {
            synchronized (this.sendLock) {
                try {
                    this.sendQueue.put(packet);

                } catch (InterruptedException e) {
                    // should be impossible
                    System.err.println("[Session][sendPacket] Interrupted while adding packet to send queue");
                    e.printStackTrace();
                }
            }
        }
        try {
            packet.setAck(this.receiveAck.get());
            packet.setWindow(Math.max((BUFFER_SIZE - this.inputStream.available()) - SessionedSocket.PACKET_LENGTH, 0));
            this.socket.send(packet);

        } catch (IOException e) {
            System.err.println("[Session][sendPacket] IOException while trying to send packet");
            e.printStackTrace();
        }
    }

    private void receiver() {
        // while opened to receiving packets
        while (this.receiveOpened.get() && (this.remoteOpened.get() || this.sentInit.get())) {
            // get a packet
            SessionPacket packet = null;
            try {
                packet = this.receiveQueue.take();

            } catch (InterruptedException e) {
                if (this.receiveOpened.get()) continue;
                break;
            }

            // make sure that packet was received in order
            if (packet.getType() == SessionPacket.PacketType.MESSAGE && packet.getMessageID() != this.receiveAck.get() + 1) {
                System.err.println("[Session][receiver] Packet received out of order");
                continue;
            }

            // apply relevant header details from received packet
            this.sendWindow.set(packet.getWindow());

            // update acknowledgement tracking
            if (this.sendAck.get() < packet.getAck()) this.sendAck.set(packet.getAck());

            // ignore everything else for keep alive packets
            if (packet.getType() == SessionPacket.PacketType.KEEP_ALIVE) {
                this.receivedKeepAlive.set(System.currentTimeMillis());
                continue;
            }

            // note that packet was received for future acknowledgement
            this.receiveAck.set(packet.getMessageID());

            //make sure sender thread is running to send keep-alive (to acknowledge received data even if nothing is sent)
            if (this.sender.getState() == Thread.State.NEW){
                //System.out.println("[Session][receiver] Received non keep-alive packet, starting sender thread.");
                this.sender.start();
            }

            // handle packet
            boolean handled = false;
            SessionPacket.PacketType type = packet.getType();
            if (type == SessionPacket.PacketType.MESSAGE) {
                handled = this.onMessage(packet);

            } else if (type == SessionPacket.PacketType.CLOSE) {
                handled = this.onClose(packet);

            } else if (type == SessionPacket.PacketType.INIT_RESPONSE) {
                this.connected.set(true);
                handled = true;

            } else if (type == SessionPacket.PacketType.CLOSE_ACK) {
                handled = this.onCloseAck(packet);

            } else {
                handled = this.onOther(packet);
            }

            if (!handled) System.err.println("[Session][receiver] Packet not successfully handled");
        }
    }

    protected boolean onMessage(SessionPacket packet) {
        synchronized (this.receiveLock) {
            try {
                this.inputBufferStream.write(packet.getPayload(), 0, packet.getPayloadSize());

            } catch (IOException e) {
                System.err.println("[Session][onMessage] IOException writing received message to buffer");
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    protected boolean onClose(SessionPacket packet) {
        // acknowledge close
        this.sendPacket(this.createPacket(SessionPacket.PacketType.CLOSE_ACK, false));
        // take note of remote closing
        this.remoteOpened.set(false);
        this.connected.set(false);
        this.socket.sessionClosed(this.sessionID);
        try {
            this.inputBufferStream.close();
        } catch (IOException e) {
            System.err.println("[Session][onClose] IOException while closing input buffer");
            e.printStackTrace();
        }
        return true;
    }

    protected boolean onOther(SessionPacket packet) {
        System.out.print("[Session][onOther] Packet received. type:");
        System.out.println(packet.getType().toString());
        return true;
    }

    protected boolean onCloseAck(SessionPacket packet) {
        this.remoteOpened.set(false);
        this.connected.set(false);
        this.socket.sessionClosed(this.sessionID);
        return true;
    }

    private void sendKeepAlive() {
        if (System.currentTimeMillis() - lastKeepAlive > RESEND_DELAY) {
            this.sendPacket(this.createPacket(SessionPacket.PacketType.KEEP_ALIVE, new byte[]{}, 0), false);
            lastKeepAlive = System.currentTimeMillis();
        }
    }

    public void sendSessionInit() {
        this.sendPacket(this.createPacket(SessionPacket.PacketType.SESSION_INIT, new byte[]{}, 0));
        this.sentInit.set(true);
    }

    public void sendInitResponse() {
        this.sendPacket(this.createPacket(SessionPacket.PacketType.INIT_RESPONSE, new byte[]{}, 0));
    }

    public InputStream inputStream() {
        return this.inputStream;
    }

    public OutputStream outputStream() {
        return this.outputStream;
    }

    protected void onPacket(SessionPacket packet) throws InterruptedException {
        if (this.receiveOpened.get()) {
            this.receiveQueue.put(packet);

        } else {
            System.err.println("[Session][onPacket] Received packet on closed session");
        }
    }
}
