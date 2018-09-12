package connect;

import javafx.collections.ObservableList;

import java.util.concurrent.Future;

/**
 * A <code>Playlist</code> is a list of {@link Song}s which are arbitrarily chosen by the user.
 *
 * @since 0.0.1
 * @author Nicholas Utz
 * @see Library
 * @see Song
 */
public interface Playlist {
    /**
     * Returns the name of the Playlist.
     *
     * @return playlist's name
     */
    String getName();

    /**
     * Returns a {@link ObservableList} of the {@link Song}s in the playlist.
     *
     * @return list of songs
     */
    ObservableList<? extends Song> getSongs();

    /**
     * Returns a reference to the {@link Library} that this Playlist is part of.
     *
     * @return library containing playlist
     */
    Library getLibrary();

    /**
     * Adds the given Song to the Playlist. Returns a {@link Future} that resolves to a boolean indicating success.
     *
     * @param song the Song to add to the playlist
     * @return Future resolving to success
     * @throws SecurityException if the current user does not have permission to modify the library
     */
    Future<Boolean> addSong(Song song) throws SecurityException;

    /**
     * Removes the given Song from the Playlist. Returns a {@link Future} that resolves to a boolean indicating success.
     *
     * @param song the song to remove from the playlist
     * @return future resolving to success
     * @throws SecurityException if the current user does not have permission to modify the library
     */
    Future<Boolean> removeSong(Song song) throws SecurityException;
}
