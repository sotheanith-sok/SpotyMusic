package ui.controller;

import connect.Song;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import stub.SongStub;

import java.net.URL;
import java.util.ResourceBundle;


public class QueueViewController implements Initializable {
    @FXML private TableView<Song> tableView;

    @FXML private TableColumn<Song, String> titleCol;
    @FXML private TableColumn<Song, String> artistCol;
    @FXML private TableColumn<Song, String> albumCol;
    @FXML private TableColumn<Song, Long> lengthCol;


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // use a 'stub' implementation - just a test class

        // this will eventually be populated with songs from the library
        ObservableList<Song> queueData = FXCollections.observableArrayList(
                new SongStub("The Genesis", "Nas", "Illmatic", 94),
                new SongStub("N.Y. State of Mind", "Nas", "Illmatic", 102),
                new SongStub("Life's a Beach", "Nas", "Illmatic", 144),
                new SongStub("Baby", "Justin Beiber", "Baby, Oh", 68),
                new SongStub("Gangnam Style", "Psy", "Psy Hit Singles", 131),
                new SongStub("Don't Stop Believing", "John Coltrane", "Don't Stop", 203)
        );

        // let table cell know how to populate itself
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        artistCol.setCellValueFactory(new PropertyValueFactory<>("artist"));
        albumCol.setCellValueFactory(new PropertyValueFactory<>("albumTitle"));
        lengthCol.setCellValueFactory(new PropertyValueFactory<>("duration"));

        tableView.setItems(queueData);
        tableView.getColumns().setAll(titleCol, artistCol, albumCol, lengthCol);
    }

    /*
    * Attributes for queue view song
    * title
    * artist
    * album
    * length
    * */

    /**
     * observable list of songs
     * cell factory tells table view how to render the list element
     */
}
