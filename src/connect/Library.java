package connect;

import javafx.collections.ObservableList;

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
     * Adds a song to the library.
     *
     * @param song a File representing the song to add
     * @throws SecurityException if the current user is not authorized to modify the library
     * @return Future that resolves to success
     */
    Future<Boolean> importSong(File song) throws SecurityException;

    /**
     * Removes a song from the library.
     *
     * @param song the Song to remove from the library
     * @throws SecurityException if the current uer is not authorized to modify the library
     * @return Future that resolves to success
     */
    Future<Boolean> deleteSong(Song song) throws SecurityException;

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
        return ((ObservableList<Song>) this.getSongs()).filtered((Song s) -> (s.getTitle().contains(searchParam) | s.getAlbumTitle().contains(searchParam) | s.getArtist().contains(searchParam)));
    }
}
