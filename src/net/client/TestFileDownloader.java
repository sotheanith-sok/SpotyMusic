package net.client;

import net.lib.Socket;
import persistence.DataManager;
import utils.CompletableRunnable;

import java.io.*;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

public class TestFileDownloader implements CompletableRunnable {

    private Socket socket;

    private byte[] trx;

    private File dest;

    private BufferedOutputStream out;
    private CheckedOutputStream check;

    private InputStream src;

    private boolean initialized;

    public TestFileDownloader(Socket socket) {
        this.socket = socket;
        this.dest = new File(DataManager.rootDirectory.getPath() + "/TestFile.wav");
        this.initialized = false;
    }

    public void initialize() throws IOException {
        if (!this.dest.exists()) this.dest.createNewFile();
        this.trx = new byte[4096];
        this.check = new CheckedOutputStream(new FileOutputStream(this.dest), new CRC32());
        this.out = new BufferedOutputStream(check);
        this.src = this.socket.inputStream();
        this.initialized = true;
    }

    @Override
    public boolean run() throws Exception {
        if (!this.initialized) this.initialize();
        if (this.socket.isSendClosed()) {
            this.out.close();
            return true;
        }

        int amnt = this.src.read(this.trx, 0, this.trx.length);
        if (amnt == -1) {
           System.out.println("[TestFileDownloader] CRC32 = " + this.check.getChecksum().getValue());
            this.out.close();
            return true;
        }

        this.out.write(this.trx, 0, amnt);

        return false;
    }
}
