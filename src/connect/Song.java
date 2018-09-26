package connect;

import utils.ComposedComparator;

import javax.sound.sampled.AudioInputStream;
import java.util.Comparator;
import java.util.concurrent.Future;

/**
 * The <code>Song</code> interface represents a single audio track in a {@link Library}.
 *
 * @since 0.0.1
 * @author Nicholas Utz
 */
public interface Song {
    /**
     * Returns the title of the Song.
     *
     * @return song title
     */
    String getTitle();

    /**
     * Returns the name of the artist to whom the song is attributed.
     *
     * @return Artist's name
     */
    String getArtist();

    /**
     * Returns the title of the album which this Song was released in.
     *
     * @return Album title
     */
    String getAlbumTitle();

    /**
     * Returns the duration of the Song in seconds.
     *
     * @return duration in seconds
     */
    long getDuration();

    /**
     * Returns a reference to the {@link Library} that this Song belongs to.
     *
     * @return reference to library containing song
     */
    Library getLibrary();

    /**
     * Returns an {@link Future}  which resolves to an {@link AudioInputStream} which provides the audio sample data
     * of the Song.
     *
     * @return Future resolving to AudioInputStream
     */
    Future<AudioInputStream> getStream();

    /** A {@link Comparator} that compares Songs based on their title. */
    Comparator<Song> TITLE_COMPARATOR = Comparator.comparing(Song::getTitle);

    /** A {@link Comparator} that compares Songs based on their artist. */
    Comparator<Song> ARTIST_COMPARATOR = Comparator.comparing(Song::getArtist);

    /** A {@link Comparator} that compares Songs based on their album. */
    Comparator<Song> ALBUM_COMPARATOR = Comparator.comparing(Song::getAlbumTitle);

    /**
     * A {@link Comparator} that compares Songs based on their artist, then album, then title.
     *
     * Sorting a list of Songs with this comparator results in songs from the same album being grouped
     * together, and sorted alphabetically. Albums by the same artist are also grouped and sorted alphabetically.
     */
    Comparator<Song> GROUPING_COMPARATOR = new ComposedComparator<Song>(
            ARTIST_COMPARATOR, ALBUM_COMPARATOR, TITLE_COMPARATOR
    );
}
