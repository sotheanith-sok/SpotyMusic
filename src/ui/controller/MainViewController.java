package ui.controller;

import connect.Song;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

/**
 *
 */

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
        leftViewController.setParentViewController(this);
        rightViewController.setParentViewController(this);
        bottomViewController.setParentViewController(this);
        loadCurrentLibrary();
    }


    /**
     * Load a library from DataManager
     */
    public void loadCurrentLibrary() {
        /*try {
            rightViewController.setCurrentLibrary(DataManager.getDataManager().getCurrentLibrary().get());

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }*/
        // System.out.println(DataManager.getDataManager().getCurrentUser().getUsername());

    }

    /**
     * This function is used to access the next song that should be play. It should be called from the BottomViewController.
     *
     * @return the next song.
     */
    public Song getNextSong() {
        return rightViewController.getNextSong();
    }


    /**
     * This function is used to access the previous song that should be play. It should be called from the BottomViewController.
     *
     * @return the previous song.
     */
    public Song getPreviousSong() {
        return rightViewController.getPreviousSong();
    }

    /**
     * This function is used to access the current song that's being play. It should be called from the BottomViewControler.
     *
     * @return
     */
    public Song getCurrentSong() {
        return rightViewController.getCurrentSong();
    }

    /**
     * The request to play a specific song has been requested from RightViewController. It should pass that song to BottomViewController to be play.
     *
     * @param song that need to be play
     */
    public void playASong(Song song) {
        bottomViewController.playASong(song);
    }


}
