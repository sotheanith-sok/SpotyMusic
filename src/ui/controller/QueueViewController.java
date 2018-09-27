package ui.controller;

import connect.Song;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;

import java.net.URL;
import java.util.ResourceBundle;


/**
 *
 */
public class QueueViewController implements Initializable {
    /**
     * Keep track of which song from the list is being play.
     */
    int indexOfCurrentSong = 0;
    @FXML
    private TableView<Song> tableView;
    @FXML
    private TableColumn<Song, String> titleCol;
    @FXML
    private TableColumn<Song, String> artistCol;
    @FXML
    private TableColumn<Song, String> albumCol;
    @FXML
    private TableColumn<Song, String> lengthCol;
    private RightViewController parentViewController;
    private ObservableList<Song> songObservableList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        //Create list
        songObservableList = FXCollections.observableArrayList();

        //Bind properties of song to column
        titleCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getTitle()));
        artistCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getArtist()));
        albumCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getAlbumTitle()));
        lengthCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getReadableDuration()));
        tableView.setItems(songObservableList);

        //Add mouse click listener
        tableView.setRowFactory(tv -> {
            TableRow<Song> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    Song clickedRow = row.getItem();
                    mouseClicked(clickedRow);
                }
            });
            return row;
        });


    }

    /**
     * Set a reference to the parent view controller of this controller.
     *
     * @param rightViewController a parent view controller.
     */

    public void setParentViewController(RightViewController rightViewController) {
        parentViewController = rightViewController;
    }


    /**
     * This function will be call when a mouse click happen on a row of song.
     *
     * @param song song that was clicked on.
     */
    public void mouseClicked(Song song) {
        indexOfCurrentSong = songObservableList.indexOf(song);
        parentViewController.playASong(song);
    }

    /**
     * Get the list of songs in this queue.
     *
     * @return the list of songs.
     */
    public ObservableList<Song> getSongObservableList() {
        return songObservableList;
    }

    /**
     * Set list of songs in this queue
     *
     * @param songObservableList a new list of songs.
     */
    public void setSongObservableList(ObservableList<Song> songObservableList) {
        this.songObservableList = songObservableList;
        tableView.setItems(songObservableList);
    }

    /**
     * Access the index of song currently playing inside the list.
     *
     * @return index of currently playing song.
     */
    public int getIndexOfCurrentSong() {
        return indexOfCurrentSong;
    }

    /**
     * Mutate the index of song currently playing inside the list. It also highlight the new song that should be playing.
     *
     * @param indexOfCurrentSong a new index of song.
     */
    public void setIndexOfCurrentSong(int indexOfCurrentSong) {
        if (indexOfCurrentSong < 0) {
            this.indexOfCurrentSong = songObservableList.size() - 1;
        } else if (indexOfCurrentSong >= songObservableList.size()) {
            this.indexOfCurrentSong = 0;
        } else {
            this.indexOfCurrentSong = indexOfCurrentSong;
        }
        tableView.getSelectionModel().select(this.indexOfCurrentSong);
    }

}
