package net.client;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import connect.Library;
import connect.Song;
import net.common.Constants;
import net.common.JsonField;
import net.common.SimpleJsonWriter;
import net.connect.Session;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.*;

public class RemoteSong implements Song {

    private static JsonFactory factory = new JsonFactory();

    private final RemoteLibrary library;

    private final String title;
    private final String artist;
    private final String album;
    private final long duration;
    private final long id;

    public RemoteSong(RemoteLibrary library, String title, String artist, String album, long duration, long id) {
        this.library = library;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.id = id;
    }

    public long getId() {
        return this.id;
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    public String getArtist() {
        return this.artist;
    }

    @Override
    public String getAlbumTitle() {
        return this.album;
    }

    @Override
    public long getDuration() {
        return this.duration;
    }

    @Override
    public Library getLibrary() {
        return this.library;
    }

    @Override
    public Future<AudioInputStream> getStream() {
        CompletableFuture<AudioInputStream> future = new CompletableFuture<>();

        Thread t = new Thread(() -> {
            System.out.println("[RemoteSong][getStream] Requesting song stream");

            Session session = this.library.getSession();
            session.debug = true;
            System.out.println("[RemoteSong][getStream] Opened Session");

            SimpleJsonWriter request = new SimpleJsonWriter(session);
            JsonField.ObjectField packet = JsonField.emptyObject();
            packet.setProperty(Constants.REQUEST_TYPE_PROPERTY, Constants.REQUEST_STREAM_SONG);
            packet.setProperty("id", this.getId());
            request.que(packet);
            request.complete();
            library.taskManager.submit(request);

            System.out.println("[RemoteSong][getStream] Request sending");
            System.out.println("[RemoteSong][getStream] Waiting for response data");

            InputStream in = session.inputStream();
            try {
                int i = 0;
                do {
                    try {
                        Thread.sleep(100);

                    } catch (InterruptedException e) {
                        System.err.println("[RemoteSong][getStream] Interrupted while waiting for stream data");
                        e.printStackTrace();
                    }

                    i++;
                } while (in.available() < Constants.MIN_BUFFERED_DATA && session.isInputOpened() && i < 100);
                if (i == 100) {
                    session.close();
                    System.err.println("[RemoteSong][getStream] Request timed out");
                    future.completeExceptionally(new TimeoutException("Request for song stream timed out"));
                    return;
                }
            } catch (IOException e) {
                System.err.println("[RemoteSong][getStream] IOException while checking buffered data");
                future.completeExceptionally(e);
                return;
            }

            if (!session.isInputOpened()) {
                future.completeExceptionally(new IOException("Connection closed unexpectedly"));
                return;
            }

            try {
                future.complete(AudioSystem.getAudioInputStream(in));

            } catch (UnsupportedAudioFileException e) {
                System.err.println("[RemoteSong][getStream] Audio stream format not supported");
                future.completeExceptionally(e);

            } catch (IOException e) {
                System.err.println("[RemoteSong][getStream] IOException while creating AudioInputStream");
                future.completeExceptionally(e);
            }

        });
        t.setDaemon(true);
        t.setName("[RemoteSong][SongStreamRequester]");
        t.start();

        return future;
    }
}
