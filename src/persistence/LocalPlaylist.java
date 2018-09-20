package persistence;

import com.fasterxml.jackson.core.JsonGenerator;
import connect.Library;
import connect.Playlist;
import connect.Song;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import utils.ObservableListImpl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Implementation of {@link Playlist} for use with {@link LocalLibrary} instances.
 */
public class LocalPlaylist implements Playlist, ListChangeListener<LocalSong> {

   private String name;
   private LocalLibrary lib;

   private ObservableList<LocalSong> songs;

   /**
    * Creates a new LocalPlaylist with the given name and song list, associated with the given {@link LocalLibrary}.
    *
    * @param name  the name of the playlist
    * @param songs the songs in the playlsit
    * @param lib   the library that the playlist is part of
    */
   public LocalPlaylist(String name, List<LocalSong> songs, LocalLibrary lib) {
      this.name = name;
      this.lib = lib;
      this.songs = new ObservableListImpl<>(songs);
      lib.songs.addListener(this);
   }

   @Override
   public String getName() {
      return this.name;
   }

   @Override
   public ObservableList<? extends Song> getSongs() {
      return this.songs;
   }

   @Override
   public Library getLibrary() {
      return this.lib;
   }

   @Override
   public Future<Boolean> addSong(Song song) throws SecurityException {
      if (song instanceof LocalSong) {
         this.songs.add((LocalSong) song);
         return DataManager.getDataManager().saveLibrary();

      } else {
         // can't add song to playlist
         CompletableFuture<Boolean> future = new CompletableFuture<>();
         future.complete(false);
         return future;
      }
   }

   @Override
   public Future<Boolean> removeSong(Song song) throws SecurityException {
      if (song instanceof LocalSong) {
         if (this.songs.contains(song)) {
            this.songs.remove((LocalSong) song);
            return DataManager.getDataManager().saveLibrary();
         }

      }
      // can't song isn't in playlist
      CompletableFuture<Boolean> future = new CompletableFuture<>();
      future.complete(true);
      return future;
   }

   @Override
   public void onChanged(Change<? extends LocalSong> c) {
      if (c.wasRemoved()) {
         this.songs.removeAll(c.getRemoved());
         // we can call saveLibrary even if whatever removed a song already did, because saveLibrary is debounced
         DataManager.getDataManager().saveLibrary();
      }
   }

   /**
    * Writes the contents of the playlist to the given {@link JsonGenerator}.
    * Used internally by {@link LocalLibrary#saveLibrary(JsonGenerator)}.
    *
    * @param gen a JsonGenerator to use as output
    * @throws IOException if an IOException occurs while writing
    */
   public void savePlaylist(JsonGenerator gen) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("name", this.name);
      gen.writeArrayFieldStart("songs");
      for (LocalSong s : this.songs) {
         gen.writeNumber(s.getId());
      }
      gen.writeEndArray();
      gen.writeEndObject();
   }
}
