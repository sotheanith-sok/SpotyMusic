package net.lib;

import net.Constants;
import utils.CRC64;
import utils.RingBuffer;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;

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

    protected RingBuffer sendBuffer;

    protected AtomicBoolean waitingAck;
    protected final Object waitingLock;

    protected Thread sender;

    // last packet send
    protected byte[] lastSend;
    protected int offset;
    protected int length;
    protected final Object senderLock;
    protected AtomicBoolean reversePoke;

   // receiving
    /**
     * The ID of the last packet that was received and acknowledged.
     * Used to enforce packet ordering.
     */
    protected AtomicInteger lastReceivedId;

    protected RingBuffer receiveBuffer;

    protected long lastReceivedTime;
    protected long lastAckTime;

    protected Thread receiver;

    public int debug = Constants.ERROR;

    public Socket(InetAddress remote, int port) {
        this(remote, port, Constants.BUFFER_SIZE, Constants.BUFFER_SIZE);
    }

    public Socket(InetAddress remote, int port, int sendBuffer, int receiveBuffer) {
        this.remote = remote;
        this.port = port;
        this.state = new AtomicInteger();

        this.messageId = new AtomicInteger(0);
        this.acknowledgedId = new AtomicInteger(0);
        this.remoteWindow = new AtomicInteger(1);
        this.sendBuffer = new RingBuffer(sendBuffer);
        this.waitingAck = new AtomicBoolean();
        this.waitingLock = new Object();
        this.sender = new Thread(this::sender);
        this.sender.setName("[Socket][sender]");
        this.senderLock = new Object();
        this.reversePoke = new AtomicBoolean(false);

        this.lastReceivedId = new AtomicInteger();
        this.receiveBuffer = new RingBuffer(receiveBuffer, 256, receiveBuffer);
        this.receiveBuffer.setLowWaterMarkListener(this::reversePoke);
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

    public void reversePoke() {
       this.reversePoke.set(true);
       synchronized (this.senderLock) {
          this.senderLock.notifyAll();
       }
       //System.out.println("[Socket][reversePoke] Reverse Poke!");
    }

    // sending system
    protected abstract Object getSocketLock();

    protected abstract DatagramSocket getSocket();

    protected int getWindow() {
        return Math.max(0, this.receiveBuffer.capacity() / Constants.PACKET_SIZE - 2);
    }

    /**
     * Sends the given data over the Socket. Blocks until the data is sent successfully.
     *
     * @param data the data to send
     * @param off the offset at which to start in data
     * @param len the length of data to send
     */
    protected void sendMessage(byte[] data, int off, int len) throws SocketTimeoutException, IOException {
        if (this.state.get() == CLOSED || this.state.get() == CLOSE_SENT) throw new IllegalStateException("Cannot send message data when connection being closed");
        try {
            int id;
            // send packet
            ByteArrayOutputStream dest = new ByteArrayOutputStream();
            CheckedOutputStream check = new CheckedOutputStream(dest, new CRC64());
            DataOutputStream out = new DataOutputStream(check);
            out.writeInt(PacketType.MESSAGE.value);
            out.writeInt(id = messageId.incrementAndGet());
            out.write(data, off, len);
            long checksum = check.getChecksum().getValue();
            //System.out.println("[Socket][sendMessage] Checksum value: " + checksum);
            out.writeLong(checksum);
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
            if (this.debug <= Constants.FINEST) System.out.println("[Socket][sendAck] Sent Ack ackId=" + ackId);

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

            if (this.debug <= Constants.FINE) System.out.println("[Socket][sendClose][debug] Sent CLOSE packet");

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
            if (this.debug <= Constants.FINEST) System.out.println("[Socket][sendPoke] Sending POKE id=" + id);
            this.sendPacket(id, dest.toByteArray(), 0, dest.size());
            if (this.debug <= Constants.FINEST) System.out.println("[Socket][sendPoke] Sent POKE id=" + id);

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
        do {
            if (this.state.get() == CLOSED) throw new IOException("Socket closed while waiting to send");
            if (this.acknowledgedId.get() + 1 == id) {
                // if next in line to be sent
                if (this.waitingAck.compareAndSet(false, true)) {
                    // send packet
                    this.lastSend = packet;
                    this.offset = off;
                    this.length = len;
                    this.sendTrivial(packet, off, len);

                    // wait for acknowledgement
                    synchronized (this.waitingLock) {
                        if (this.acknowledgedId.get() == id) {
                            this.waitingAck.set(false);
                            break;

                        } else {
                            try {
                                this.waitingLock.wait(Constants.RESEND_DELAY);
                                if (this.acknowledgedId.get() == id) {
                                    this.waitingAck.set(false);
                                    break;
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (System.currentTimeMillis() - lastReceivedTime > Constants.TIMEOUT_DELAY) {
                                this.reportTimeout();
                                throw new SocketTimeoutException("Socket timed out while trying to send packet");
                            }
                            if (this.debug <= Constants.INFO) System.out.println("[Socket][sendPacket][INFO] Packet not acknowledged, sending packet again");
                        }

                    }
                }

            } else {
                System.err.println("[Socket][sendPacket] Packet not being sent in order. id=" + id + " lastAcknowledged=" + acknowledgedId.get());
                try {
                    synchronized (this.waitingLock) {
                        this.waitingLock.wait();
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        } while (true);

        if (this.debug <= Constants.DEBUG) System.out.println("[Socket][sendPacket][DEBUG] Packet id=" + id + " sent and acknowledged");
    }

    protected void sendTrivial(byte[] packet, int off, int len) {
        synchronized (this.getSocketLock()) {
            DatagramPacket p = new DatagramPacket(packet, off, len, this.remote, this.port);

            try {
                this.getSocket().send(p);

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
        lastAckTime = System.currentTimeMillis();

        InputStream src = this.sendBuffer.getInputStream();
        while (this.state.get() != CLOSED && this.state.get() != CLOSE_RECEIVED && (this.sendBuffer.available() > 0 || this.sendBuffer.isWriteOpened())) {
           if (this.reversePoke.get()) {
              this.sendAck(this.lastReceivedId.get());
           }

           // if remote can accept, send data
            try {
                if (this.remoteWindow.get() > 0 && src.available() > 0) {
                    try {
                        int amnt = src.read(trx, 0, trx.length); // blocks until at least one byte read
                        if (amnt == -1) break;
                        //System.out.println("[Socket][sender] Sending " + amnt + " bytes");
                        this.sendMessage(trx, 0, amnt); // blocks until data sent or socket closed
                        if (this.debug <= Constants.LOG) System.out.println("[Socket][sender][debug] Sent " + amnt + " bytes of message data");

                    } catch (IOException e) {
                        System.err.println("[Socket][sender] IOException while trying to send data");
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                System.err.println("[Socket][sender] IOException while checking for available data to send");
                e.printStackTrace();
            }

            if (System.currentTimeMillis() - this.lastAckTime > Constants.TIMEOUT_DELAY / 2) {
                this.sendPoke();
            }

            synchronized (this.senderLock) {
               try {
                  this.senderLock.wait(100);
               } catch (InterruptedException e) {
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
                ByteArrayInputStream in = new ByteArrayInputStream(data, off + len, Constants.FOOTER_OVERHEAD);
                DataInputStream read = new DataInputStream(in);
                long expected = read.readLong();

                Checksum check = new CRC64();
                check.update(data, 0, len + Constants.HEADER_OVERHEAD);
                long calculated = check.getValue();

                if (calculated != expected) {
                    System.err.println("[Socket][onMessage] Checksum mismatch! Calculated = " + calculated + " Received = " + expected);
                }

                //System.out.write(data, off, len);

                if (this.debug <= Constants.LOG) System.out.println("[Socket][onMessage][LOG] Received " + len + " bytes of message data");
                this.receiveBuffer.getOutputStream().write(data, off, len);
                if (this.debug <= Constants.LOG) System.out.println("[Socket][onMessage][LOG] Wrote " + len + " bytes to receive buffer");

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
            this.lastAckTime = System.currentTimeMillis();
            this.remoteWindow.set(window);
            synchronized (this.waitingLock) {
               this.waitingLock.notifyAll();
            }
            synchronized (this.senderLock) {
               this.senderLock.notifyAll();
            }

        } else if (this.acknowledgedId.get() == ackId) {
           this.remoteWindow.set(window);
           synchronized (this.senderLock) {
              this.senderLock.notifyAll();
           }

        } else {
            // if not acknowledged in order
            System.out.println("[Socket][onAck] ACK received out of order, resending last packet");
            this.sendTrivial(lastSend, offset, length);
        }
    }

    protected void onClose(int id) {
        if (enforceOrdering(id)) {
            if (this.debug <= Constants.FINE) System.out.println("[Socket][onClose][debug] Received CLOSE packet");

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

            if (this.debug <= Constants.FINER) System.out.println("[Socket][onPacket] Received packet: type=" + type + " id=" + id);

            if (type == PacketType.SYN) {
                this.onSyn(pack.getAddress(), pack.getPort(), id);

            } else if (type == PacketType.ACK) {
                int window = reader.readInt();
                this.onAck(id, window);

            } else if (type == PacketType.MESSAGE) {
                this.onMessage(id, packet, Constants.HEADER_OVERHEAD, pack.getLength() - Constants.HEADER_OVERHEAD - Constants.FOOTER_OVERHEAD);

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
        this.state.set(CLOSED);
        if (this.debug <= Constants.WARN) System.out.println("[Socket][reportTimeout][WARN] Socket timed out");
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
