package ui.controller;

import connect.Playlist;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.Initializable;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import java.net.URL;
import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import persistence.DataManager;
import java.util.ResourceBundle;

/**
 * shows the Playlist View, which displays all playlists
 */
public class PlaylistListViewController implements Initializable {

    /**
     * listview of type Playlist
     */
    @FXML
     ListView<Playlist> playlistList;

    /**
     * override the initialize, so listview of playlists can be generated
     * @param location location to resolve relative path
     * @param resources resources used for root object
     */
    @Override
    public void initialize(URL location, ResourceBundle resources){


        /**
         * on mouse click, calls method selectPlaylist
         */
        playlistList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                selectPlaylist();
            }
        });

        /**
         * a list of all the playlists are generated onto the view
         */
        playlistList.setCellFactory(lv->new ListCell<Playlist>(){
            @Override
            public void updateItem(Playlist item, boolean empty){
                super.updateItem(item, empty);
                if(empty){
                    //sets text to null if there is no information
                    setText(null);
                }else{
                    //gets Playlist name string and sets the text to it
                    setText(item.getName());
                }
            }
        });
    }

    /**
     * once mouse is clicked, the highlighted playlist is selected
     */
    public void selectPlaylist(){
        //select a song list based on the playlist
//        DataManager.getDataManager().getCurrentLibrary().
    }

    /**
     * add playlist data
     * @param a observable list of type Playlist
     */
    public void addPlaylistData(ObservableList<Playlist> a){
        playlistList.setItems(a);
    }

}
