package ui.controller;

import connect.Album;
import connect.Song;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * shows Album list view, which displays all available albums
 */
public class AlbumListViewController implements Initializable {

    /**
     * a tableview
     */
    @FXML
    private TableView<Album> tableView;

    /**
     * will be used to add options to delete/add an album?
     */
    @FXML
    private ContextMenu options;

    /**
     * columns that are used in tableview
     */
    @FXML
    private TableColumn<Album, String> albumName, artistName;


    private RightViewController parentViewController;
    private ObservableList<Album> albumObservableList;


    /**
     * override the initialize, so tableview of albums can be generated
     *
     * @param location  location to resolve relative path
     * @param resources resources used for root object
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        albumObservableList = FXCollections.observableArrayList();
        //setting the columns
        albumName.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getTitle()));
        artistName.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getArtist()));

        //on mouse click, calls method selectPlaylist, secondary = right click
        tableView.setRowFactory(tv -> {
            TableRow<Album> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    Album clickedRow = row.getItem();
                    mouseClicked(clickedRow);
                }
            });
            return row;
        });
        tableView.setItems(albumObservableList);

    }

    /**
     * mouse click = album selected
     */
    public void mouseClicked(Album album) {

        parentViewController.showDetailView(PanelType.ALBUM, (ObservableList<Song>) album.getSongs(),null,album.getTitle());
    }

    /**
     * add album data
     *
     * @param a observable list of type Album
     */
    public void addAlbumData(ObservableList<Album> a) {
        tableView.setItems(a);
    }

    public void setParentViewController(RightViewController rightViewController) {
        parentViewController = rightViewController;
    }

    /**
     * Get the list of albums
     *
     * @return list of albums
     */
    public ObservableList<Album> getAlbumObservableList() {
        return albumObservableList;
    }

    /**
     * Set a new list of albums
     *
     * @param albumObservableList a new list of albums
     */
    public void setAlbumObservableList(ObservableList<Album> albumObservableList) {
        this.albumObservableList = albumObservableList;
        tableView.setItems(albumObservableList);
    }
}
