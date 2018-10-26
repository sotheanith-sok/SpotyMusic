package utils;

import java.util.zip.Checksum;

/**
 * https://github.com/ztellman/byte-transforms/blob/master/src/byte_transforms/CRC64.java
 */
public class CRC64 implements Checksum {

    private static final long poly = 0xC96C5795D7870F42L;
    private static final long crcTable[] = new long[256];

    private long crc = -1;

    static {
        for (int b = 0; b < crcTable.length; ++b) {
            long r = b;
            for (int i = 0; i < 8; ++i) {
                if ((r & 1) == 1)
                    r = (r >>> 1) ^ poly;
                else
                    r >>>= 1;
            }

            crcTable[b] = r;
        }
    }

    public CRC64() {
    }

    public void update(byte b) {
        crc = crcTable[(b ^ (int) crc) & 0xFF] ^ (crc >>> 8);
    }

    public void update(byte[] buf) {
        update(buf, 0, buf.length);
    }

    @Override
    public void update(int b) {
        update((byte) b);
    }

    public void update(byte[] buf, int off, int len) {
        int end = off + len;

        while (off < end)
            crc = crcTable[(buf[off++] ^ (int) crc) & 0xFF] ^ (crc >>> 8);
    }

    public long getValue() {
        return ~crc;
    }

    @Override
    public void reset() {
        crc = -1;
    }
}
