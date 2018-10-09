package utils;

/**
 * Implements a circular buffer.
 */
public class CircularBuffer {

    private byte[] buffer;

    private int head = 0;
    private int tail = 0;
    private int size = 0;

    /**
     * Creates a new CircularBuffer with the indicated capacity.
     *
     * @param size the size of the buffer to create
     */
    public CircularBuffer(int size) {
        this.size = size;
        this.buffer = new byte[size];
    }

    /**
     * Writes the given byte to the head of the circular buffer.
     *
     * @param b the byte to write
     */
    public void write(int b) {
        this.buffer[this.head % this.size] = ((byte) b);
        this.head++;
        this.head %= this.size;
        if (this.head - 1 == this.tail || this.head - 1 == -1) {
            this.tail++;
            this.tail %= this.size;
        }
    }

    /**
     * Writes the given byte array into the buffer.
     *
     * @param buf the data to write
     * @param offset an offset to start writing from
     * @param len the length of data to write
     */
    public void write(byte[] buf, int offset, int len) {
        for (int i = offset; i < offset + len; i++) {
            this.write(buf[i]);
        }
    }

    /**
     * Returns the amount of unused space in the buffer.
     *
     * @return available space
     */
    public int space() {
        if (this.tail < this.head) {
            return this.tail + (this.size - this.head);

        } else {
            return this.tail - this.head;
        }
    }

    /**
     * Returns the amount of available data in the buffer.
     *
     * @return available data
     */
    public int available() {
        if (this.tail < this.head) {
            return this.head - this.tail;

        } else {
            return (this.size - this.tail) + this.head;
        }
    }

    /**
     * Reads a single byte from the buffer, returning -1 if the buffer is empty.
     *
     * @return next byte
     */
    public int read() {
        if (this.tail == this.head) {
            return -1;

        } else {
            return this.buffer[this.tail++ % this.size];
        }
    }

    /**
     * Reads up to len bytes from the buffer into the given byte[], starting at offset.
     *
     * @param buf a byte[] to read data into
     * @param offset an offset to start at in buf
     * @param len the maximum number of bytes to read
     * @return the number of bytes read
     */
    public int read(byte[] buf, int offset, int len) {
        int r = 0;
        for (int i = offset; i < offset + len && this.available() > 0; i++, r++) {
            if (this.tail == this.head) break;
            buf[i] = this.buffer[this.tail++];
            this.tail %= this.size;
        }
        return r;
    }
}
