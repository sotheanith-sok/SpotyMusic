package utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class StreamBuffer {

    protected final Object lock;

    protected byte[] buffer;
    protected final int capacity;
    protected int head;
    protected int tail;

    protected AtomicBoolean readOpened;
    protected AtomicBoolean writeOpened;

    protected BufferProvider source;
    protected BufferConsumer sink;

    public StreamBuffer(int bufferSize) {
        this.buffer = new byte[bufferSize];
        this.capacity = bufferSize;
        this.lock = new Object();
        this.head = 0;
        this.tail = 0;

        this.readOpened = new AtomicBoolean(true);
        this.writeOpened = new AtomicBoolean(true);

        this.source = new BufferProvider(this);
        this.sink = new BufferConsumer(this);
    }

    public InputStream getInputStream() {
        return this.sink;
    }

    public OutputStream getOutputStream() {
        return this.source;
    }

    public int capacity() {
        synchronized (lock) {
            if (tail == head) {
                return capacity - 1;

            } else if (tail < head) {
                return capacity - (head - ((tail + 1) % capacity));

            } else {
                return ((tail + 1) % capacity) - head;
            }
        }
    }

    public int available() {
        synchronized (lock) {
            if (head > tail) {
                return head - tail;

            } else if (head < tail) {
                return head + (capacity - tail);

            } else {
                return 0;
            }

        }
    }

}
class BufferConsumer extends InputStream {

    private final StreamBuffer buffer;

    public BufferConsumer(StreamBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public int available() {
        return buffer.available();
    }

    @Override
    public int read() throws IOException {
        if (!buffer.readOpened.get()) return -1;
        synchronized (buffer.lock) {
            if (available() == 0 && buffer.writeOpened.get()) {
                try {
                    //System.out.println("[StreamBuffer][InputStream] available() = 0");
                    buffer.lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (available() > 0) {
                int d = buffer.buffer[buffer.tail];
                buffer.tail++;
                buffer.tail %= buffer.capacity;
                buffer.lock.notifyAll();
                return d;

            } else {
                return -1;
            }
        }
    }

    @Override
    public void close() {
        buffer.readOpened.set(false);
        synchronized (buffer.lock) {
            buffer.lock.notifyAll();
        }
    }
}
class BufferProvider extends OutputStream {

    private final StreamBuffer buffer;

    public BufferProvider(StreamBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void write(int b) throws IOException {
        synchronized (buffer.lock) {
            if (buffer.capacity() <= 0 && buffer.readOpened.get()) {
                try {
                    /*
                    System.out.println("[StreamBuffer][OutputStream][write] capacity() = " + buffer.capacity());
                    System.out.println("[StreamBuffer][OutputStream][write] head = " + buffer.head);
                    System.out.println("[StreamBuffer][OutputStream][write] tail = " + buffer.tail);
                    */
                    buffer.lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (!buffer.readOpened.get()) {
                return;
            }

            if (buffer.capacity() > 0) {
                buffer.buffer[buffer.head] = (byte) b;
                buffer.head++;
                buffer.head %= buffer.capacity;
                buffer.lock.notifyAll();

            } else {
                throw new IOException("Buffer is full");
            }
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {

       synchronized (this.buffer.lock) {
          for (int i = 0; i < len; i++) {
             write(b[off + i]);
          }
       }

       System.out.println("[StreamBuffer][OutputStream][write] " + System.nanoTime());
    }

    @Override
    public void close() {
        buffer.writeOpened.set(false);
        synchronized (buffer.lock) {
            buffer.lock.notifyAll();
        }
       System.out.println("[StreamBuffer][OutputStream][close] " + System.nanoTime());
    }
}