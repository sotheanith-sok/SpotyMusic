package ui.controller;

import connect.Song;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.text.Text;
import javafx.util.Duration;
import persistence.DataManager;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Represent media play back and its controls.
 */
public class BottomViewController implements Initializable {
    // song progress
    @FXML
    Slider songScrubbingSilder;
    // song title
    @FXML
    Text songTitle, timestamp;
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

    private Clip clip;
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
            clip = AudioSystem.getClip();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }

        // song scrubbing
        songScrubbingSilder.setOnMouseReleased(e -> {
            scrubbingSong(songScrubbingSilder.getValue());
            scrubbingSliderControl = false;
        });
        songScrubbingSilder.setOnMousePressed(e -> {
            scrubbingSliderControl = true;
        });

        // previous song button
        previousSongBtn.setOnMouseClicked(e -> playPreviousSong());
        playPauseSongBtn.setOnMouseClicked(e -> playOrPauseSong());
        nextSongBtn.setOnMouseClicked(e -> playNextSong());

        // volume slider
        volumeSlider.setOnMouseDragged(e -> adjustVolume(volumeSlider.getValue()));

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(250), ae -> updateUIElements()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
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
            try {
                if (clip.isOpen()) {
                    clip.close();
                }
                clip.open(song.getStream().get());
                clip.start();
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This function is being call when the play or pause bottom is pressed. It pauses or plays the current song.
     */
    public void playOrPauseSong() {
        if (song == null) {
            playASong(parentViewController.getCurrentSong());
        }
        if (clip.isRunning()) {
            clipTime = clip.getMicrosecondPosition();
            clip.stop();
        } else {
            clip.setMicrosecondPosition(clipTime);
            clip.start();
        }
    }

    /**
     * This function is called in fix interval to update UI elements.
     */
    private void updateUIElements() {
        if (song != null) {
            //Update Play or Pause Button
            if (clip.isRunning()) {
                playPauseSongBtn.setText("Pause");
            } else {
                playPauseSongBtn.setText("Play");
            }

            //Update Title
            songTitle.setText(song.getTitle());

            //Update timestamp

            long currentTimestamp = TimeUnit.MICROSECONDS.toSeconds(clip.getMicrosecondPosition());
            long length = TimeUnit.MICROSECONDS.toSeconds(clip.getMicrosecondLength());
            timestamp.setText(Long.toString(currentTimestamp) + "/" + Long.toString(length));


            //Update slider
            if (!scrubbingSliderControl) {
                songScrubbingSilder.setMin(0);
                songScrubbingSilder.setMax(clip.getMicrosecondLength());
                songScrubbingSilder.setValue(clip.getMicrosecondPosition());
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
        clip.setMicrosecondPosition((long) value);
    }

    /**
     * Adjust the volume
     *
     * @param value new volume
     */
    public void adjustVolume(double value) {
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            double gain = value / 100;
            float dB = (float) (Math.log(gain) / Math.log(10.0) * 20.0);
            gainControl.setValue(dB);
        }
    }

}
