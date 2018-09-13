package stub;

import connect.Library;
import connect.Song;
import javafx.beans.property.SimpleStringProperty;

import javax.sound.sampled.AudioInputStream;
import java.util.concurrent.Future;

public class SongStub implements Song{

    private final String title;
    private final String artist;
    private final String albumTitle ;
    private final long duration;

    public SongStub(String title, String artist, String albumTitle, long duration) {
        this.title = title;
        this.artist = artist;
        this.albumTitle = albumTitle;
        this.duration = duration;
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    public String getArtist() {
        return this.artist;
    }

    @Override
    public String getAlbumTitle() {
        return this.albumTitle;
    }

    @Override
    public long getDuration() {
        return this.duration;
    }

    @Override
    public Library getLibrary() {
        return null;
    }

    @Override
    public Future<AudioInputStream> getStream() {
        return null;
    }
}
