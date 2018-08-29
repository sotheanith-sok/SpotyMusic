package controller;

import javafx.animation.Animation;
import javafx.animation.Transition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

public class BottomViewController implements Initializable {
   @FXML
   Text text;

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
      String s="Title of the song should go here";
      final Animation animation=new Transition() {
         {
            setCycleDuration(Duration.millis(7000));
         }
         @Override
         protected void interpolate(double frac) {
            final int length=s.length();
            final int n=Math.round(length*(float)frac);
            text.setText(s.substring(0,n));
         }
      };
      animation.play();
   }
}
