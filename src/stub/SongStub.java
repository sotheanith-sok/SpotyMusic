package stub;

import connect.Library;
import connect.Song;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Future;

public class SongStub implements Song {

   private final String title;
   private final String artist;
   private final String albumTitle;
   private final long duration;
   private AudioInputStream audioInputStream;

   public SongStub(String title, String artist, String albumTitle, long duration, String path) {
      this.title = title;
      this.artist = artist;
      this.albumTitle = albumTitle;
      this.duration = duration;
      URL url = this.getClass().getClassLoader().getResource(path);
      try {
         audioInputStream = AudioSystem.getAudioInputStream(url);
      } catch (UnsupportedAudioFileException e) {
         e.printStackTrace();
      } catch (IOException e) {
         e.printStackTrace();
      }
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
    public String getReadableDuration() {
        int seconds = (int)(this.duration/1000000);
        return Integer.toString(seconds/60) + ":" + Integer.toString(seconds % 60);
    }

    @Override
    public Library getLibrary() {
        return null;
 

   @Override
   public Future<AudioInputStream> getStream() {
      return null;
   }

   public AudioInputStream getAudioInputStream() {
      return audioInputStream;
   }
}
