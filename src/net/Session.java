package net;

import utils.CircularBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.SynchronousQueue;

public abstract class Session {

    private final static int BUFFER_SIZE = 1024 * 10;

    private Thread receiver;

    private int ack = 0;
    private int window = 0;

    private boolean opened;

    protected SynchronousQueue<SessionPacket> receiveQueue;

    private CircularBuffer inputBuffer;
    private CircularBuffer outputBuffer;

    public Session() {
        this.receiver = new Thread(this::receive);
        this.receiveQueue = new SynchronousQueue<>();

        this.inputBuffer = new CircularBuffer(BUFFER_SIZE);
        this.outputBuffer = new CircularBuffer(BUFFER_SIZE);
    }

    public void open() {
        this.receiver.start();
    }

    public void receive() {
        try {
            SessionPacket packet = this.receiveQueue.take();
            this.onPacket(packet);

        } catch (InterruptedException e) {
            System.err.println("[Session][receive] Interrupted while waiting to receive packet");
            e.printStackTrace();
        }

    }

    protected void onReceive(SessionPacket packet) {
        this.outputBuffer.write(packet.getPayload(), 0, packet.getPayloadSize());
    }

    protected void onPacket(SessionPacket packet) throws InterruptedException {
        this.receiveQueue.put(packet);
    }

    class SessionInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            return 0;
        }
    }

    class SessionOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {

        }
    }
}
