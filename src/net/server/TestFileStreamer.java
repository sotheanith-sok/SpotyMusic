package net.server;

import net.Constants;
import net.common.StreamGenerator;
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

    public TestFileStreamer(Socket socket) {
        super(socket, true);
        this.testFile = new File(DataManager.rootDirectory.getPath() + "/Media/Artists/Taylor Davis/Enchanted Christmas/Greensleeves.wav");
        //this.testFile = new File(DataManager.rootDirectory.getPath() + "/TestFile.txt");
    }

    @Override
    protected void initialize() throws IOException {
        super.initialize();
        this.trx = new byte[8192];
        this.check = new CheckedInputStream(new FileInputStream(this.testFile), new CRC32());
        this.src = new BufferedInputStream(this.check);
        System.out.println("[TestFileStreamer][initialize] TestFileStreamer initialized");
    }

    @Override
    protected void transfer(int maxSize) throws Exception {
        System.out.println("[TestFileStreamer][transfer] Reading from file");
        int amnt = this.src.read(this.trx, 0, Math.min(maxSize, this.trx.length));
        if (amnt == -1) {
            System.out.println("[TestFileStreamer][transfer] CRC32=" + this.check.getChecksum().getValue());
            this.src.close();
            this.finished();
            return;
        }

        if (this.socket.isSendClosed()) {
            System.out.println("TestFileStreamer][transfer] Socket closed");
            this.finished();
            return;
        }

        System.out.println("[TestFileStreamer][transfer] Transferring " + amnt + " bytes to socket");
        this.dest.write(this.trx, 0, amnt);
        System.out.println("[TestFileStreamer][transfer] Transferred " + amnt + " bytes to socket");
    }

}
