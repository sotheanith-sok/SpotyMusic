package ui.controller;

import connect.Song;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import persistence.DataManager;
import ui.component.ControlledView;
import ui.component.Router;

import javax.xml.crypto.Data;
import java.net.URL;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;

/**
 *
 */
public class MainViewController implements Initializable, ControlledView {
    Router router;
    @FXML
    private LeftViewController leftViewController;
    @FXML
    private RightViewController rightViewController;
    @FXML
    private BottomViewController bottomViewController;

    @FXML
    private Pane container;

    private String currentTheme = "Default";

    public void setViewParent(Router viewParent) {
        router = viewParent;
    }

    @Override
    public void beforeShow() {
        loadCurrentLibrary();
    }

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
        //Give MainViewController reference to sub controllers
        System.out.println("[MainViewController] initializing MainViewController");
        leftViewController.setParentViewController(this);
        rightViewController.setParentViewController(this);
        bottomViewController.setParentViewController(this);

    }


    /**
     * Load a library from DataManager
     */
    public void loadCurrentLibrary() {
        //boolean result =DataManager.getDataManager().tryAuth("nico", "78736779");
        System.out.println("[MainViewController][loadCurrentLibrary] Getting username");
        leftViewController.setUserName(DataManager.getDataManager().getCurrentUser().getUsername());

        try {
            System.out.println("[MainViewController][loadCurrentLibrary] Applying library to right view");
            rightViewController.setCurrentLibrary(DataManager.getDataManager().getCurrentLibrary().get());
            System.out.println("[MainViewController][loadCurrentLibrary] Done");

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        System.out.println("[MainViewController][loadCurrentLibrary] Applying user theme");
        this.setTheme(DataManager.getDataManager().getCurrentUser().getTheme());

    }

    /**
     * Changes the current theme.
     * Note that this function works by retrieving the Scene that the MainView is part of, and changing its list
     * of stylesheets. Thus, this function will effect everything in the current scene, not just the MainView.
     *
     * @param name the name of the new theme to change to. If the given string does not name a valid theme,
     *             then the theme is not changed and the error is logged.
     */
    public void setTheme(String name) {
        if (THEMES.containsKey(name)) {
            ObservableList<String> stylesheets = container.getStylesheets();
            stylesheets.clear();
            stylesheets.add(THEMES.get(name));
            this.currentTheme = name;
            DataManager.getDataManager().getCurrentUser().setTheme(name);

        } else {
            System.err.print("[MainViewController][setTheme] Unknown theme: ");
            System.err.println(name);
        }
    }

    public String getCurrentTheme(){
        if(THEMES.containsKey(currentTheme)){
            return THEMES.get(currentTheme);
        }else{
            return THEMES.get("Default");
        }
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

    @FXML
    public void trySignOut() {
        this.setTheme("Default");
        router.setView("splash");
    }

    public static final HashMap<String, String> THEMES;

    static {
        THEMES = new HashMap<>();
        THEMES.put("Default", MainViewController.class.getResource("/ui/view/styleSheets/Default.css").toExternalForm());
        THEMES.put("Neon", MainViewController.class.getResource("/ui/view/styleSheets/NeonTheme.css").toExternalForm());
        THEMES.put("Pastel", MainViewController.class.getResource("/ui/view/styleSheets/PastelTheme.css").toExternalForm());
    }
}
