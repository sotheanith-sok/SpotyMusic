package controller;

import com.sun.org.apache.xml.internal.security.Init;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;

import java.net.URL;
import java.util.ResourceBundle;

public class LeftViewController implements Initializable {
   @FXML
   ListView profileList;
   @FXML
   Button button;

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
      ObservableList<String> names = FXCollections.observableArrayList(
              "Julia", "Ian", "Sue", "Matthew", "Hannah", "Stephan", "Denise");
      profileList.setItems(names);
   button.setOnMouseClicked(new EventHandler<MouseEvent>() {
      @Override
      public void handle(MouseEvent event) {
         System.out.println("MOUSE Clicked");
      }
   });
   }
}
