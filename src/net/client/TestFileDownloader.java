package net.client;

import net.common.Constants;
import net.lib.Socket;
import persistence.DataManager;

import java.io.*;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

public class TestFileDownloader implements Runnable {

    private Socket socket;

    private byte[] trx;

    private File dest;

    private BufferedOutputStream out;
    private CheckedOutputStream check;

    private InputStream src;

    public TestFileDownloader(Socket socket) {
        this.socket = socket;
        socket.debug = Constants.FINER;
        this.dest = new File(DataManager.rootDirectory.getPath() + "/TestFile.wav");
        //this.dest = new File(DataManager.rootDirectory.getPath() + "/TestFile1.txt");
    }

    public void initialize() throws IOException {
        if (!this.dest.exists()) this.dest.createNewFile();
        this.trx = new byte[16384];
        this.check = new CheckedOutputStream(new FileOutputStream(this.dest), new CRC32());
        this.out = new BufferedOutputStream(check);
        this.src = this.socket.inputStream();
        System.out.println("[TestFileDownloader][initialize] TestFileDownloader initialized");
    }

    @Override
    public void run() {
        try {
            this.initialize();

            while (true) {
                if (this.socket.isSendClosed()) {
                    this.out.close();
                    System.out.println("[TestFileDownloader][run] Socket closed");
                    break;
                }

                System.out.println("[TestFileDownloader] Reading from socket");
                int amnt = this.src.read(this.trx, 0, this.trx.length);
                if (amnt == -1) {
                    System.out.println("[TestFileDownloader] CRC32 = " + this.check.getChecksum().getValue());
                    this.out.close();
                    break;
                }

                System.out.println("[TestFileDownloader][run] Writing " + amnt + " bytes to file");
                this.out.write(this.trx, 0, amnt);
                System.out.println("[TestFileDownloader][run] Wrote " + amnt + " bytes to file");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
