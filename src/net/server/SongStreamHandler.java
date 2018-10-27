package net.server;

import net.Constants;
import net.lib.Socket;
import persistence.LocalSong;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SongStreamHandler implements Runnable {

    private byte[] trx;

    private Socket socket;
    private OutputStream dest;

    private LocalSong song;

    private Future<InputStream> in;

    public SongStreamHandler(Socket socket, LocalSong song) {
        this.socket = socket;
        this.song = song;
    }

    protected void initialize() throws IOException {
        this.dest = this.socket.outputStream();
        this.trx = new byte[Constants.PACKET_SIZE];
        this.in = this.song.getRawStream();
    }

    @Override
    public void run() {
        try {
            this.initialize();
        } catch (IOException e) {
            e.printStackTrace();
        }

        InputStream in  = null;
        try {
            in = this.in.get();
            System.out.println("[SongStreamHandler][run] Audio file opened");

            int amnt = 0;
            while (!this.socket.isSendClosed() && (amnt = in.read(trx, 0, trx.length)) != -1) {
                this.dest.write(trx, 0, amnt);
            }

            if (this.socket.isSendClosed()) {
                System.out.println("[SongStreamHandler][run] Socket is closed");
                in.close();

            } else {
                System.out.println("[SongStreamHandler][run] End of file");
                in.close();
                this.socket.close();
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
