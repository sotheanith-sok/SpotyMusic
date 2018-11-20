package ui.controller;

import connect.Playlist;
import connect.Song;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

public class SongsContextMenu extends ContextMenu {

   private ObservableList<Playlist> playlists;
   private Menu adder;
   private MenuItem remover;

   private Song song;
   public SongsContextMenu(Song song, ObservableList<Playlist> playlists){
      super();
      this.playlists=FXCollections.observableArrayList();
      adder=new Menu();
      adder.setText("Add to...");
      remover=new MenuItem("Delete");
      this.getItems().addAll(adder,remover);
      remover.setOnAction(event -> {
         removeFromPlaylist((Playlist) ((MenuItem) event.getSource()).getUserData());
      });
      this.song = song;
      this.playlists=playlists;
      updateAdder();
      playlists.addListener((ListChangeListener<Playlist>) c -> updateAdder());
   }


   public void addToPlaylist(Playlist playlist){
      playlist.addSong(song);
   }

   public void removeFromPlaylist(Playlist playlist){
      playlist.removeSong(song);
   }

   private void updateAdder(){
      adder.getItems().clear();
      for(Playlist playlist : playlists){
         MenuItem item = new MenuItem();
         item.setText(playlist.getName());
         item.setUserData(playlist);
         adder.getItems().add(item);
         item.setOnAction(event -> {
            addToPlaylist((Playlist) ((MenuItem) event.getSource()).getUserData());
         });
      }
   }
}
