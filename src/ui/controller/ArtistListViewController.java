package ui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * shows Artist list view, which shows all artists
 */
public class ArtistListViewController implements Initializable {

   /**
    * a listview
    */
   @FXML
   private ListView<String> listView;

   private RightViewController parentViewController;

   private ObservableList<String> artistObservableList;


   /**
    * override the initialize, so listview of artists can be generated
    *
    * @param location  location to resolve relative path
    * @param resources resources used for root object
    */
   @Override
   public void initialize(URL location, ResourceBundle resources) {
      artistObservableList = FXCollections.observableArrayList();

      //on mouse click, calls method selectArtist
      listView.setOnMouseClicked(event -> {
         if (event.getClickCount() == 2 && listView.getSelectionModel().getSelectedItem()!=null)
            mouseClicked(listView.getSelectionModel().getSelectedItem());
      });
      listView.setItems(artistObservableList);
   }

   /**
    * when mouse is clicked, highlighted artist is selected
    */
   public void mouseClicked(String artist) {
      parentViewController.showDetailView(PanelType.ARTIST, artist, null, artist);
   }


   public void setParentViewController(RightViewController rightViewController) {
      parentViewController = rightViewController;
   }

   /**
    * Get list of artists.
    *
    * @return list of artists
    */
   public ObservableList<String> getArtistObservableList() {
      return artistObservableList;
   }

   /**
    * Set list of artists
    *
    * @param artistObservableList a new artist list
    */
   public void setArtistObservableList(ObservableList<String> artistObservableList) {
      this.artistObservableList = artistObservableList;
      listView.setItems(artistObservableList);
   }
}

