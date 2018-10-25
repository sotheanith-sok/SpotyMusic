package utils;

import java.io.IOException;
import java.io.OutputStream;

public class CountingOutputStream extends OutputStream {

    private long count;

    private OutputStream out;

    public CountingOutputStream(OutputStream out) {
        this.out = out;
        this.count = 0;
    }

    public long getCount() {
        return this.count;
    }

    public void reset() {
        this.count = 0;
    }

    @Override
    public void close() throws IOException {
        this.out.close();
    }

    @Override
    public void flush() throws IOException {
        this.out.flush();
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.out.write(b);
        this.count += b.length;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.out.write(b, off, len);
        this.count += len;
    }

    @Override
    public void write(int b) throws IOException {
        this.out.write(b);
        this.count++;
    }
}
