package ui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import mesh.MeshSystem;
import persistence.DataManager;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class FileImportViewController implements Initializable {

   @FXML
   private Button browseButton,importButton,cancelButton;
   @FXML
   private ListView<String> recentlyImportedSongListView;
   @FXML
   private TextField filePath,title,album,artist;
   private Stage stage;
   private File file;
   private MainViewController parentViewController;

   @FXML
   private Pane container;
   private ObservableList<String > songObservableList;

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
        songObservableList=FXCollections.observableArrayList();
       recentlyImportedSongListView.setCellFactory(lv -> new ListCell<String>() {
           @Override
           public void updateItem(String item, boolean empty) {
               super.updateItem(item, empty);
               if (empty) {
                   //sets text to null if there is no information
                   setText(null);
               } else {
                   //gets Playlist name string and sets the text to it
                   setText(item);
               }
           }
       });



       filePath.setEditable(false);
      browseButton.setOnMouseClicked(event -> {
         openFileChooser();
      });
      importButton.setOnMouseClicked(event -> {
         importSong();
      });
      cancelButton.setOnMouseClicked(event -> {
         cancel();
      });

   }

   public void setStage(Stage stage){
       this.stage=stage;
   }
   public void setParentController(MainViewController controller){
    this.parentViewController =controller;
   }
   private void openFileChooser(){
      FileChooser fileChooser =new FileChooser();
      fileChooser.setTitle("Choose song");
      fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("WAV","*.wav"));
      file =fileChooser.showOpenDialog(stage);
      if(file!=null){
         filePath.setText(file.getPath());
         title.setText(file.getName().replaceFirst("[.][^.]+$", ""));
      }

   }

   private void importSong(){
      if(filePath.getText().isEmpty()){
         filePath.setPromptText("[Information Needed]");
      }
      if(title.getText().isEmpty()){
         title.setPromptText("[Information Needed]");
      }
      if(album.getText().isEmpty()){
         album.setPromptText("[Information Needed]");
      }
      if(artist.getText().isEmpty()){
         artist.setPromptText("[Information Needed]");
      }
      if(!filePath.getText().isEmpty() && !title.getText().isEmpty() &&!album.getText().isEmpty()&&!artist.getText().isEmpty()){
          MeshSystem.getInstance().getLibrary().importSong(file, title.getText(), artist.getText(), album.getText());
          songObservableList.add(title.getText());
          stage.close();
      }
   }

   private void cancel(){
      stage.close();
   }

   public void updateStyleSheet(){
       container.getStylesheets().clear();
       container.getStylesheets().add(parentViewController.getCurrentTheme());
   }

}
