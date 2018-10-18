package net.lib;

import net.common.Constants;
import utils.StreamBuffer;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

public abstract class Socket {

    protected AtomicInteger state;

    protected InetAddress remote;
    protected int port;

    // waitingAck
    /**
     * The ID for the nest packet to send.
     * Incremented when ACK is received.
     */
    protected AtomicInteger messageId;
    protected AtomicInteger acknowledgedId;

    /**
     * The latest window value received in an ACK from remote.
     * Defaults to 1 when Socket is created.
     */
    protected AtomicInteger remoteWindow;

    protected StreamBuffer sendBuffer;

    protected AtomicBoolean waitingAck;
    protected final Object waitingLock;

    protected Thread sender;

    // last packet send
    protected byte[] lastSend;
    protected int offset;
    protected int length;

    // receiving
    /**
     * The ID of the last packet that was received and acknowledged.
     * Used to enforce packet ordering.
     */
    protected AtomicInteger lastReceivedId;

    protected StreamBuffer receiveBuffer;

    protected long lastReceivedTime;

    protected Thread receiver;

    public Socket(InetAddress remote, int port) {
        this.remote = remote;
        this.port = port;
        this.state = new AtomicInteger();

        this.messageId = new AtomicInteger(0);
        this.acknowledgedId = new AtomicInteger(0);
        this.remoteWindow = new AtomicInteger(1);
        this.sendBuffer = new StreamBuffer(Constants.BUFFER_SIZE);
        this.waitingAck = new AtomicBoolean();
        this.waitingLock = new Object();
        this.sender = new Thread(this::sender);
        this.sender.setName("[Socket][sender]");

        this.lastReceivedId = new AtomicInteger();
        this.receiveBuffer = new StreamBuffer(Constants.BUFFER_SIZE);
        this.receiver = new Thread(this::receiver);
        this.receiver.setName("[Socket][receiver]");
    }

    // public API and convenience methods
    public boolean isConnected() {
        return this.state.get() == ESTABLISHED;
    }

    public boolean isClosed() {
        return this.state.get() == CLOSED ||
                this.state.get() == CLOSE_SENT ||
                this.state.get() == CLOSE_RECEIVED;
    }

    public boolean isSendClosed() {
        return !this.sendBuffer.isWriteOpened();
    }

    public boolean isReceiveClosed() {
        return !this.receiveBuffer.isReadOpened();
    }

    public int outputBufferSpace() {
        return this.sendBuffer.capacity();
    }

    public int inputBufferAvailable() {
        return this.receiveBuffer.available();
    }

    public OutputStream outputStream() {
        return this.sendBuffer.getOutputStream();
    }

    public InputStream inputStream() {
        return this.receiveBuffer.getInputStream();
    }

    public void close() {
        try {
            this.sendBuffer.getOutputStream().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // sending system
    protected abstract Object getSocketLock();

    protected abstract DatagramSocket getSocket();

    protected int getWindow() {
        return this.receiveBuffer.capacity() > Constants.PACKET_SIZE ? 1 : 0;
    }

    /**
     * Sends the given data over the Socket. Blocks until the data is sent successfully.
     *
     * @param data the data to send
     * @param off the offset at which to start in data
     * @param len the length of data to send
     */
    protected void sendMessage(byte[] data, int off, int len) throws SocketTimeoutException, IOException {
        if (this.state.get() == CLOSED) throw new IllegalStateException("Cannot send message data when connection is not ESTABLISHED");
        try {
            int id;
            // send packet
            ByteArrayOutputStream dest = new ByteArrayOutputStream();
            DataOutputStream rawOut = new DataOutputStream(dest);
            CheckedOutputStream check = new CheckedOutputStream(rawOut, new CRC32());
            DataOutputStream out = new DataOutputStream(rawOut);
            out.writeInt(PacketType.MESSAGE.value);
            out.writeInt(id = messageId.incrementAndGet());
            out.write(data, off, len);
            rawOut.writeLong(check.getChecksum().getValue());
            this.sendPacket(id, dest.toByteArray(), 0, dest.size());

        } catch (SocketTimeoutException e) {
            throw e;

        } catch (IOException e) {
            System.err.println("[Socket][sendMessage] IOException while trying to send message packet");
            throw e;
        }
    }

    protected void sendSyn() throws SocketTimeoutException {
        this.lastReceivedTime = Math.max(this.lastReceivedTime, System.currentTimeMillis());
        if (this.state.get() >= SYN_SENT) throw new IllegalStateException("SYN packet already sent");
        try {
            int id;
            ByteArrayOutputStream dest = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dest);
            out.writeInt(PacketType.SYN.value);
            out.writeInt(id = this.messageId.incrementAndGet());
            this.sendPacket(id, dest.toByteArray(), 0, dest.size());

        } catch (SocketTimeoutException e) {
            throw e;

        } catch (IOException e) {
            System.err.println("[Socket][sendSync] IOException while sending SYNC packet");
            e.printStackTrace();
        }
    }

    protected void sendAck(int ackId) {
        if (this.state.get() == CLOSED) throw new IllegalStateException("Cannot send ACK on closed socket");
        try {
            ByteArrayOutputStream dest = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dest);
            out.writeInt(PacketType.ACK.value);
            out.writeInt(ackId);
            out.writeInt(this.getWindow());
            this.sendTrivial(dest.toByteArray(), 0, dest.size());
            this.lastReceivedId.set(ackId);

        } catch (IOException e) {
            System.err.println("[Socket][sendAck] IOException while trying to send ACK packet");
            e.printStackTrace();
        }
    }

