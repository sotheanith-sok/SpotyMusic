package utils;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SongStreamer {

    private AudioInputStream source;
    private AtomicBoolean newSource;

    private SourceDataLine dest;

    private Thread streamer;
    private final Object lock;
    private boolean running;
    private volatile AtomicBoolean playing;

    public SongStreamer(SourceDataLine dest) {
        this.dest = dest;
        this.lock = new Object();
        this.running = true;
        this.playing = new AtomicBoolean(false);

        this.streamer = new Thread(this::streamer);
        this.streamer.setName("[SongStreamer][streamer]");
        this.streamer.start();
        this.newSource = new AtomicBoolean(false);
    }

    private void streamer() {
        byte[] trx = new byte[8192];
        while (this.running) {
            synchronized (this.lock) {
                if (!this.playing.get()) {
                    try {
                        System.out.println("[SongStreamer][streamer] SongStreamer streamer thread sleeping");
                        this.lock.wait();
                        Thread.sleep(100);
                        //System.out.println("[SongStreamer][streamer] SongStreamer streamer thread awoken");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                } else {
                    System.out.println("[SongStreamer][streamer] SongStreamer Streamer thread streaming");
                    while (this.playing.get()) {
                        try {
                            if (this.newSource.get()) {
                                this.dest.close();
                                try {
                                    this.dest.open(this.source.getFormat(), 8196);
                                    this.dest.start();
                                } catch (LineUnavailableException e) {
                                    e.printStackTrace();
                                }
                                this.newSource.set(false);
                            }

                            int amnt = this.source.read(trx, 0, trx.length);
                            this.dest.write(trx, 0, amnt);
                            //System.out.println("[SongStreamer][streamer] Wrote " + amnt + " bytes to SourceDataLine");

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        System.out.println("[SongStreamer][streamer] SongStreamer streamer thread terminating");
    }

    public boolean isPlaying() {
        return this.playing.get();
    }

    public void play(AudioInputStream stream) {
        synchronized (this.lock) {
            try {
                if (this.source != null) this.source.close();

            } catch (IOException e) {
                e.printStackTrace();
            }

            this.source = stream;
            this.playing.set(true);
            this.newSource.set(true);
            this.lock.notifyAll();
        }
    }

    public void stop() {
        synchronized (this.lock) {
            this.playing.set(false);
            this.dest.stop();
        }
    }

    public void resume() {
        synchronized (this.lock) {
            this.playing.set(true);
            this.dest.start();
            this.lock.notifyAll();
        }
    }

    public int getFramePosisiton() {
        return this.dest.getFramePosition();
    }

    public long getLongFramePosition() {
        return this.dest.getLongFramePosition();
    }

    public long getMicrosecondPosition() {
        return this.dest.getMicrosecondPosition();
    }
}
