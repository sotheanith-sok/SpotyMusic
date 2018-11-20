package mesh.library;

import connect.Library;
import connect.Song;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.*;

public class MeshClientSong implements Song {

    private String title;

    private String artist;

    private String album;

    private long duration;

    private String fileName;

    private MeshLibrary library;

    public MeshClientSong(String title, String artist, String album, long duration, String fileName, MeshLibrary library) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.fileName = fileName;
        this.library = library;
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
    public long getId() {
        return this.fileName.hashCode();
    }

    public String getGUID() {
        return this.fileName;
    }

    @Override
    public Future<AudioInputStream> getStream() {
        CompletableFuture<AudioInputStream> future = new CompletableFuture<>();

        this.library.onSongPlayed(this);

        this.library.executor.submit(() -> {
            try {
                Future<InputStream> file = this.library.dfs.readFile(this.fileName);
                InputStream in = file.get(5, TimeUnit.SECONDS);
                if (future.isCancelled()) {
                    in.close();
                    return;
                }
                System.out.println("[MeshClientSong][getStream] Got InputStream, creating AudioInputStream");

                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(in);
                System.out.println("[MeshClientSong][getStream] Got AudioInputStream");

                future.complete(audioInputStream);

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println("[MeshClientSong][getStream] Exception while trying to read from DFS");
                e.printStackTrace();
                future.completeExceptionally(e);

            } catch (UnsupportedAudioFileException e) {
                System.err.println("[MeshClientSong][getStream] Unable to read data from DFS");
                e.printStackTrace();
                future.completeExceptionally(e);

            } catch (IOException e) {
                System.err.println("[MeshClientSong][getStream] IOException while constructing AudioInputStream");
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        return future;
    }
}
