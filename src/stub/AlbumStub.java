package stub;

import connect.Album;
import connect.Library;
import connect.Song;
import javafx.collections.ObservableList;

public class AlbumStub implements Album {
   private String title, artist;

   public AlbumStub(String title, String artist) {
      this.title = title;
      this.artist = artist;
   }

   /**
    * Returns the title of the Album as a String.
    *
    * @return album title
    */
   @Override
   public String getTitle() {
      return title;
   }

   /**
    * Returns the name of the artist who wrote the Album.
    *
    * @return album artist's name
    */
   @Override
   public String getArtist() {
      return artist;
   }

   /**
    * Returns a {@link ObservableList} containing all of the {@link Song}s that are part of the Album.
    *
    * @return list of songs
    */
   @Override
   public ObservableList<? extends Song> getSongs() {
      return null;
   }

   /**
    * Returns a reference to the {@link Library} that this Album is part of.
    *
    * @return library containing album
    */
   @Override
   public Library getLibrary() {
      return null;
   }
}
