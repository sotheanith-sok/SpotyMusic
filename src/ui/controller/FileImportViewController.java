package ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

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
      stage=new Stage();
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
   }

   private void cancel(){
      stage.close();
   }


}