    protected void sendClose() throws SocketTimeoutException {
        if (this.state.get() != ESTABLISHED && this.state.get() != CLOSE_RECEIVED) throw new IllegalStateException("Cannot send CLOSE packet when connection is not established");
        try {
            int id;
            ByteArrayOutputStream dest = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dest);
            out.writeInt(PacketType.CLOSE.value);
            out.writeInt(id = this.messageId.incrementAndGet());
            this.sendBuffer.getOutputStream().close();
            this.sendPacket(id, dest.toByteArray(), 0, dest.size());

            if (this.state.get() == ESTABLISHED) {
                // if was established, then local initiated close
                this.state.set(CLOSE_SENT);

            } else if (this.state.get() == CLOSE_RECEIVED) {
                // received close, now fully closed
                this.state.set(CLOSED);
                this.onClosed();
            }

        }  catch (SocketTimeoutException e) {
            throw e;

        }catch (IOException e) {
            System.err.println("[Socket][sendClose] IOException while trying to send CLOSE packet");
            e.printStackTrace();
        }
    }

    protected void sendPoke() {
        if (this.state.get() == CLOSED) throw new IllegalStateException("Cannot send POKE on CLOSED socket");
        try {
            int id;
            ByteArrayOutputStream dest = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dest);
            out.writeInt(PacketType.POKE.value);
            out.writeInt(id = this.messageId.incrementAndGet());
            this.sendPacket(id, dest.toByteArray(), 0, dest.size());

        } catch (IOException e) {
            System.err.println("[Socket][sendPoke] IOException while trying to send POKE packet");
            e.printStackTrace();
        }
    }

    /**
     * Sends the given packet, blocking until the packet is sent and acknowledged.
     *
     * @param id the ID number of the packet to send
     * @param packet the data to send in the packet
     * @param off the offset at which relevant packet data starts
     * @param len the length of the data to send
     * @throws IOException if the socket is closed while sending data
     */
    protected void sendPacket(int id, byte[] packet, int off, int len) throws SocketTimeoutException, IOException {
        if (this.state.get() == CLOSED || this.state.get() == CLOSE_SENT) throw new IllegalStateException("Cannot send message data when connection being closed");
        boolean sent = false;
        do {
            if (this.state.get() == CLOSED) throw new IOException("Socket closed while waiting to send");
            if (this.acknowledgedId.get() + 1 == id) {
                // if next in line to be sent
                if (this.waitingAck.compareAndSet(false, true)) {
                    // send packet
                    this.sendTrivial(packet, off, len);

                    // wait for acknowledgement
                    synchronized (this.waitingLock) {
                        while (!sent) {
                            if (this.acknowledgedId.get() == id) {
                                //System.out.println("[Socket][sendPacket] Packet sent and acknowledged!");
                                sent = true;
                                this.waitingAck.set(false);
                                break;

                            } else {
                                try {
                                    this.waitingLock.wait(Constants.RESEND_DELAY);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }

            } else {
                System.out.println("[Socket][sendPacket] Packet not being sent in order");
                try {
                    synchronized (this.waitingLock) {
                        this.waitingLock.wait();
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        } while (!sent);
    }

    protected void sendTrivial(byte[] packet, int off, int len) {
        synchronized (this.getSocketLock()) {
            DatagramPacket p = new DatagramPacket(packet, off, len, this.remote, this.port);

            try {
                this.getSocket().send(p);
                this.lastSend = packet;
                this.offset = off;
                this.length = len;

            } catch (BindException e) {
                System.err.println("[Socket][sendTrivial] BindException while trying to send packet. Remote address: " + this.remote + ":" + this.port);
                e.printStackTrace();

            } catch (IOException e) {
                System.err.println("[Socket][sendTrivial] IOException while sending packet");
                e.printStackTrace();
            }
        }
    }

    protected void sender() {
        byte[] trx = new byte[Constants.PACKET_SIZE];

        while (this.state.get() != CLOSED && this.state.get() != CLOSE_RECEIVED && (this.sendBuffer.available() > 0 || this.sendBuffer.isWriteOpened())) {
            // if remote can accept, send data
            if (this.remoteWindow.get() > 0) {
                try {
                    InputStream src = this.sendBuffer.getInputStream();
                    int amnt = src.read(trx, 0, trx.length); // blocks until at least one byte read
                    if (amnt == -1) break;
                    //System.out.println("[Socket][sender] Sending " + amnt + " bytes");
                    this.sendMessage(trx, 0, amnt); // blocks until data sent or socket closed

                } catch (IOException e) {
                    System.err.println("[Socket][sender] IOException while trying to send data");
                    e.printStackTrace();
                }
            }
        }

        if ((this.state.get() == ESTABLISHED && !this.sendBuffer.isWriteOpened()) || this.state.get() == CLOSE_RECEIVED) {
            try {
                this.sendClose();

            } catch (SocketTimeoutException e) {
                System.err.println("[Socket][sender] Did not receive ACK to CLOSE packet");
                e.printStackTrace();
            }
        }

        //System.out.println("[Socket][sender] Sender thread terminating");
    }

    // receiving system
    protected void onMessage(int id, byte[] data, int off, int len) {
        if (this.enforceOrdering(id)) {
            try {
/*
                Reader reader = new InputStreamReader(new ByteArrayInputStream(data, off, len));
                char[] c = new char[150];
                while (reader.read(c, 0, 100) != -1) System.out.println(c);
*/
                this.receiveBuffer.getOutputStream().write(data, off, len);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            System.err.println("[Socket][onMessage] MESSAGE packet received out of order");
        }
    }

    protected abstract void onSyn(InetAddress remote, int port, int id);

    protected void onAck(int ackId, int window) {
        if (this.acknowledgedId.compareAndSet(ackId - 1, ackId)) {
            // if acknowledged in order
            this.remoteWindow.set(window);
            synchronized (this.waitingLock) {
               this.waitingLock.notifyAll();
            }

        } else {
            // if not acknowledged in order
            System.out.println("[Socket][onAck] ACK received out of order, resending last packet");
            this.sendTrivial(lastSend, offset, length);
        }
    }

    protected void onClose(int id) {
        if (enforceOrdering(id)) {
            if (this.state.get() == ESTABLISHED) {
                this.state.set(CLOSE_RECEIVED);

            } else if (this.state.compareAndSet(CLOSE_SENT, CLOSED)) {
                this.state.set(CLOSED);
                this.onClosed();

            }

            try {
                this.receiveBuffer.getOutputStream().close();
                this.sendBuffer.getOutputStream().close();
            } catch (IOException e) {
                System.err.println("[Socket][onClose] IOException while trying to close send buffer in response to received CLOSE packet");
                e.printStackTrace();
            }
        }
    }

    protected void onPoke(int id) {
        this.enforceOrdering(id);
    }

    /**
     * Enforces ordering, given the ID of a packet that was received. An ACK packet is sent,
     * irrespective of proper ordering. If the packet is not in order, then the last packet
     * that was received in order is acknowledged. If the packet was received in order, then
     * the packet is acknowledged.
     *
     * @param id the id of a received packet
     * @return true if packet was received in order
     */
    protected boolean enforceOrdering(int id) {
        if (this.lastReceivedId.get() + 1 == id) {
            this.sendAck(id);
            return true;

        } else {
            this.sendAck(this.lastReceivedId.get());
            return false;
        }
    }

    protected void onPacket(DatagramPacket pack) {
        this.lastReceivedTime = System.currentTimeMillis();

        try {
            byte[] packet = pack.getData();
            ByteArrayInputStream in = new ByteArrayInputStream(packet);
            DataInputStream reader = new DataInputStream(in);
            PacketType type = PacketType.fromValue(reader.readInt());
            int id = reader.readInt();

            //System.out.println("[Socket][onPacket] Received packet of type " + type);

            if (type == PacketType.SYN) {
                this.onSyn(pack.getAddress(), pack.getPort(), id);

            } else if (type == PacketType.ACK) {
                int window = reader.readInt();
                this.onAck(id, window);

            } else if (type == PacketType.MESSAGE) {
                this.onMessage(id, packet, 8, pack.getLength() - 8);

            } else if (type == PacketType.CLOSE) {
                this.onClose(id);

            } else if (type == PacketType.POKE) {
                this.onPoke(id);

            } else {
                System.err.println("[Socket][onPacket] Received packet of unknown type");
            }

            reader.close();
            in.close();

        } catch (IOException e) {
            System.err.println("[Socket][onPacket] IOException while parsing packet");
            e.printStackTrace();
        }
    }

    protected abstract DatagramPacket receivePacket() throws IOException;

    protected void receiver() {
        while (this.state.get() != CLOSED) {
            DatagramPacket packet;

            try {
                //System.out.println("[Socket][receiver] Attempting to receive packet");
                packet = this.receivePacket();

                if (packet == null) {
                    if (this.state.get() == CLOSED) break;
                    if (System.currentTimeMillis() - this.lastReceivedTime > Constants.TIMEOUT_DELAY) {
                        this.reportTimeout();

                    } else {
                        this.sendPoke();
                    }

                } else {
                    this.onPacket(packet);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //System.out.println("[Socket][receiver] Receiver thread terminating");
    }

    // lifecycle functions
    protected abstract void onClosed();

    protected abstract void onTimeout();

    protected void reportTimeout() {
        this.onTimeout();
    }

    public static final int LISTEN = 1;
    public static final int SYN_RECEIVED = 2;
    public static final int SYN_SENT = 3;
    public static final int ESTABLISHED = 4;
    public static final int CLOSE_SENT = 5;
    public static final int CLOSE_RECEIVED = 6;
    public static final int CLOSED = 7;
}
