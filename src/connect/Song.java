package connect;

import javax.sound.sampled.AudioInputStream;

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
     * Returns the duration of the Song in <em>microseconds</em>.
     *
     * @return duration in micros
     */
    long getDuration();

    /**
     * Returns an {@link AudioInputStream} from which the audio stream of the Song can be read.
     *
     * @return Audio stream
     */
    AudioInputStream getStream();
}
