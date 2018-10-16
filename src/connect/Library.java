package connect;

import javafx.collections.ObservableList;
import persistence.FileImportTask;

import java.io.File;
import java.util.concurrent.Future;

/**
 * <code>Library</code> is the primary interface that connects the UI to the persistence layer.
 *
 * A Library instance represents a collection of music that a user has access to.
 */
public interface Library {
    /**
     * Returns a {@link ObservableList} of {@link Album}s in the Library.
     *
     * @return list of Albums
     */
    ObservableList<? extends Album> getAlbums();

    /**
     * Returns a {@link ObservableList} of the names of the artists of all of the songs in the Library.
     *
     * @return list of artist names
     */
    ObservableList<String> getArtists();

    /**
     * Returns a {@link ObservableList} of {@link Album}s written by the artist with the given name.
     *
     * @param artist the name of an artist
     * @return list of albums by the named artist
     */
    ObservableList<? extends Album> getAlbumsByArtist(String artist);

    /**
     * Returns a {@link ObservableList} containing all of the {@link Song}s written by the named artist.
     *
     * @param artist name of an artist
     * @return list of songs by the named artist
     */
    ObservableList<? extends Song> getSongsByArtist(String artist);

    /**
     * Returns a {@link ObservableList} containing all of the {@link Song}s in the library.
     *
     * @return list of all songs
     */
    ObservableList<? extends Song> getSongs();

    /**
     * Returns a {@link ObservableList} containing all of the {@link Playlist}s in the library.
     *
     * @return list of playlists
     */
    ObservableList<? extends Playlist> getPlaylists();

    /**
     * Creates a new Playlist with the given name.
     *
     * @param name the name of the playlist to create
     * @return a Future that resolves to a boolean indicating success
     * @throws SecurityException if the current user is not authorized to modify the library
     */
    Future<Boolean> createPlaylist(String name) throws SecurityException;

    /**
     * Returns an {@link ObservableList} of {@link Song}s in this Library that meet the given search
     * parameter. Sorting of search results is not required.
     *
     * @param searchParam a string to search by
     * @return the results of the search
     */
    default ObservableList<? extends Song> search(String searchParam) {
        final String lowerSearchParam = searchParam.toLowerCase();
        return ((ObservableList<Song>) this.getSongs()).filtered((Song s) -> (s.getTitle().toLowerCase().contains(lowerSearchParam)
                | s.getAlbumTitle().toLowerCase().contains(lowerSearchParam) | s.getArtist().toLowerCase().contains(lowerSearchParam)));
    }
}
