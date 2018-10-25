package net.server;

import net.common.Constants;
import net.common.StreamGenerator;
import net.connect.Session;
import persistence.LocalSong;

import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SongStreamHandler extends StreamGenerator {

    private byte[] trx;

    private LocalSong song;

    private Future<AudioInputStream> in;

    public SongStreamHandler(Session session, LocalSong song) {
        super(session);
        this.song = song;
    }

    @Override
    protected void initialize() throws IOException {
        super.initialize();
        this.trx = new byte[Constants.PACKET_SIZE];
        this.in = this.song.getStream();
    }

    @Override
    protected void transfer(int maxSize) throws Exception {
        if (in.isDone()) {
            InputStream in;
            try {
                in = this.in.get();

            } catch (ExecutionException e){
                // well thats a problem...
                throw e;
            }

            int l = in.read(this.trx, 0, Math.min(trx.length, maxSize));
            this.dest.write(this.trx, 0, l);
            if (l == -1) {
                in.close();
                this.finished();

            } else if (l == 1) {
                this.waitingForSource();
            }

        } else {
            this.waitingForSource();
        }
    }
}
