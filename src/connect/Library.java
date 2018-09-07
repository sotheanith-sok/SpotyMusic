package connect;

import javafx.collections.ObservableList;

import java.io.File;

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
    ObservableList<Album> getAlbums();

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
    ObservableList<Album> getAlbumsByArtist(String artist);

    /**
     * Returns a {@link ObservableList} containing all of the {@link Song}s written by the named artist.
     *
     * @param artist name of an artist
     * @return list of songs by the named artist
     */
    ObservableList<Song> getSongsByArtist(String artist);

    /**
     * Returns a {@link ObservableList} containing all of the {@link Song}s in the library.
     *
     * @return list of all songs
     */
    ObservableList<Song> getSongs();

    /**
     * Returns a {@link ObservableList} containing all of the {@link Playlist}s in the library.
     *
     * @return list of playlists
     */
    ObservableList<Playlist> getPlaylists();

    /**
     * Adds a song to the library.
     *
     * @param song a File representing the song to add
     * @throws SecurityException if the current user is not authorized to modify the library
     */
    void importSong(File song) throws SecurityException;

    /**
     * Removes a song from the library.
     *
     * @param song the Song to remove from the library
     * @throws SecurityException if the current suer is not authorized to modify the library
     */
    void deleteSong(Song song) throws SecurityException;
}
