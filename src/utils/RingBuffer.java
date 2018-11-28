package utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RingBuffer {

    private final Object lock;

    private final int size;
    private final byte[] buffer;

    private final AtomicInteger head;
    private final AtomicInteger tail;

    private AtomicInteger tailMark;
    private int readLimit;
    private AtomicInteger readSinceMark;

    private final AtomicBoolean writeOpened;
    private final AtomicBoolean readOpened;

    private final int lowWaterMark;
    private final int highWaterMark;

    private Runnable onLowMark;

    public RingBuffer(int size, int lowWaterMark, int highWaterMark) {
        this.size = size;
        this.buffer = new byte[size];

        this.lock = new Object();

        this.head = new AtomicInteger(0);
        this.tail = new AtomicInteger(0);

        this.tailMark = new AtomicInteger(-1);
        this.readLimit = 0;
        this.readSinceMark = new AtomicInteger(0);

        this.writeOpened = new AtomicBoolean(true);
        this.readOpened = new AtomicBoolean(true);

        this.lowWaterMark = lowWaterMark;
        this.highWaterMark = highWaterMark;
    }

    public RingBuffer(int size) {
       this(size, 0, size);
    }

    public int size() {
        return this.size;
    }

    public int available() {
        synchronized (this.lock) {
            int tail = this.tail.get();
            int head = this.head.get();

            return head >= tail ? head - tail : head + (size - tail);
        }
    }

    public int capacity() {
        synchronized (this.lock) {
            int tail = this.tailMark.get() >= 0 ? tailMark.get() : this.tail.get();
            int head = this.head.get();
            int cap = 0;
            if (head > tail) cap = tail + (size - head);
            else if (tail > head) cap = tail - head;
            else cap = size;
            cap = Math.max(cap - 1, 0);

            //System.out.println("[RingBuffer][capacity] head=" + head + " tail=" + tail + " cap=" + cap);

            return cap;
        }
    }

    public InputStream getInputStream() {
        return this.new BufferConsumer();
    }

    public OutputStream getOutputStream() {
        return this.new BufferProvider();
    }

    public void setLowWaterMarkListener(Runnable listener) {
       this.onLowMark = listener;
    }

    public boolean isWriteOpened() {
        return this.writeOpened.get();
    }

    public boolean isReadOpened() {
        return this.available() > 0 || this.readOpened.get();
    }

    class BufferProvider extends OutputStream {

        @Override
        public void close() {
            synchronized (lock) {
                //System.out.println("[RingBuffer][BufferProvider] BufferProvider closed");
                writeOpened.set(false);
                lock.notifyAll();
            }
        }

        @Override
        public void flush() {
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            synchronized (lock) {
                while (len > 0) {
                    if (!writeOpened.get()) throw new IOException("BufferProvider is closed");

                    int toWrite = Math.min(capacity(), len);

                    if (toWrite == 0) {
                        //System.out.println("[RingBuffer][BufferProvider][write] BufferProvider waiting for space");
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            //System.out.println("[RingBuffer][BufferProvider][write] BufferProvider interrupted while waiting for space");
                        }
                    }

                    for (int i = 0; i < toWrite; i++) {
                        buffer[head.getAndAccumulate(1, (l, r) -> (l + r) % size)] = b[off + i];
                    }

                    len -= toWrite;
                    off += toWrite;

                    lock.notifyAll();

                    if (len > 0 && writeOpened.get()) {
                        try {
                            //System.out.println("[RingBuffer][BufferProvider][write] BufferProvider waiting for space");
                            lock.wait(1000, 0);
                        } catch (InterruptedException e) {}
                    }
                }
            }
        }

        @Override
        public void write(int b) throws IOException {
            synchronized (lock) {
                while (true) {
                    if (!writeOpened.get()) throw new IOException("BufferProvider is closed");

                    if (capacity() > 1) {
                        buffer[head.getAndAccumulate(1, (l, r) -> (l + r) % size)] = (byte) b;
                        break;

                    } else {
                        try {
                            lock.wait(1000, 0);
                        } catch (InterruptedException e) {}
                    }
                }

                lock.notifyAll();
            }
        }
    }

    class BufferConsumer extends InputStream {
        @Override
        public int available() {
            return RingBuffer.this.available();
        }

        @Override
        public void close() {
            synchronized (lock) {
                readOpened.set(false);
                writeOpened.set(false);
                lock.notifyAll();
            }
        }

        @Override
        public void mark(int readLimit) {
            readLimit = Math.max(readLimit, 200);
            synchronized (lock) {
                //System.err.println("Mark set at " + tail.get() + " readLimit = " + readLimit);
                tailMark.set(tail.get());
                RingBuffer.this.readLimit = readLimit;
                readSinceMark.set(0);
                lock.notifyAll();
            }
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public int read() {
            synchronized (lock) {
                if (!readOpened.get()) return -1;
                while (available() == 0) {
                    if (!writeOpened.get()) readOpened.set(false);
                    if (!readOpened.get()) return -1;
                    try {
                        lock.wait(1000, 0);
                    } catch (InterruptedException e) {}
                }

                byte b = buffer[tail.getAndAccumulate(1, (l, r) -> (l + r) % size)];

                int mark = tailMark.get();
                if (mark >= 0) {
                    if (readSinceMark.incrementAndGet() > readLimit) {
                        System.err.println("Mark read limit exceeded after reading " + readSinceMark.get() + " bytes");
                        tailMark.set(-1);
                        readSinceMark.set(0);
                    }
                }

                lock.notifyAll();
                int ret = b;
                return ret & 0xFF;
            }
        }

        @Override
        public int read(byte[] b) {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            synchronized (lock) {
                if (!readOpened.get()) return -1;

                while (available() == 0) {
                    if (!writeOpened.get()) readOpened.set(false);
                    if (!readOpened.get()) return -1;

                    try {
                        lock.wait(1000, 0);
                    } catch (InterruptedException e) {}
                }

                int toRead = Math.min(len, available());
                for (int i = 0; i < toRead; i++) {
                    b[off + i] = buffer[tail.getAndAccumulate(1, (l, r) -> (l + r) % size)];
                    if (tailMark.get() >= 0) {
                        if (readSinceMark.incrementAndGet() > readLimit) {
                            //System.err.println("Mark read limit exceeded after reading " + readSinceMark.get() + " bytes");
                            tailMark.set(-1);
                        }
                    }
                }

                //System.out.println("[RingBuffer][BufferConsumer][read] Read " + len + " bytes from buffer");

                lock.notifyAll();
                if (available() <= lowWaterMark && onLowMark != null) onLowMark.run();
                return toRead;
            }
        }

        @Override
        public void reset() throws IOException {
            synchronized (lock) {
                if (tailMark.get() < 0) throw new IOException("No valid mark to reset to");
                //System.err.println("Mark reset after reading " + readSinceMark.get() + " bytes");
                tail.set(tailMark.get());
                readSinceMark.set(0);
                lock.notifyAll();
            }
        }
        /*
        @Override
        public long skip(long n) {
            synchronized (lock) {
                long toSkip = Math.min((long) this.available(), n);
                int newTail = tail.addAndGet((int) toSkip);
            }
        }
        */
    }
}
