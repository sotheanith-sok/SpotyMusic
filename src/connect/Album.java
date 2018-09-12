package connect;

import javafx.collections.ObservableList;

import java.util.Comparator;

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
     * Returns a {@link ObservableList} containing all of the {@link Song}s that are part of the Album.
     *
     * @return list of songs
     */
    ObservableList<? extends Song> getSongs();

    // TODO: album artwork?

    /**
     * Returns a reference to the {@link Library} that this Album is part of.
     *
     * @return library containing album
     */
    Library getLibrary();

    /**
     * A {@link Comparator} that compares {@link Album}s by title, then by artist.
     */
    class AlbumComparator implements Comparator<Album> {
        @Override
        public int compare(Album o1, Album o2) {
            int c = o1.getTitle().compareTo(o2.getTitle());
            if (c == 0) return o1.getArtist().compareTo(o2.getArtist());
            return c;
        }
    }
}
