package ui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class ThemeSelectorViewController implements Initializable {

    @FXML
    private Button applyButton,closeButton;
    @FXML
    private ListView<String> themeList;

    private ObservableList<String> themes;
    private MainViewController parentViewController;
    private Stage stage;
    @FXML
    private Pane container;

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
        themes = FXCollections.observableArrayList();
        themeList.setItems(themes);
        themes.clear();
        for(String k:MainViewController.THEMES.keySet()){
            themes.add(k);
        }
        themeList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                applyTheme(themeList.getSelectionModel().getSelectedItem());
            }
        });
        applyButton.setOnMouseClicked(event -> {
            applyTheme(themeList.getSelectionModel().getSelectedItem());
        });
        closeButton.setOnMouseClicked(event -> {
            close();
        });

    }

    public void setParentController(MainViewController mainViewController) {
        this.parentViewController = mainViewController;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }
    private void applyTheme(String name){
        parentViewController.setTheme(name);
        container.getStylesheets().clear();
        container.getStylesheets().add(parentViewController.getCurrentTheme());
    }
    private void close(){
        stage.close();
    }
    public void updateStyleSheet(){
        container.getStylesheets().clear();
        container.getStylesheets().add(parentViewController.getCurrentTheme());
    }
}
