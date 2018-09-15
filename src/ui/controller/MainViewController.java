package ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class MainViewController implements Initializable {
   @FXML
   private LeftViewController leftViewController;
   @FXML
   private RightViewController rightViewController;
   @FXML
   private BottomViewController bottomViewController;

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
      //Give MainViewController reference to sub controllers.
      leftViewController.setMainViewController(this);
      rightViewController.setMainViewController(this);
      bottomViewController.setMainViewController(this);
      System.out.println(this);
   }
}
