package ui.controller;

import javafx.animation.Animation;
import javafx.animation.Transition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.text.*;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

public class BottomViewController implements Initializable {
   private MainViewController mainViewController;



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

   }

   /**
    * Called to set the reference to the MainViewController
    * @param mainViewController
    */
   public void setMainViewController(MainViewController mainViewController){
      this.mainViewController=mainViewController;
      System.out.println(mainViewController);
   }
}
