package net.reqres;

import net.Constants;
import net.common.*;
import net.lib.Socket;
import utils.Logger;
import utils.RingBuffer;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * The SocketPlexer multiplexes multiple data streams across a single {@link Socket}.
 *
 * The intended use for the SocketPlexer is to separate request/response headers from
 * request/response body data in a way that is flexible enough to be used for other applications,
 * and also convenient to use with non-socket aware systems such as {@link com.fasterxml.jackson.core.JsonGenerator}
 * and {@link com.fasterxml.jackson.core.JsonParser}.
 *
 * Because request/response headers are usually short and sent first, the current implementation
 * of SocketPlexer does not employ any kind of flow-control. Thus, it is possible for one channel
 * to saturate the sending/receiving buffers of the underlying Sockets, preventing other channels
 * from transfering data.
 */
public class Socketplexer {

    /**
     * The default size of the buffers used to buffer data being received/sent over each channel.
     */
    private static final int DEFAULT_SUB_BUFFER_SIZE = 4096;

    private Socket socket;

    private final Object outputsLock;
    private HashMap<Integer, RingBuffer> outputChannels;

    private Logger logger;

    private AsyncJsonStreamGenerator controlWriter;

    private final Object inputsLock;
    private HashMap<Integer, RingBuffer> inputChannels;
    private HashMap<Integer, RingBuffer> pendingChannels;
    private HashMap<Integer, CompletableFuture<InputStream>> waitingChannels;

    private final Object multiplexerLock;

    /**
     * Creates a new SocketPlexer, wrapped around the given Socket.
     *
     * @param socket the Socket to use to send/receive data
     * @param executor an ExecutorService to manage the Threads backing SocketPlexer operation
     */
    public Socketplexer(Socket socket, ExecutorService executor) {
        this.socket = socket;

        this.logger = new Logger("Socketplexer", Constants.LOG);

        //this.logger.trace(" Initializing channel collections");
        this.outputChannels = new HashMap<>();
        this.inputChannels = new HashMap<>();
        this.pendingChannels = new HashMap<>();
        this.waitingChannels = new HashMap<>();

        //this.logger.trace(" Initializing locks");
        this.outputsLock = new Object();
        this.inputsLock = new Object();

        this.multiplexerLock = new Object();

        this.logger.trace(" Initializing control buffers");
        RingBuffer controlRecBuf = new RingBuffer(DEFAULT_SUB_BUFFER_SIZE);
        synchronized (this.inputsLock) {
            this.inputChannels.put(0, controlRecBuf);
        }
        executor.submit(new JsonStreamParser(controlRecBuf.getInputStream(), false, this::onControlPacket, true));

        RingBuffer controlSendBuf = new RingBuffer(DEFAULT_SUB_BUFFER_SIZE);
        synchronized (this.outputsLock) {
            this.outputChannels.put(0, controlSendBuf);
        }
        this.controlWriter = new AsyncJsonStreamGenerator(controlSendBuf.getOutputStream());
        executor.submit(this.controlWriter);
        this.controlWriter.enqueue((gen) -> gen.writeStartArray());

        this.logger.trace(" Starting mux/demux threads");
        executor.submit(this::demultiplexer);
        executor.submit(this::multiplexer);
    }

