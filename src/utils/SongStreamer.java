package utils;

import connect.Song;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SongStreamer {

    private Song song;

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
        byte[] trx = null;
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
                                this.source = this.song.getStream().get(5, TimeUnit.SECONDS);
                                try {
                                    this.dest.open(this.source.getFormat(), 1024 * 16);
                                    if (trx == null || trx.length % dest.getFormat().getFrameSize() != 0)
                                    trx = new byte[1024 * 4 * dest.getFormat().getFrameSize()];
                                } catch (LineUnavailableException e) {
                                    e.printStackTrace();
                                }
                                this.newSource.set(false);
                            }

                            this.dest.start();

                            //System.out.println("[SongStreamer][streamer] Reading from socket");
                            int trxd = 0;
                            while ((trxd = this.source.read(trx, 0, trx.length)) != -1 && this.playing.get()) {
                                //System.out.println("[SongStreamer][streamer] Writing to SourceDataLine");
                                this.dest.write(trx, 0, trxd);
                                //System.out.println("[SongStreamer][streamer] Wrote " + trxd + " bytes to SourceDataLine");
                            }

                            this.dest.stop();

                        } catch (IOException e ) {
                            e.printStackTrace();

                        } catch (TimeoutException | ExecutionException | InterruptedException e) {
                            System.err.println("[SongStreamer][streamer] Unable to obtain stream for new song");
                            e.printStackTrace();
                            this.playing.set(false);
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

    public void play(Song song) {
        if (this.isPlaying()) this.stop();
        synchronized (this.lock) {
            try {
                if (this.source != null) this.source.close();

            } catch (IOException e) {
                e.printStackTrace();
            }

            this.song = song;
            this.source = null;
            this.playing.set(true);
            this.newSource.set(true);
            this.lock.notifyAll();
        }
    }

    public void stop() {
        this.playing.set(false);
    }

    public void resume() {
        if (this.playing.compareAndSet(false, true)) {
            synchronized (this.lock) {
                this.lock.notifyAll();
            }
        }
    }

    public void setVolume(double value) {
        if (this.dest != null && this.dest.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) this.dest.getControl(FloatControl.Type.MASTER_GAIN);
            double gain = value / 100;
            float dB = (float) (Math.log(gain) / Math.log(10.0) * 20.0);
            gainControl.setValue(dB);
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

    public long getMicrosecondLength() {
        if (this.song != null) return TimeUnit.SECONDS.toMicros(this.song.getDuration());
        else return 0;
    }
}
