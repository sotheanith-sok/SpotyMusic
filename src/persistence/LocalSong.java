package persistence;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import connect.Library;
import connect.Song;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * An implementation of {@link Song} used by {@link LocalLibrary}.
 */
public class LocalSong implements Song {

   private String title;
   private String artist;
   private String album;
   private long duration;
   private Library lib;
   private long id;
   private File path;

   /**
    * Creates a new LocalSong with the given title, artist, album title, duration, path, and ID number.
    *
    * @param title    the title of the song
    * @param artist   the name of the artist who wrote the song
    * @param album    the album that the song is a part of
    * @param duration the duration of the song, in microseconds
    * @param path     the path to the file containing the song
    * @param id       the ID number of the song
    */
   protected LocalSong(String title, String artist, String album, long duration, File path, long id) {
      this.title = title;
      this.artist = artist;
      this.album = album;
      this.duration = duration;
      this.path = path;
      this.id = id;
   }

   /**
    * Loads a LocalSong from a {@link JsonParser}.
    * Used internally by {@link persistence.loaders.MediaLoader}.
    *
    * @param parser a parser from which to read
    * @return a LocalSong based on information read from the parser
    * @throws IOException if there is a problem while reading
    */
   public static LocalSong loadSong(JsonParser parser) throws IOException {
      String title = null;
      String album = null;
      String artist = null;
      long duration = 0;
      String path = null;
      long id = 0;

      JsonToken token = parser.currentToken();
      while (token != JsonToken.END_OBJECT) {
         if (token == JsonToken.FIELD_NAME) {
            String fieldName;
            fieldName = parser.getText();
            if (fieldName == "title") title = parser.nextTextValue();
            else if (fieldName == "artist") artist = parser.nextTextValue();
            else if (fieldName == "album") album = parser.nextTextValue();
            else if (fieldName == "path") path = parser.nextTextValue();
            else if (fieldName == "duration") duration = parser.nextLongValue(0);
            else if (fieldName == "id") id = parser.nextIntValue(0);
         }

         token = parser.nextToken();
      }

      if (title == null || album == null || artist == null || path == null) {
         // not enough info to make LocalSong
         System.out.println("[LocalSong][loadSong] Not enough data to load song");
         return null;
      }

      System.out.print("[LocalSong][LoadSong] Loaded Song ");
      System.out.println(title);
      return new LocalSong(title, artist, album, duration, new File(path), id);
   }

   /**
    * Returns the ID number of this song.
    * ID numbers should be unique within a {@link DataManager}
    *
    * @return song ID number
    */
   public long getId() {
      return this.id;
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
      return this.album;
   }

   @Override
   public long getDuration() {
      return this.duration;
   }

   @Override
   public Library getLibrary() {
      return this.lib;
   }

   /**
    * Sets the {@link Library} that the LocalSong belongs to.
    * Used internally.
    *
    * @param lib the library that the song belongs to
    */
   protected void setLibrary(Library lib) {
      this.lib = lib;
   }

   @Override
   public Future<AudioInputStream> getStream() {
      return DataManager.getDataManager().executor.submit(this.new GetStreamTask());
   }

   /**
    * Writes the LocalSong's meta-data to the given {@link JsonGenerator}.
    * Used internally by {@link persistence.writers.MediaWriter}.
    *
    * @param gen a generator to write to
    * @throws IOException if there is a problem while writing data
    */
   public void saveSong(JsonGenerator gen) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("title", this.title);
      gen.writeStringField("artist", this.artist);
      gen.writeStringField("album", this.album);
      gen.writeStringField("path", this.path.getPath());
      gen.writeNumberField("duration", this.duration);
      gen.writeNumberField("id", this.id);
      gen.writeEndObject();
   }

   /**
    * GetStreamTask asynchronously retrieves an {@link AudioInputStream} for a {@link LocalSong}.
    */
   class GetStreamTask implements Callable<AudioInputStream> {
      @Override
      public AudioInputStream call() throws Exception {
         return AudioSystem.getAudioInputStream(path);
      }
   }
}