    /**
     * Main function of the Socketplexer's sending or multiplexing thread.
     *
     * The multiplexer thread iterates over each opened output channel, checks if they have buffered
     * data, and sends that data over the underlying socket.
     */
    private void multiplexer() {
        this.logger.log("[multiplexer] Multiplexer thread starting");

        this.logger.trace("[multiplexer] Creating DataOutputStream");
        DataOutputStream out = new DataOutputStream(this.socket.outputStream());

        this.logger.trace("[multiplexer] Initializing transfer buffer");
        byte[] trx = new byte[Constants.PACKET_SIZE - 8];
        boolean dataWritten = false;

        this.logger.trace("[multiplexer] Entering multiplexing loop");
        while (!this.socket.isReceiveClosed()) {
            dataWritten = false;

            for (Map.Entry<Integer, RingBuffer> entry : this.outputChannels.entrySet()) {
                int channel = entry.getKey();
                RingBuffer buffer = entry.getValue();

                if (this.socket.isSendClosed()) break;

                try {
                    if (!buffer.isReadOpened()) {
                        this.logger.finer("[multiplexer] Sending channel close command for channel " + channel);
                        this.controlWriter.enqueue((gen) -> {
                            gen.writeStartObject();
                            gen.writeStringField(COMMAND_FIELD_NAME, COMMAND_CLOSE_CHANNEL);
                            gen.writeNumberField(CHANNEL_ID_FIELD_NAME, channel);
                            gen.writeEndObject();
                        });
                        this.inputChannels.remove(channel);

                    } else if (buffer.available() > 0) {
                        this.logger.trace("[multiplexer] Reading data from output channel buffer " + channel);
                        int length = buffer.getInputStream().read(trx, 0, trx.length);
                        this.logger.trace("[multiplexer] Read " + length + " bytes from buffer, writing to socket");
                        out.writeInt(channel);
                        out.writeInt(length);
                        out.write(trx, 0, length);
                        dataWritten = true;
                        this.logger.finest("[multiplexer] Multiplexed " + length + " bytes from channel " + channel);

                    } else {
                        //this.logger.trace("[multiplexer] Channel " + channel + " has no data to send");
                    }

                } catch (IOException e) {
                    this.logger.error("[multiplexer] IOException while trying to multiplex data");
                    e.printStackTrace();
                }
            }

            if (!dataWritten && !this.socket.isSendClosed()) {
                synchronized (this.multiplexerLock) {
                    try {
                        this.multiplexerLock.wait(100);
                    } catch (InterruptedException e) {
                        this.logger.info("[multiplexer] Multiplexer thread interrupted while sleeping");
                        //e.printStackTrace();
                    }
                }
            }
        }

        this.logger.log("[multiplexer] Socket closed, terminating multiplexer");

        synchronized (this.outputsLock) {
            for (Map.Entry<Integer, RingBuffer> entry : this.outputChannels.entrySet()) {
                try {
                    entry.getValue().getInputStream().close();
                } catch (IOException e) {
                    this.logger.warn("[multiplexer] IOException while closing sending sub-buffer");
                    //e.printStackTrace();
                }
            }
        }

        this.logger.log("[multiplexer] Multiplexer thread shutting down");
    }

    /**
     * Main function of the Socketplexer's receiving or demultiplexing thread.
     *
     * The demultiplexer receives data from the underlying Socket, and writes it to the appropriate
     * input channel's buffer.
     */
    private void demultiplexer() {
        this.logger.log("[demultiplexer] Demultiplexer thread starting");

        DataInputStream in = new DataInputStream(this.socket.inputStream());
        byte[] trx = new byte[Constants.PACKET_SIZE - 8];

        int channel = 0;
        int length = 0;
        int off = 0;
        int read = 0;

        while (!this.socket.isReceiveClosed()) {
            try {
                channel = in.readInt();
                length = in.readInt();

                this.logger.trace("[demultiplexer] Receiving " + length + " bytes for channel " + channel);

                off = 0;
                read = 0;
                while ((read += in.read(trx, off, length - read)) < length && read != -1) off += read;
                if (read == -1) {
                    this.logger.info("[demultiplexer] Socket receive buffer closed");
                    break;
                }
                this.logger.finest("[demultiplexer] Demultiplexed " + length + " bytes from channel " + channel);
                //System.out.write(trx, 0, length);
                //System.out.println();

                synchronized (this.inputsLock) {
                    if (this.inputChannels.containsKey(channel)) {
                        try {
                            this.inputChannels.get(channel).getOutputStream().write(trx, 0, length);
                            this.logger.debug("[demultiplexer] Transferred data to receive buffer");
                        } catch (IOException e) {
                            this.logger.info("pdemultiplexer] IOException while writing to channel receive buffer");
                        }

                    } else {
                        this.logger.info("[demultiplexer] Received data for unopened channel");
                    }
                }

            } catch (IOException e) {
                if (this.socket.isReceiveClosed()) break;
                this.logger.warn("[demultiplexer] IOException while demultiplexing data");
                e.printStackTrace();
            }
        }

        this.logger.log("[demultiplexer] Socket closed, terminating demultiplexer");

        this.doClose();

        this.logger.log("[demultiplexer] Demultiplexer shutting down");
    }

