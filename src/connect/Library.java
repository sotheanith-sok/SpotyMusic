package connect;

import java.util.List;
import java.io.File;

/**
 * <code>Library</code> is the primary interface that connects the UI to the persistence layer.
 *
 * A Library instance represents a collection of music that a user has access to.
 */
public interface Library {
    /**
     * Returns a {@link List} of {@link Album}s in the Library.
     *
     * @return list of Albums
     */
    List<Album> getAlbums();

    /**
     * Returns a {@link List} of the names of the artists of all of the songs in the Library.
     *
     * @return list of artist names
     */
    List<String> getArtists();

    /**
     * Returns a {@link List} of {@link Album}s written by the artist with the given name.
     *
     * @param artist the name of an artist
     * @return list of albums by the named artist
     */
    List<Album> getAlbumsByArtist(String artist);

    /**
     * Returns a {@link List} containing all of the {@link Song}s written by the named artist.
     *
     * @param artist name of an artist
     * @return list of songs by the named artist
     */
    List<Song> getSongsByArtist(String artist);

    /**
     * Returns a {@link List} containing all of the {@link Song}s in the library.
     *
     * @return list of all songs
     */
    List<Song> getSongs();

    /**
     * Returns a {@link List} containing all of the {@link Playlist}s in the library.
     *
     * @return list of playlists
     */
    List<Playlist> getPlaylists();

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
