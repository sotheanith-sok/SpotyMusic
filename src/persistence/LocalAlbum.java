package persistence;

import connect.Album;
import connect.Library;
import connect.Song;
import javafx.collections.ObservableList;

/**
 * The LocalAlbum class is an implementation of {@link Album} that represents an album in the local user's library.
 */
public class LocalAlbum implements Album {

   private String title;
   private String artist;
   private ObservableList<LocalSong> songs;
   private LocalLibrary lib;

   /**
    * Creates a new LocalAlbum with the given title and artist. The album contains songs from the given
    * {@link LocalLibrary}.
    *
    * @param title  the title of the new album
    * @param artist the artist who published the album
    * @param lib    the library from which the album comes
    */
   protected LocalAlbum(String title, String artist, LocalLibrary lib) {
      this.title = title;
      this.artist = artist;
      this.songs = lib.songs.filtered(s -> s.getAlbumTitle().equals(this.title) && s.getArtist().equals(this.artist));
      this.lib = lib;
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
   public ObservableList<? extends Song> getSongs() {
      return this.songs;
   }

   @Override
   public Library getLibrary() {
      return this.lib;
   }
}