    /**
     * Establishes a new channel with the given ID number and buffer capacity.
     * The data written to the channel will be buffered until the remote Socketplexer
     * acknowledges the creation of the channel. The remote must know the id used to
     * create the local output channel in order to obtain an InputStream to receive
     * data written to the output channel.
     *
     * @param id the ID number of the channel
     * @param bufferCapacity the size of this channel's sub-buffer.
     *  Used both for local output channel, and remote input channel
     * @return an OutputStream that can be used to write data over the new channel
     * @see #getInputChannel(int)
     * @see #waitInputChannel(int)
     */
    public OutputStream openOutputChannel(int id, int bufferCapacity) throws IOException {
        this.logger.fine("[openOutputChannel Opening output channel " + id);
        if (id <= 0) throw new IllegalArgumentException("Invalid channel ID. ID must be greater than zero");
        if (this.socket.isSendClosed()) throw new IOException("Socketplexer is closed");
        synchronized (this.outputsLock) {
            if (this.outputChannels.containsKey(id)) {
                this.logger.finer("[openOutputChannel] Output channel already opened");
                return this.outputChannels.get(id).getOutputStream();

            } else if (this.pendingChannels.containsKey(id)) {
                return this.pendingChannels.get(id).getOutputStream();

            } else {
                this.logger.finer("[openOutputChannel] Creating new output channel");

                this.controlWriter.enqueue((gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(COMMAND_FIELD_NAME, COMMAND_OPEN_CHANNEL);
                    gen.writeNumberField(CHANNEL_ID_FIELD_NAME, id);
                    gen.writeNumberField(BUFFER_SIZE_FIELD_NAME, bufferCapacity);
                    gen.writeEndObject();
                });

                synchronized (this.multiplexerLock) {
                    this.multiplexerLock.notifyAll();
                }

                this.logger.debug("[openOutputChannel] OpenChannel command enqueued");

                RingBuffer buffer = new RingBuffer(bufferCapacity);
                this.pendingChannels.put(id, buffer);
                return buffer.getOutputStream();
            }
        }
    }

    /**
     * Establishes a new channel with the given ID number and the default sub-buffer
     * capacity. see {@link #openOutputChannel(int, int)} for more details.
     *
     * @param id the ID number of the channel to open
     * @return an OutputStream to write data to the channel
     */
    public OutputStream openOutputChannel(int id) throws IOException {
        return this.openOutputChannel(id, DEFAULT_SUB_BUFFER_SIZE);
    }

    /**
     * Determines whether there is an opened input channel with the given ID number.
     *
     * @param id the ID to check for
     * @return whether there is an InputChannel with the given ID
     */
    public boolean hasInputChannel(int id) {
        synchronized (this.inputsLock) {
            return this.inputChannels.containsKey(id);
        }
    }

    /**
     * Returns an InputStream to read data from the input channel with the given ID number, or null
     * if there is no active input channel with that ID.
     *
     * @param id the ID of the input channel to return
     * @return an InputStream to read from the input channel with the given ID, or null
     */
    public InputStream getInputChannel(int id) {
        synchronized (this.inputsLock) {
            return this.inputChannels.get(id).getInputStream();
        }
    }

    /**
     * Returns a {@link Future} which resolves to an {@link InputStream} when the remote Socketplexer creates a channel
     * with the given ID number.
     *
     * @param id the ID of the channel to wait for
     * @return a Future resolving to the input channel with the given ID
     */
    public Future<InputStream> waitInputChannel(int id) {
        synchronized (this.inputsLock) {
            if (this.inputChannels.containsKey(id)) {
                return CompletableFuture.completedFuture(this.inputChannels.get(id).getInputStream());

            } else if (this.waitingChannels.containsKey(id)) {
                return this.waitingChannels.get(id);

            } else {
                CompletableFuture<InputStream> future = new CompletableFuture<>();
                this.waitingChannels.put(id, future);
                return future;
            }
        }
    }

    public InputStream waitInputStream(int id, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return this.waitInputChannel(id).get(timeout, unit);
    }

    /**
     * Forcibly terminates all channels and closes the underlying Socket.
     */
    public void terminate() {
        this.logger.fine("[terminate] Terminating socketplexer");

        this.doClose();
    }

