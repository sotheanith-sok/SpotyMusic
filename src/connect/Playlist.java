package connect;

import javafx.collections.ObservableList;

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
    ObservableList<Song> getSongs();
}
