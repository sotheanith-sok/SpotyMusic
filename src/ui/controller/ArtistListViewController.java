package ui.controller;

import connect.Playlist;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.Initializable;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.ListView;
import java.net.URL;
import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import persistence.DataManager;
import java.util.ResourceBundle;

/**
 * shows Artist list view, which shows all artists
 */
public class ArtistListViewController implements Initializable {

    /**
     * a listview
     */
    @FXML
    private ListView artistView;


    /**
     * override the initialize, so listview of artists can be generated
     * @param location location to resolve relative path
     * @param resources resources used for root object
     */
    @Override
    public void initialize(URL location, ResourceBundle resources){

        //on mouse click, calls method selectArtist
        artistView.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                selectArtist();
            }
        });

    }

    /**
     * when mouse is clicked, highlighted artist is selected
     */
    public void selectArtist(){
        //select artist based on mouse click, this will show songs made by that artist

        System.out.println("Selected Item from Artist Table View");
    }

    /**
     * adds artist data
     * @param a Observable list of type String
     */
    public void addArtistData(ObservableList<String> a){
        artistView.setItems(a);
    }

}