    public boolean isOpened() {
        return !this.socket.isClosed() && this.outputChannels.size() + this.inputChannels.size() > 2;
    }

    /**
     * Called when an OpenChannel packet is received.
     * Used internally.
     *
     * @param packet the received packet
     */
    private void onOpenChannel(JsonField.ObjectField packet) {
        int bufferSize = (int) packet.getLongProperty(BUFFER_SIZE_FIELD_NAME);
        int channel = (int) packet.getLongProperty(CHANNEL_ID_FIELD_NAME);
        RingBuffer buffer_in = new RingBuffer(bufferSize);

        this.logger.fine("[onOpenChannel] Received OpenChannel for channel " + channel);

        synchronized (this.inputsLock) {
            this.inputChannels.put(channel, buffer_in);

            if (this.waitingChannels.containsKey(channel)) {
                CompletableFuture<InputStream> future = this.waitingChannels.get(channel);
                this.waitingChannels.remove(channel);
                if (future.isCancelled()) {
                    try {
                        buffer_in.getInputStream().close();
                    } catch (IOException e) {}

                } else {
                    future.complete(buffer_in.getInputStream());
                }
            }
        }

        synchronized (this.outputsLock) {
            if (this.pendingChannels.containsKey(channel)) {
                RingBuffer buffer_out = this.pendingChannels.get(channel);
                this.pendingChannels.remove(channel);
                this.outputChannels.put(channel, buffer_out);

            } else if (!this.outputChannels.containsKey(channel)) {
                RingBuffer buffer_out = new RingBuffer(bufferSize);
                this.outputChannels.put(channel, buffer_out);
            }
        }

        this.logger.debug("[onOpenChannel] Sending OpenChannelAck");
        this.controlWriter.enqueue((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(COMMAND_FIELD_NAME, COMMAND_OPEN_ACK);
            gen.writeNumberField(CHANNEL_ID_FIELD_NAME, channel);
            gen.writeEndObject();
        });
    }

    /**
     * Called when an Open Channel command is acknowledged by the remote.
     * Used internally.
     *
     * @param packet the received packet
     */
    private void onOpenAck(JsonField.ObjectField packet) {
        int channel = (int) packet.getLongProperty(CHANNEL_ID_FIELD_NAME);
        this.logger.fine("[onOpenAck] Received OpenAck for channel " + channel);

        int bufferSize;

        synchronized (this.outputsLock) {
            RingBuffer buffer = this.pendingChannels.get(channel);
            bufferSize = buffer.size();
            this.pendingChannels.remove(channel);
            this.outputChannels.put(channel, buffer);
        }

        synchronized (this.inputsLock) {
            RingBuffer buffer_in = this.inputChannels.getOrDefault(channel, new RingBuffer(bufferSize));
            this.inputChannels.put(channel, buffer_in);

            if (this.waitingChannels.containsKey(channel)) {
                CompletableFuture<InputStream> future = this.waitingChannels.get(channel);
                this.waitingChannels.remove(channel);
                if (future.isCancelled()) {
                    try {
                        buffer_in.getInputStream().close();
                    } catch (IOException e) {}

                } else {
                    future.complete(buffer_in.getInputStream());
                }
            }
        }
    }

    /**
     * Called when a Close Channel packet is received.
     * Used internally.
     *
     * @param packet the received packet
     */
    private void onCloseChannel(JsonField.ObjectField packet) {
        int channel = (int) packet.getLongProperty(CHANNEL_ID_FIELD_NAME);
        this.logger.fine("[onCloseChannel] Received CloseChannel command for channel " + channel);

        synchronized (this.inputsLock) {
            RingBuffer buffer = this.inputChannels.get(channel);
            this.inputChannels.remove(channel);

            try {
                buffer.getOutputStream().close();
            } catch (IOException e) {}
        }

        this.logger.debug("[onCloseChannel] Sending CloseChannelAck");
        this.controlWriter.enqueue((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(COMMAND_FIELD_NAME, COMMAND_CLOSE_ACK);
            gen.writeNumberField(CHANNEL_ID_FIELD_NAME, channel);
            gen.writeEndObject();
        });

        this.checkShouldClose();
    }

