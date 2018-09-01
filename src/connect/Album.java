package connect;

import java.util.List;

/**
 * The <code>Album</code> interface represents a collection of {@link Song}s that are released as a group.
 *
 * @since 0.0.1
 * @author Nicholas Utz
 * @see Song
 * @see Library
 */
public interface Album {
    /**
     * Returns the title of the Album as a String.
     *
     * @return album title
     */
    String getTitle();

    /**
     * Returns the name of the artist who wrote the Album.
     *
     * @return album artist's name
     */
    String getArtist();

    /**
     * Returns a {@link List} containing all of the {@link Song}s that are part of the Album.
     *
     * @return list of songs
     */
    List<Song> getSongs();

    // TODO: album artwork?
}
