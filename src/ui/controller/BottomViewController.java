package ui.controller;

import connect.Song;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import utils.SongStreamer;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Represent media play back and its controls.
 */
public class BottomViewController implements Initializable {
   // song progress
   @FXML
   Slider songScrubbingSlider;
   // song title
   @FXML
   Label songTitle, timestamp, progressLabel;
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
   @FXML
   private MenuItem importSong,theme;

   @FXML
   private ProgressBar progressBar;

   private SongStreamer streamer;
   private long clipTime = 0;
   private Song song;
   private MainViewController parentViewController;
   private boolean scrubbingSliderControl = false;

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
      //Create Clip
      try {
         //clip = AudioSystem.getClip();
         SourceDataLine line = AudioSystem.getSourceDataLine(null);
         this.streamer = new SongStreamer(line);
      } catch (LineUnavailableException e) {
         e.printStackTrace();
      }

      // song scrubbing
      songScrubbingSlider.setOnMouseReleased(e -> {
         scrubbingSong(songScrubbingSlider.getValue());
         scrubbingSliderControl = false;
      });
      songScrubbingSlider.setOnMousePressed(e -> {
         scrubbingSliderControl = true;
      });


      // previous song button
      previousSongBtn.setOnMouseClicked(e -> playPreviousSong());
      playPauseSongBtn.setOnMouseClicked(e -> playOrPauseSong());
      nextSongBtn.setOnMouseClicked(e -> playNextSong());

      // volume slider
       volumeSlider.setValue(100);
       adjustVolume(100);
      volumeSlider.setOnMouseDragged(e -> adjustVolume(volumeSlider.getValue()));
      volumeSlider.setOnMouseClicked(event -> adjustVolume(volumeSlider.getValue()));

      Timeline timeline = new Timeline(new KeyFrame(Duration.millis(250), ae -> updateUIElements()));
      timeline.setCycleCount(Animation.INDEFINITE);
      timeline.play();

      //Menu
      importSong.setOnAction(event -> {
         showImportSongSelector();
      });
      theme.setOnAction(event -> {
         showThemeSelector();
      });
   }

   /**
    * Called to set the reference to the MainViewController
    *
    * @param mainViewController
    */
   public void setParentViewController(MainViewController mainViewController) {
      parentViewController = mainViewController;
   }

   /**
    * Tell the clip to play a specific song.
    *
    * @param song that need to be play
    */
   public void playASong(Song song) {
      if (song != null) {
         this.song = song;
         Future<AudioInputStream> task = song.getStream();
         Runnable runnable= () -> {
            if(task.isDone()){
               this.streamer.play(task);
            }else{
               progressBar.setVisible(true);
               progressBar.setVisible(true);
               this.streamer.play(task);
            }
            progressBar.setVisible(false);
            progressBar.setVisible(false);
         };
         new Thread(runnable).start();
      }
   }

   /**
    * This function is being call when the play or pause bottom is pressed. It pauses or plays the current song.
    */
   public void playOrPauseSong() {
      if (song == null) {
         playASong(parentViewController.getCurrentSong());
      }
      if (this.streamer.isPlaying()) {
         this.streamer.stop();

      } else {
         this.streamer.resume();
      }
   }

   /**
    * This function is called in fix interval to update UI elements.
    */
   private void updateUIElements() {
      if (song != null) {
         //Update Play or Pause Button
         if (this.streamer.isPlaying()) {
            playPauseSongBtn.setText("Pause");
         } else {
            playPauseSongBtn.setText("Play");
         }

         //Update Title
         songTitle.setText(song.getTitle());

         //Update timestamp

         long currentTimestamp = TimeUnit.MICROSECONDS.toSeconds(streamer.getMicrosecondPosition());
         //long length = TimeUnit.MICROSECONDS.toSeconds(streamer.getMicrosecondLength());
         //timestamp.setText(Long.toString(currentTimestamp) + "/" + Long.toString(length));
         timestamp.setText(Long.toString(currentTimestamp));

         //Update slider
         if (!scrubbingSliderControl) {
            songScrubbingSlider.setMin(0);
            //songScrubbingSlider.setMax(clip.getMicrosecondLength());
            songScrubbingSlider.setMax(10000);
            songScrubbingSlider.setValue(streamer.getMicrosecondPosition());
         }
      }
      if(progressBar.isVisible() && progressLabel.isVisible()){
         if (progressBar.getProgress() < 1d) {
            double progress=progressBar.getProgress()+100d/5000d;
            progressBar.setProgress(progress);
            progressLabel.setText(String.format("Importing: %.2f%%",progress));
         }

         if(progressBar.getProgress()>=1){
            progressBar.setProgress(0);
            progressBar.setVisible(false);
            progressLabel.setVisible(false);
         }
      }
   }

   /**
    * Play the next song.
    */
   public void playNextSong() {
      playASong(parentViewController.getNextSong());
   }

   /**
    * Play the previous song
    */
   public void playPreviousSong() {
      playASong(parentViewController.getPreviousSong());
   }

   /**
    * Skip a song to a specific place
    *
    * @param value microseconds position that should be skip to.
    */
   public void scrubbingSong(double value) {
      //clip.setMicrosecondPosition((long) value);
   }

   /**
    * Adjust the volume
    *
    * @param value new volume
    */
   public void adjustVolume(double value) {/*
      if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
         FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
         double gain = value / 100;
         float dB = (float) (Math.log(gain) / Math.log(10.0) * 20.0);
         gainControl.setValue(dB);
      }*/
   }
   private void showImportSongSelector(){
      try {
         FXMLLoader fxmlLoader = new FXMLLoader();
         fxmlLoader.setLocation(getClass().getResource("/ui/view/FileImportView.fxml"));
         Scene scene = new Scene(fxmlLoader.load(), 600, 400);
         Stage stage = new Stage();
          ((FileImportViewController)fxmlLoader.getController()).setStage(stage);
          ((FileImportViewController)fxmlLoader.getController()).setParentController(parentViewController);
          ((FileImportViewController)fxmlLoader.getController()).updateStyleSheet();
         stage.setTitle("Import song");
         stage.setScene(scene);
          stage.initModality(Modality.APPLICATION_MODAL);
         stage.show();
      }
      catch (IOException e) {
         e.printStackTrace();
      }
   }

   private void showThemeSelector(){
       try {
           FXMLLoader fxmlLoader = new FXMLLoader();
           fxmlLoader.setLocation(getClass().getResource("/ui/view/ThemeSelectorView.fxml"));
           Scene scene = new Scene(fxmlLoader.load(), 600, 400);
           Stage stage = new Stage();
           ((ThemeSelectorViewController)fxmlLoader.getController()).setStage(stage);
           ((ThemeSelectorViewController)fxmlLoader.getController()).setParentController(parentViewController);
           ((ThemeSelectorViewController)fxmlLoader.getController()).updateStyleSheet();
           stage.setTitle("Import song");
           stage.setScene(scene);
           stage.initModality(Modality.APPLICATION_MODAL);
           stage.show();
       }
       catch (IOException e) {
           e.printStackTrace();
       }
   }

}
