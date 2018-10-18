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

    protected int mark;
    protected int readLimit;
    protected int readSinceMark;
    protected int readSinceReset;

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
        this.mark = Integer.MAX_VALUE;
        this.readLimit = 0;
        this.readSinceMark = 0;
        this.readSinceReset = 0;

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

    public boolean isWriteOpened() {
        return this.writeOpened.get() && this.readOpened.get();
    }

    public boolean isReadOpened() {
        return this.available() > 0 || this.writeOpened.get();
    }

    public int capacity() {
        synchronized (lock) {
            int effectiveTail;
            if (mark < capacity) {
                effectiveTail = mark < tail ? mark : tail;

            } else {
                effectiveTail = tail;
            }

            if (effectiveTail == head) {
                return capacity - 1;

            } else if (effectiveTail < head) {
                return capacity - (head - ((effectiveTail + 1) % capacity));

            } else {
                return ((effectiveTail + 1) % capacity) - head;
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
    public boolean markSupported() {
        return true;
    }

    @Override
    public void mark(int readLimit) {
        synchronized (buffer.lock) {
            buffer.mark = buffer.tail;
            buffer.readLimit = readLimit;
            buffer.readSinceMark = 0;
            System.out.println("[StreamBuffer][BufferConsumer] BufferConsumer mark set with readLimit of " + readLimit);
        }
    }

    @Override
    public void reset() throws IOException {
        synchronized (buffer.lock) {
            if (buffer.mark > buffer.capacity) throw new IOException("Cannot reset to invalid mark");
            buffer.tail = buffer.mark;
            System.out.println("[StreamBuffer][BufferConsumer][reset] BufferConsumer mark was reset after reading " + buffer.readSinceMark + " bytes");
            buffer.readSinceReset = 0;
        }
    }

    @Override
    public int read() throws IOException {
        if (!buffer.readOpened.get()) {
            System.out.println("[StreamBuffer][BufferConsumer][read] Attempt to read closed buffer");
            return -1;
        }
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
                buffer.readSinceReset++;
                buffer.tail %= buffer.capacity;

                if (buffer.mark < buffer.capacity) {
                    if (buffer.readSinceMark > buffer.readLimit) {
                        buffer.mark = Integer.MAX_VALUE;
                    }
                }

                if (d == -1) System.err.println("[StreamBuffer][BufferConsumer][read] Read -1 from byte array. Thats not possible.");
                //System.out.println("[StreamBuffer][BufferConsumer][read] read byte: " + d);
                buffer.lock.notifyAll();
                return d;

            } else {
                System.out.println("[StreamBuffer][BufferConsumer][read] End of input after reading " + buffer.readSinceReset + " bytes since reset");
                buffer.lock.notifyAll();
                return -1;
            }
        }
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (!buffer.readOpened.get()) {
            System.err.println("[StreamBuffer][BufferConsumer] Attempt to read closed buffer");
            return -1;
        }
        synchronized (buffer.lock) {
            int toRead = Math.min(this.available(), len);

            while (toRead == 0) {
                try {
                    buffer.lock.wait();
                } catch (InterruptedException e) {}

                if (!buffer.readOpened.get() && buffer.available() == 0) {
                    //System.err.println("[SteamBuffer][BufferConsumer][read] StreamBuffer Consumer closed while waiting for input");
                    return -1;
                }

                if (buffer.available() == 0 && !buffer.writeOpened.get()) {
                    //System.err.println("[StreamBuffer][BufferConsumer][read] StreamBuffer Producer closed while waiting for input");
                    return -1;
                }

                toRead = Math.min(this.available(), len);
            }

            for (int i = 0; i < toRead; i++) {
                b[off + i] = buffer.buffer[buffer.tail];
                buffer.tail++;
                buffer.tail %= buffer.capacity;
                buffer.readSinceReset++;
                buffer.readSinceMark++;
            }

            if (buffer.mark < buffer.capacity) {
                if (buffer.readSinceMark > buffer.readLimit) {
                    buffer.mark = Integer.MAX_VALUE;
                }
            }

            buffer.lock.notifyAll();
            return toRead;
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
        if (!buffer.writeOpened.get()) throw new IOException("Buffer is closed to writing");
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
        if (!buffer.writeOpened.get()) throw new IOException("Buffer is closed to writing");
        synchronized (this.buffer.lock) {
            int written = 0;
            do {
                int toWrite = Math.min(len - written, buffer.capacity());
                for (; written < toWrite; written++) {
                    buffer.buffer[buffer.head] = b[off + written];
                    buffer.head++;
                    buffer.head %= buffer.capacity;
                }

                if (written < len) {
                    try {
                        this.buffer.lock.wait();
                    } catch (InterruptedException e) {}
                }

            } while (written < len && buffer.writeOpened.get());

            this.buffer.lock.notifyAll();
        }
        //System.out.println("[StreamBuffer][BufferProvider][write] Wrote " + len + " bytes");
    }

    @Override
    public void close() {
        buffer.writeOpened.set(false);
        synchronized (buffer.lock) {
            buffer.lock.notifyAll();
        }
    }

    @Override
    public void flush() {
        //System.out.println("[StreamBuffer][BufferProvider][flush] Flush!");
    }
}