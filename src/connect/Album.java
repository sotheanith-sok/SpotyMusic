package connect;

import javafx.collections.ObservableList;
import utils.ComposedComparator;

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

    /** A {@link Comparator} that compares Albums by their title. */
    Comparator<Album> TITLE_COMPARATOR = Comparator.comparing(Album::getTitle);

    /** A {@link Comparator} that compares Albums by their artist. */
    Comparator<Album> ARTIST_COMPARATOR = Comparator.comparing(Album::getArtist);

    /**
     * A {@link Comparator} that compares Albums by artist, then by title.
     *
     * Sorting a collection of Albums with this comparator results in albums being grouped by artist, and sorted
     * alphabetically.
     */
    Comparator<Album> GROUPING_COMPARATOR = new ComposedComparator<Album>(ARTIST_COMPARATOR, TITLE_COMPARATOR);
}
