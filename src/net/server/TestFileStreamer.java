package net.server;

import net.common.StreamGenerator;
import net.connect.Session;
import net.lib.Socket;
import persistence.DataManager;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

public class TestFileStreamer extends StreamGenerator {

    private byte[] trx;

    private File testFile;

    private BufferedInputStream src;
    private CheckedInputStream check;

    boolean doCopy;

    public TestFileStreamer(Socket socket) {
        super(socket, true);
        this.testFile = new File(DataManager.rootDirectory.getPath() + "/Media/Artists/Antonio Vivaldi/Classic/Autumn.wav");
        this.doCopy = false;
    }

    @Override
    protected void initialize() throws IOException {
        super.initialize();
        this.trx = new byte[4096];
        this.check = new CheckedInputStream(new FileInputStream(this.testFile), new CRC32());
        this.src = new BufferedInputStream(this.check);
    }

    @Override
    protected void transfer(int maxSize) throws Exception {
        if (this.doCopy) this.src.mark(4096);
        int amnt = this.src.read(this.trx, 0, Math.min(maxSize, this.trx.length));
        if (amnt == -1) {
           System.out.println("[TestFileStreamer] CRC32=" + this.check.getChecksum().getValue());
            this.src.close();
            this.finished();
            return;
        }

        this.dest.write(this.trx, 0, amnt);

        if (this.doCopy) {
            this.src.reset();
            this.doCopy = false;
        }
    }

}
