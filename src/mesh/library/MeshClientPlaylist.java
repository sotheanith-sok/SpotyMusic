package mesh.library;

import connect.Library;
import connect.Playlist;
import connect.Song;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class MeshClientPlaylist implements Playlist {

    private String title;

    private MeshLibrary library;

    private FilteredList<? extends Song> songs;

    private List<Long> ids;

    private PlaylistChangeListener listener = null;

    public MeshClientPlaylist(String title, List<Long> songs, MeshLibrary library) {
        this.title = title;
        this.ids = ids;
        this.library = library;
        this.songs = this.library.getSongs().filtered(this::filter);
    }

    public MeshClientPlaylist(String title, MeshLibrary library) {
        this.title = title;
        this.ids = new LinkedList<>();
        this.library = library;
        this.songs = this.library.getSongs().filtered(this::filter);
    }

    private boolean filter(Song song) {
        return this.ids.contains(song.getId());
    }

    public void setChangeListener(PlaylistChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public String getName() {
        return title;
    }

    @Override
    public ObservableList<? extends Song> getSongs() {
        return this.songs;
    }

    @Override
    public Library getLibrary() {
        return this.library;
    }

    @Override
    public Future<Boolean> addSong(Song song) throws SecurityException {
        this.ids.add(song.getId());
        this.songs.setPredicate(this::filter);
        if (this.listener != null) this.listener.onSongAdded(song);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Future<Boolean> removeSong(Song song) throws SecurityException {
        this.ids.remove(song.getId());
        this.songs.setPredicate(this::filter);
        if (this.listener != null) this.listener.onSongRemoved(song);
        return CompletableFuture.completedFuture(true);
    }

    public interface PlaylistChangeListener {
        void onSongAdded(Song added);

        void onSongRemoved(Song removed);
    }
}
