package ui.controller;

import connect.Library;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import net.advert.LibraryAdvertisement;

import java.net.URL;
import java.util.ResourceBundle;

/**
 *
 */
public class LeftViewController implements Initializable {

   @FXML
   Label user;
   private MainViewController parentViewController;
   @FXML
   private ListView<LibraryAdvertisement> listView;
   private ObservableList<LibraryAdvertisement> libraryObservableList;

   /**
    * Called to initialize a controller after its root element has been
    * completely processed.
    *
    * @param location  The location used to resolve relative paths for the root object, or
    *                  <tt>null</tt> if the location is not known.
    * @param resources The resources used to localize the root object, or <tt>null</tt> if
    */
   @Override
   public void initialize(URL location, ResourceBundle resources) {
      libraryObservableList = FXCollections.observableArrayList();
      listView.setOnMouseClicked(event -> {
         if (event.getClickCount() == 2 &&  listView.getSelectionModel().getSelectedItem()!=null) {
            mouseClicked(listView.getSelectionModel().getSelectedItem());
         }
      });

      listView.setCellFactory(lv -> new ListCell<LibraryAdvertisement>() {
         @Override
         public void updateItem(LibraryAdvertisement item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
               //sets text to null if there is no information
               setText("LibraryNewGoHere");
            } else {
               //gets Playlist name string and sets the text to it
               setText(item.getLibraryName());
            }
         }
      });
      listView.setItems(libraryObservableList);
   }

   /**
    * Called to set the reference to the MainViewController
    *
    * @param mainViewController
    */
   public void setParentViewController(MainViewController mainViewController) {
      this.parentViewController = mainViewController;
   }

   /**
    * once mouse is clicked, the highlighted playlist is selected
    */
   public void mouseClicked(LibraryAdvertisement library) {
      System.out.println(library);

   }

   @FXML //switches view to splash
   public void trySignOut() {
      System.out.println("Sign Out Successful");
      parentViewController.trySignOut();
   }

   public ObservableList<LibraryAdvertisement> getLibraryObservableList() {
      return libraryObservableList;
   }

   /**
    * Set the list of playlist for this view.
    *
    * @param libraryObservableList a new list of playlist.
    */
   public void setLibraryObservableList(ObservableList<LibraryAdvertisement> libraryObservableList) {
      this.libraryObservableList = libraryObservableList;
      listView.setItems(libraryObservableList);
   }

   public void setUserName(String tester1) {
      user.setText("Welcome " + tester1);
   }
}