package net.lib;

import net.Constants;
import utils.CRC64;
import utils.Logger;
import utils.RingBuffer;

import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;

public abstract class Socket {

    protected AtomicInteger state;

    protected Logger logger;
    private Logger sendPacketLogger;

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

    private LinkedList<TimeoutListener> timeoutListeners;

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

        this.timeoutListeners = new LinkedList<>();

        this.logger = new Logger("Socket", Constants.INFO);
        this.sendPacketLogger = new Logger("Socket][sendPacket", Constants.LOG);
        this.logger.info(" New socket bound to: " + remote + ":" + port);
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
        this.logger.log("[close] Closing socket");
        try {
            this.sendBuffer.getOutputStream().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addTimeoutListener(TimeoutListener listener) {
        this.timeoutListeners.add(listener);
    }

    public void reversePoke() {
        this.logger.finer("[reversePoke] Sending reverse poke");
        this.reversePoke.set(true);
        synchronized (this.senderLock) {
            this.senderLock.notifyAll();
        }
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
     * @param off  the offset at which to start in data
     * @param len  the length of data to send
     */
    protected void sendMessage(byte[] data, int off, int len) throws SocketTimeoutException, IOException {
        if (this.state.get() == CLOSED || this.state.get() == CLOSE_SENT)
            throw new IOException("Cannot send message data when connection is closed or closing");
        if (len > Constants.PACKET_SIZE - (4 * 2))
            throw new IOException("Message size cannot exceed PACKET_SIZE - 8B");
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
            this.logger.debug("[sendMessage] Sending " + len + " bytes of message data. Checksum value: " + checksum);
            out.writeLong(checksum);
            this.sendPacket(id, dest.toByteArray(), 0, dest.size());

        } catch (SocketTimeoutException e) {
            throw e;

        } catch (IOException e) {
            this.logger.error("[sendMessage] IOException while trying to send message packet");
            throw e;
        }
    }

    protected void sendSyn() throws SocketTimeoutException {
        this.logger.log("[sendSyn] Sending SYN packet");
        this.lastReceivedTime = Math.max(this.lastReceivedTime, System.currentTimeMillis());
        if (this.state.get() >= SYN_SENT) {
            this.logger.warn("[sendSyn] SYN packet already sent");
            throw new IllegalStateException("SYN packet already sent");
        }
        try {
            this.logger.trace("[sendSyn] Building SYN packet");
            int id;
            ByteArrayOutputStream dest = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dest);
            out.writeInt(PacketType.SYN.value);
            out.writeInt(id = this.messageId.incrementAndGet());

            this.logger.debug("[sendSyn] Sending SYN packet");
            this.sendPacket(id, dest.toByteArray(), 0, dest.size());

            this.logger.debug("[sendSyn] SYN packet sent");

        } catch (SocketTimeoutException e) {
            this.logger.warn("[sendSyn] Timed out while trying to send SYN packet");
            throw e;

        } catch (IOException e) {
            this.logger.warn("[sendSync] IOException while sending SYN packet");
            e.printStackTrace();
        }
    }

