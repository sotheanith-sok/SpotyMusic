package stub;

import connect.Library;
import connect.Playlist;
import connect.Song;
import javafx.collections.ObservableList;

import java.util.concurrent.Future;

public class PlaylistStub implements Playlist {

   private String name;

   public PlaylistStub(String name) {
      this.name = name;
   }

   /**
    * Returns the name of the Playlist.
    *
    * @return playlist's name
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Returns a {@link ObservableList} of the {@link Song}s in the playlist.
    *
    * @return list of songs
    */
   @Override
   public ObservableList<? extends Song> getSongs() {
      return null;
   }

   /**
    * Returns a reference to the {@link Library} that this Playlist is part of.
    *
    * @return library containing playlist
    */
   @Override
   public Library getLibrary() {
      return null;
   }

   /**
    * Adds the given Song to the Playlist. Returns a {@link Future} that resolves to a boolean indicating success.
    *
    * @param song the Song to add to the playlist
    * @return Future resolving to success
    * @throws SecurityException if the current user does not have permission to modify the library
    */
   @Override
   public Future<Boolean> addSong(Song song) throws SecurityException {
      return null;
   }

   /**
    * Removes the given Song from the Playlist. Returns a {@link Future} that resolves to a boolean indicating success.
    *
    * @param song the song to remove from the playlist
    * @return future resolving to success
    * @throws SecurityException if the current user does not have permission to modify the library
    */
   @Override
   public Future<Boolean> removeSong(Song song) throws SecurityException {
      return null;
   }
}
