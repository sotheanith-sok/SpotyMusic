package ui.controller;

import connect.Song;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 *
 */
public class RightViewController implements Initializable {
    private MainViewController parentViewController;
    @FXML
    private QueueViewController queueViewController;
    @FXML
    private PlaylistListViewController playlistListViewController;
    @FXML
    private AlbumListViewController albumListViewController;
    @FXML
    private ArtistListViewController artistListViewController;

    @FXML
    private AnchorPane playlistsView, artistsView, albumsView;

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
        queueViewController.setParentViewController(this);
        playlistListViewController.setParentViewController(this);
        albumListViewController.setParentViewController(this);
        artistListViewController.setParentViewController(this);
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
     * Facade function used to show detail view. It will be call from ArtistViewController
     * @param panelType which panel is being call from
     * @param string name of the artist
     */

    public void showDetailView(PanelType panelType, String string) {
        switch (panelType) {
            case ARTIST:
                createDetailView(artistsView, panelType);
                break;
        }
    }

    /**
     * Facade function used to show detail view. It will be called from PlaylistListViewController and AlbumListViewController.
     * @param panelType which panel is being call from
     * @param songObservableList collection of songs.
     */

    public void showDetailView(PanelType panelType, ObservableList<Song> songObservableList) {
        switch (panelType) {
            case PLAYLIST:
                createDetailView(playlistsView, panelType);
                break;
            case ALBUM:
                createDetailView(albumsView, panelType);
                break;
        }
    }

    /**
     * Used to remove detail view from a specific panel.
     * @param panelType which panel is being call from.
     */
    public void removeDetailView(PanelType panelType) {
        switch (panelType) {
            case PLAYLIST:
                playlistsView.getChildren().remove(playlistsView.getChildren().size() - 1);
                break;
            case ALBUM:
                albumsView.getChildren().remove(albumsView.getChildren().size() - 1);
                break;
            case ARTIST:
                artistsView.getChildren().remove(artistsView.getChildren().size() - 1);
                break;
        }
    }

    /**
     * A private function used to create detail view.
     * @param pane parent panel
     * @param panelType panel type
     * @return detailViewController for the newly created detailView.
     */
    private DetailViewController createDetailView(AnchorPane pane, PanelType panelType) {
        DetailViewController detailViewController = null;
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ui/view/DetailView.fxml"));
            Pane newLoadedPane = fxmlLoader.load();
            detailViewController = fxmlLoader.getController();
            pane.getChildren().add(newLoadedPane);
            AnchorPane.setBottomAnchor(newLoadedPane, 0.0);
            AnchorPane.setTopAnchor(newLoadedPane, 0.0);
            AnchorPane.setLeftAnchor(newLoadedPane, 0.0);
            AnchorPane.setRightAnchor(newLoadedPane, 0.0);

            detailViewController.setPanelType(panelType);
            detailViewController.setParentViewController(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return detailViewController;
    }

}