    protected void sendAck(int ackId) {
        this.logger.trace("[sendAck] Sending ACK with AckID=" + ackId);
        if (this.state.get() == CLOSED) {
            this.logger.warn("[sendAck] Socket is closed");
            throw new IllegalStateException("Cannot send ACK on closed socket");
        }
        try {
            this.logger.trace("[sendAck] Building ACK packet");
            ByteArrayOutputStream dest = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dest);
            out.writeInt(PacketType.ACK.value);
            out.writeInt(ackId);
            out.writeInt(this.getWindow());
            this.logger.trace("[sendAck] Sending ACK packet");
            this.sendTrivial(dest.toByteArray(), 0, dest.size());
            this.lastReceivedId.set(ackId);
            this.logger.debug("[sendAck] Sent Ack ackId=" + ackId);

        } catch (IOException e) {
            this.logger.warn("[sendAck] IOException while trying to send ACK packet");
            e.printStackTrace();
        }
    }

    protected void sendClose() throws SocketTimeoutException {
        if (this.state.get() != ESTABLISHED && this.state.get() != CLOSE_RECEIVED)
            throw new IllegalStateException("Cannot send CLOSE packet when connection is not established");
        try {
            int id;
            ByteArrayOutputStream dest = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dest);
            out.writeInt(PacketType.CLOSE.value);
            out.writeInt(id = this.messageId.incrementAndGet());
            this.sendBuffer.getOutputStream().close();
            this.sendPacket(id, dest.toByteArray(), 0, dest.size());

            this.logger.finer("[sendClose][debug] Sent CLOSE packet");

            if (this.state.get() == ESTABLISHED) {
                // if was established, then local initiated close
                this.state.set(CLOSE_SENT);

            } else if (this.state.get() == CLOSE_RECEIVED) {
                // received close, now fully closed
                this.state.set(CLOSED);
                this.logger.info("[sendClose] Socket closed");
                this.onClosed();
            }

        } catch (SocketTimeoutException e) {
            throw e;

        } catch (IOException e) {
            this.logger.warn("[sendClose] IOException while trying to send CLOSE packet");
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
            this.logger.finest("[sendPoke] Sending POKE id=" + id);
            this.sendPacket(id, dest.toByteArray(), 0, dest.size());
            this.logger.finest("[sendPoke] Sent POKE id=" + id);

        } catch (IOException e) {
            this.logger.error("[sendPoke] IOException while trying to send POKE packet");
            e.printStackTrace();
        }
    }

    /**
     * Sends the given packet, blocking until the packet is sent and acknowledged.
     *
     * @param id     the ID number of the packet to send
     * @param packet the data to send in the packet
     * @param off    the offset at which relevant packet data starts
     * @param len    the length of the data to send
     * @throws IOException if the socket is closed while sending data
     */
    protected void sendPacket(int id, byte[] packet, int off, int len) throws SocketTimeoutException, IOException {
        this.logger.finest("[sendPacket][" + id + "] Sending packet " + id);
        do {
            if (this.acknowledgedId.get() + 1 == id && this.waitingAck.compareAndSet(false, true)) {
                this.sendPacketLogger.setName("Socket][sendPacket][" + id);
                // if next in line to be sent
                this.sendPacketLogger.trace(" Logging packet data");
                // send packet
                this.lastSend = packet;
                this.offset = off;
                this.length = len;

                this.sendPacketLogger.trace(" Sending packet trivially");
                this.sendTrivial(packet, off, len);

                // wait for acknowledgement
                synchronized (this.waitingLock) {
                    do {
                        if (this.acknowledgedId.get() == id) {
                            this.waitingAck.set(false);
                            break;
                        }

                        try {
                            this.sendPacketLogger.trace(" Waiting for acknowledgement");
                            this.waitingLock.wait(Constants.RESEND_DELAY);
                            if (this.acknowledgedId.get() == id) {
                                this.sendPacketLogger.trace(" Acknowledgement received");
                                this.waitingAck.set(false);
                                break;
                            }
                        } catch (InterruptedException e) {
                            if (this.acknowledgedId.get() == id) {
                                this.sendPacketLogger.trace(" Acknowledgement received");
                                this.waitingAck.set(false);
                                break;

                            } else {
                                this.sendPacketLogger.debug(" Interrupted while waiting ");
                                this.waitingAck.set(false);
                                e.printStackTrace();
                            }
                        }

                        this.sendPacketLogger.trace(" Resending packet...");
                        this.sendTrivial(packet, off, len);

                    } while (System.currentTimeMillis() - lastReceivedTime < Constants.TIMEOUT_DELAY);
                    this.waitingAck.set(false);
                }
            } else {
                synchronized (this.waitingLock) {
                    try {
                        this.waitingLock.wait(100);

                    } catch (InterruptedException e) {}
                }
            }

        } while (this.acknowledgedId.get() < id && System.currentTimeMillis() - lastReceivedTime < Constants.TIMEOUT_DELAY && this.state.get() != CLOSED);

        if (this.state.get() == CLOSED) {
            this.logger.warn("[sendPacket][" + id + "] Socket is closed");
            throw new IOException("Socket closed while waiting to send");
        }

        if (System.currentTimeMillis() - lastReceivedTime > Constants.TIMEOUT_DELAY) {
            this.logger.warn("[sendPacket] Timed out while trying to send packet");
            this.reportTimeout();
            throw new SocketTimeoutException("Socket timed out while trying to send data");
        }

        this.logger.debug("[sendPacket] Packet id=" + id + " sent and acknowledged");
    }

    protected void sendTrivial(byte[] packet, int off, int len) {
        synchronized (this.getSocketLock()) {
            DatagramPacket p = new DatagramPacket(packet, off, len, this.remote, this.port);

            try {
                this.getSocket().send(p);

            } catch (BindException e) {
                this.logger.error("[sendTrivial] BindException while trying to send packet. Remote address: " + this.remote + ":" + this.port);
                e.printStackTrace();

            } catch (IOException e) {
                this.logger.warn("[sendTrivial] IOException while sending packet");
                e.printStackTrace();
            }
        }
    }

    protected void sender() {
        byte[] trx = new byte[Constants.PACKET_SIZE - (4 * 2)];
        lastAckTime = System.currentTimeMillis();

        InputStream src = this.sendBuffer.getInputStream();
        while (this.state.get() != CLOSED && this.state.get() != CLOSE_RECEIVED && (this.sendBuffer.available() > 0 || this.sendBuffer.isWriteOpened())) {
            if (this.reversePoke.compareAndSet(true, false)) {
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
                        this.logger.debug("[sender] Sent " + amnt + " bytes of message data");

                    } catch (IOException e) {
                        this.logger.info("[sender] IOException while trying to send data");
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                this.logger.warn("[sender] IOException while checking for available data to send");
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
                this.logger.info("[sender] Did not receive ACK to CLOSE packet");
                e.printStackTrace();
            }
        }

        this.logger.log("[sender] Sender thread terminating");
    }

    // receiving system
    protected void onMessage(int id, byte[] data, int off, int len) {
        // enforce ordering manually, in case of checksum mismatch
        if (this.lastReceivedId.get() + 1 == id) {

            try {
                this.logger.debug("[onMessage] Received " + len + " bytes of message data");

                ByteArrayInputStream in = new ByteArrayInputStream(data, off + len, Constants.FOOTER_OVERHEAD);
                DataInputStream read = new DataInputStream(in);
                long expected = read.readLong();

                Checksum check = new CRC64();
                check.update(data, 0, len + Constants.HEADER_OVERHEAD);
                long calculated = check.getValue();

                if (calculated != expected) {
                    this.logger.warn("[onMessage] Checksum mismatch! Calculated = " + calculated + " Received = " + expected);
                    this.sendAck(this.lastReceivedId.get());
                    return;

                } else {
                    this.sendAck(id);
                }

                this.receiveBuffer.getOutputStream().write(data, off, len);
                this.logger.debug("[onMessage] Wrote " + len + " bytes to receive buffer");

            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            this.logger.warn("[onMessage] MESSAGE packet received out of order");
            this.sendAck(this.lastReceivedId.get());
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
            this.logger.finer("[onAck] ACK received out of order, resending last packet. Received " + ackId + " expected " + this.acknowledgedId.get());
            this.sendTrivial(lastSend, offset, length);
        }
    }

    protected void onClose(int id) {
        if (enforceOrdering(id)) {
            this.logger.log("[onClose] Received CLOSE packet");

            if (this.state.get() == ESTABLISHED) {
                this.state.set(CLOSE_RECEIVED);

            } else if (this.state.compareAndSet(CLOSE_SENT, CLOSED)) {
                this.state.set(CLOSED);
                this.logger.info("[onClose] Socket closed");
                this.onClosed();

            }

            try {
                this.receiveBuffer.getOutputStream().close();
                this.sendBuffer.getOutputStream().close();
            } catch (IOException e) {
                this.logger.info("[onClose] IOException while trying to close send buffer in response to received CLOSE packet");
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

            this.logger.finest("[onPacket] Received packet: type=" + type + " id=" + id);

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
                this.logger.info("[onPacket] Received packet of unknown type");
            }

            reader.close();
            in.close();

        } catch (IOException e) {
            this.logger.error("[onPacket] IOException while parsing packet");
            e.printStackTrace();
        }
    }

    protected abstract DatagramPacket receivePacket() throws IOException;

    protected void receiver() {
        this.logger.log("[receiver][" + System.currentTimeMillis() + "] Receiver thread starting");
        this.lastReceivedTime = System.currentTimeMillis();

        while (this.state.get() != CLOSED) {
            DatagramPacket packet;

            try {
                this.logger.trace("[receiver] Attempting to receive packet");
                packet = this.receivePacket();

                if (packet == null) {
                    if (this.state.get() == CLOSED) {
                        this.logger.fine("[receiver] Socket has been closed");
                        break;
                    }
                    if (System.currentTimeMillis() - this.lastReceivedTime > Constants.TIMEOUT_DELAY) {
                        this.logger.log("[receiver][" + System.currentTimeMillis() + "] Timed out while waiting for packet");
                        this.reportTimeout();
                    }

                } else {
                    this.onPacket(packet);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.logger.log("[receiver] Receiver thread terminating");
    }

    // lifecycle functions
    protected abstract void onClosed();

    protected void onTimeout() {
        for (TimeoutListener listener : this.timeoutListeners) {
            try {
                listener.onTimeout();
            } catch (Exception e) {}
        }
    }

    protected void reportTimeout() {
        this.state.set(CLOSED);
        synchronized (this.waitingLock) {
            this.waitingLock.notifyAll();
        }

        synchronized (this.senderLock) {
            this.senderLock.notifyAll();
        }

        this.logger.info("[reportTimeout] Socket timed out");
        this.onTimeout();
    }

    public interface TimeoutListener {
        void onTimeout();
    }

    public static final int LISTEN = 1;
    public static final int SYN_RECEIVED = 2;
    public static final int SYN_SENT = 3;
    public static final int ESTABLISHED = 4;
    public static final int CLOSE_SENT = 5;
    public static final int CLOSE_RECEIVED = 6;
    public static final int CLOSED = 7;
}
