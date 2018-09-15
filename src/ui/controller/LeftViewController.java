package ui.controller;

import connect.Library;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import stub.LibraryStub;

import java.net.URL;
import java.util.ResourceBundle;

/**
 *
 */
public class LeftViewController implements Initializable {

    private MainViewController parentViewController;

    @FXML
    private ListView<Library> listView;

    private ObservableList<Library> libraryObservableList;

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
        libraryObservableList = FXCollections.observableArrayList(new LibraryStub(), new LibraryStub(), new LibraryStub());
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                mouseClicked(listView.getSelectionModel().getSelectedItem());
            }
        });

        listView.setCellFactory(lv -> new ListCell<Library>() {
            @Override
            public void updateItem(Library item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    //sets text to null if there is no information
                    setText(null);
                } else {
                    //gets Playlist name string and sets the text to it
                    setText(item.toString());
                }
            }
        });
        listView.setItems(libraryObservableList);
    }

    /**
     * Called to set the reference to the MainViewController
     *
     * @param mainViewController
     */
    public void setParentViewController(MainViewController mainViewController) {
        this.parentViewController = mainViewController;
    }

    /**
     * once mouse is clicked, the highlighted playlist is selected
     */
    public void mouseClicked(Library library) {
        System.out.println(library);

    }

    public ObservableList<Library> getPlaylistObservableList() {
        return libraryObservableList;
    }

    /**
     * Set the list of playlist for this view.
     *
     * @param libraryObservableList a new list of playlist.
     */
    public void setPlaylistObservableList(ObservableList<Library> libraryObservableList) {
        this.libraryObservableList = libraryObservableList;
        listView.setItems(libraryObservableList);
    }

}
