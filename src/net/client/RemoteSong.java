package net.client;

import com.fasterxml.jackson.core.JsonFactory;
import connect.Library;
import connect.Song;
import net.common.Constants;
import net.common.JsonField;
import net.common.SocketJsonWriter;
import net.lib.Socket;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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

            Socket socket = null;
            try {
                socket = this.library.getConnection(Constants.BUFFER_SIZE, 1024 * 150);
                //socket.debug = Constants.FINE;

            } catch (SocketException e) {
                System.err.println("[RemoteSong][getStream] SocketException while opening connection");
                future.completeExceptionally(e);

            } catch (SocketTimeoutException e) {
                System.err.println("[RemoteSong][getStream] Timed out while trying to connect");
                e.printStackTrace();
            }
            System.out.println("[RemoteSong][getStream] Opened Socket");

            SocketJsonWriter request = new SocketJsonWriter(socket, false);
            JsonField.ObjectField packet = JsonField.emptyObject();
            packet.setProperty(Constants.REQUEST_TYPE_PROPERTY, Constants.REQUEST_STREAM_SONG);
            packet.setProperty("id", this.getId());
            request.que(packet);
            request.complete();
            library.taskManager.submit(request);

            System.out.println("[RemoteSong][getStream] Request sending");
            System.out.println("[RemoteSong][getStream] Waiting for response data");

            InputStream in = socket.inputStream();
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
                } while (in.available() < Constants.MIN_BUFFERED_DATA && !socket.isReceiveClosed() && i < 100);
                if (i == 100) {
                    socket.close();
                    System.err.println("[RemoteSong][getStream] Request timed out");
                    future.completeExceptionally(new TimeoutException("Request for song stream timed out"));
                    return;
                }
            } catch (IOException e) {
                System.err.println("[RemoteSong][getStream] IOException while checking buffered data");
                future.completeExceptionally(e);
                return;
            }

            if (socket.isReceiveClosed()) {
                future.completeExceptionally(new IOException("Connection closed unexpectedly"));
                return;
            }

            try {
                future.complete(AudioSystem.getAudioInputStream(new BufferedInputStream(in)));
                System.out.println("[RemoteSong][getStream] It worked!");

            } catch (UnsupportedAudioFileException e) {
                System.err.println("[RemoteSong][getStream] Audio stream format not supported");
                socket.close();
                future.completeExceptionally(e);

            } catch (IOException e) {
                System.err.println("[RemoteSong][getStream] IOException while creating AudioInputStream");
                socket.close();
                future.completeExceptionally(e);
            }
        });
        t.setDaemon(true);
        t.setName("[RemoteSong][SongStreamRequester]");
        t.start();

        return future;
    }
}
