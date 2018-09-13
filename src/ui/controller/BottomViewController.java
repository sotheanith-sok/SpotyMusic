package ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.text.*;

import java.net.URL;
import java.util.ResourceBundle;

public class BottomViewController implements Initializable {
   // song progress
   @FXML
   Slider songScrubbingSilder;

   // song title
   @FXML
   Text songTitle;

   // playback control buttons
   @FXML
   Button previousSongBtn;
   @FXML
   Button playPauseSongBtn;
   @FXML
   Button nextSongBtn;

   // volume slider
   @FXML
   Slider volumeSlider;


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

      // song scrubbing
      songScrubbingSilder.setOnMouseClicked(e -> System.out.println("Song scrubbing clicked"));

      // previous song button
      previousSongBtn.setOnMouseClicked(e -> System.out.println("Previous button clicked"));
      playPauseSongBtn.setOnMouseClicked(e -> System.out.println("Play/pause button clicked"));
      nextSongBtn.setOnMouseClicked(e -> System.out.println("Next button clicked"));

      // volume slider
      volumeSlider.setOnMouseClicked(e -> System.out.println("Volume slider clicked"));
   }
}
