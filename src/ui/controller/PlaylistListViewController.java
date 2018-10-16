package ui.controller;

import connect.Playlist;
import connect.Song;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import persistence.DataManager;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;

/**
 * shows the Playlist View, which displays all playlists
 */
public class PlaylistListViewController implements Initializable {

   /**
    * listview of type Playlist
    */
   @FXML
   private ListView<Playlist> listView;

   private RightViewController parentViewController;

   private ObservableList<Playlist> playlistObservableList;

   /**
    * override the initialize, so listview of playlists can be generated
    *
    * @param location  location to resolve relative path
    * @param resources resources used for root object
    */
   @Override
   public void initialize(URL location, ResourceBundle resources) {
      playlistObservableList = FXCollections.observableArrayList();

      /**
       * on mouse click, calls method selectPlaylist
       */
      listView.setOnMouseClicked(event -> {
         if (event.getClickCount() == 2) {
            mouseClicked(listView.getSelectionModel().getSelectedItem());
         }
      });

      /**
       * a list of all the playlists are generated onto the view
       */
      listView.setCellFactory(lv -> new ListCell<Playlist>() {
         @Override
          public void updateItem(Playlist item, boolean empty) {
              super.updateItem(item, empty);
              if (empty) {
                  //sets text to null if there is no information
                  setText(null);
              } else {
                  //gets Playlist name string and sets the text to it
                  setText(item.getName());
              }
          }
      });

      listView.setItems(playlistObservableList);

   }

   /**
    * once mouse is clicked, the highlighted playlist is selected
    */
   public void mouseClicked(Playlist playlist) {

      parentViewController.showDetailView(PanelType.PLAYLIST, (ObservableList<Song>) playlist.getSongs(), null, playlist.getName());
   }

   /**
    * Set reference to the parent view controller of this object.
    *
    * @param rightViewController parent view controller.
    */

   public void setParentViewController(RightViewController rightViewController) {
      parentViewController = rightViewController;
   }

   /**
    * Get the list of playlist in this view.
    *
    * @return the current list of playlist.
    */
   public ObservableList<Playlist> getPlaylistObservableList() {
      return playlistObservableList;
   }

   /**
    * Set the list of playlist for this view.
    *
    * @param playlistObservableList a new list of playlist.
    */
   public void setPlaylistObservableList(ObservableList<Playlist> playlistObservableList) {
      this.playlistObservableList = playlistObservableList;
      listView.setItems(playlistObservableList);
   }

   public void createPlaylist() {
      TextInputDialog dialog = new TextInputDialog();
      dialog.setTitle("Create playlist");
      dialog.setHeaderText("Please enter a new playlist name");
      dialog.setContentText("Name:");
      Optional<String> result = dialog.showAndWait();
      result.ifPresent(name -> {
          parentViewController.getCurrentLibrary().createPlaylist(name);
      });
   }

   public void deletePlaylist() {
      playlistObservableList.remove(listView.getSelectionModel().getSelectedItem());
   }
}