    /**
     * Called when a Close Channel command is acknowledged by the remote.
     * Used internally.
     *
     * @param packet the received acknowledge packet
     */
    private void onCloseAck(JsonField.ObjectField packet) {
        try {
            this.logger.fine("[onCloseAck] Received CloseAck  for channel " + packet.getLongProperty(CHANNEL_ID_FIELD_NAME));
        } catch (Exception e) {}
        this.checkShouldClose();
    }

    /**
     * Closes the underlying {@link Socket} when there are no opened channels remaining.
     * Used internally.
     */
    private void checkShouldClose() {
        int channelsOpened = 0;

        synchronized (this.outputsLock) {
            channelsOpened += this.outputChannels.size() - 1;
        }

        synchronized (this.inputsLock) {
            channelsOpened += this.inputChannels.size() - 1;
        }

        this.logger.finest("[checkShouldClose] " + channelsOpened + " use channels opened");
        if (channelsOpened == 0) {
            this.doClose();
        }
    }

    private void doClose() {
        this.logger.log("[doClose] Closing Socketplexer");
        try { this.controlWriter.enqueue((gen) -> {
            gen.writeEndArray();
            gen.close();
        }); } catch (Exception e) {}

        synchronized (this.outputsLock) {
            this.outputsLock.notifyAll();
        }

        synchronized (this.inputsLock) {
            Exception e1 = new IOException("Socketplexer closed");
            for (Map.Entry<Integer, CompletableFuture<InputStream>> entry : this.waitingChannels.entrySet()) {
                entry.getValue().completeExceptionally(e1);
            }

            for (Map.Entry<Integer, RingBuffer> entry : this.inputChannels.entrySet()) {
                try {
                    entry.getValue().getInputStream().close();
                } catch (IOException e) {
                    this.logger.warn("[terminate] IOException while closing sending sub-buffer");
                    //e.printStackTrace();
                }
            }
        }

        synchronized (this.outputsLock) {
            for (Map.Entry<Integer, RingBuffer> entry : this.pendingChannels.entrySet()) {
                try {
                    entry.getValue().getInputStream().close();
                } catch (IOException e) {}
            }

            for (Map.Entry<Integer, RingBuffer> entry : this.outputChannels.entrySet()) {
                try {
                    entry.getValue().getOutputStream().close();
                } catch (IOException e) {
                    this.logger.warn("[terminate] IOException while closing receiving sub-buffer");
                    //e.printStackTrace();
                }
            }
        }

        this.socket.close();
    }

    /**
     * Called when a packet is received on the Socketplexer's control channel.
     * Used internally.
     *
     * @param field the received packet
     */
    private void onControlPacket(JsonField field) {
        if (!field.isObject()) return;

        this.logger.debug("[onControlPacket] Received control packet");

        JsonField.ObjectField packet = (JsonField.ObjectField) field;
        String type = packet.getStringProperty(COMMAND_FIELD_NAME);
        this.logger.fine("[onControlPacket] Received control packet: " + type);
        switch (type) {
            case COMMAND_OPEN_CHANNEL: this.onOpenChannel(packet); break;
            case COMMAND_OPEN_ACK: this.onOpenAck(packet); break;
            case COMMAND_CLOSE_CHANNEL: this.onCloseChannel(packet); break;
            case COMMAND_CLOSE_ACK: this.onCloseAck(packet); break;
            default : this.logger.info("[onControlPacket] Unrecognized packet type on control channel");
        }
    }

    public void setLogFilter(int level) {
        this.logger.setFilter(level);
    }

    private static final String COMMAND_FIELD_NAME = "FIELD_COMMAND";
    private static final String COMMAND_OPEN_CHANNEL = "COMMAND_OPEN_CHANNEL";
    private static final String COMMAND_OPEN_ACK = "COMMAND_OPEN_ACK";
    private static final String COMMAND_CLOSE_CHANNEL = "COMMAND_CLOSE_CHANNEL";
    private static final String COMMAND_CLOSE_ACK = "COMMAND_CLOSE_ACK";

    private static final String CHANNEL_ID_FIELD_NAME = "FIELD_CHANNEL_ID";
    private static final String BUFFER_SIZE_FIELD_NAME = "FIELD_BUFFER_SIZE";
}
