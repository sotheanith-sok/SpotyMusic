package ui.controller;

import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

/**
 *
 */
public class LeftViewController implements Initializable {

    private MainViewController parentViewController;


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

    }

    /**
     * Called to set the reference to the MainViewController
     *
     * @param mainViewController
     */
    public void setParentViewController(MainViewController mainViewController) {
        this.parentViewController = mainViewController;
    }
}
