package ui.controller;

import connect.Playlist;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import connect.Album;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.Initializable;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ContextMenu;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
    private TableView albumTableView;

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


    /**
     * override the initialize, so tableview of albums can be generated
     * @param location location to resolve relative path
     * @param resources resources used for root object
     */
    @Override
    public void initialize(URL location, ResourceBundle resources){

        ObservableList<String> data = FXCollections.observableArrayList("Blah", "i think this works");
        albumTableView.setItems(data);

        //setting the columns
        albumName.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getTitle()));
        artistName.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getArtist()));

        //on mouse click, calls method selectPlaylist, secondary = right click
        albumTableView.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if(event.getButton() == MouseButton.SECONDARY){
                        options.show(albumTableView, event.getScreenX(), event.getScreenY());

                }
                else {
                    selectAlbum();
                }
            }
        });

    }

    /**
     * mouse click = album selected
     */
    public void selectAlbum(){
        //select album based on mouse click, which will show songs within that album


        System.out.println("Selected Item from Album Table View");
    }

    /**
     * add album data
     * @param a observable list of type Album
     */
    public void addAlbumData(ObservableList<Album> a){
        albumTableView.setItems(a);
    }

}
