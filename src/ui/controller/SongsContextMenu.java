package ui.controller;

import connect.Playlist;
import connect.Song;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

public class SongsContextMenu extends ContextMenu {

   private ObservableList<Playlist> playlists;
   private Menu adder;
   private Menu remover;

   private Song song;
   public SongsContextMenu(Song song, ObservableList<Playlist> playlists){
      super();
      this.playlists=FXCollections.observableArrayList();
      adder=new Menu("Add to...");
      remover=new Menu("Delete from...");
      this.getItems().addAll(adder,remover);
      this.song = song;
      this.playlists=playlists;
      this.setOnShowing(event -> {
       update();
      });
   }


   public void addToPlaylist(Playlist playlist){
      playlist.addSong(song);
   }

   public void removeFromPlaylist(Playlist playlist){
      playlist.removeSong(song);
   }

   private void update(){
      adder.getItems().clear();
      remover.getItems().clear();
      for(Playlist playlist : playlists){
         //Create menu for adder
         MenuItem item0 = new MenuItem(playlist.getName());
         item0.setUserData(playlist);
         adder.getItems().add(item0);
         item0.setOnAction(event -> {
            addToPlaylist((Playlist) ((MenuItem) event.getSource()).getUserData());
         });

         //Create menu for remover
         if (playlist.getSongs().contains(song)){
            MenuItem item1 = new MenuItem(playlist.getName());
            item1.setUserData(playlist);
            remover.getItems().add(item1);
            item1.setOnAction(event -> {
               removeFromPlaylist((Playlist) ((MenuItem) event.getSource()).getUserData());
            });
         }
      }
      if(adder.getItems().size()==0){
         adder.setDisable(true);
      }else{
         adder.setDisable(false);
      }
      if(remover.getItems().size()==0){
         remover.setDisable(true);
      }else{
         remover.setDisable(false);
      }
   }
}
