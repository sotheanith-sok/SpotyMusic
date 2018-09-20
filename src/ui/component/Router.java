package ui.component;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import javax.naming.ldap.Control;
import java.sql.Time;

import java.util.HashMap;


public class Router extends StackPane {

    private HashMap<String, Node> ViewMap = new HashMap<>();
    private HashMap<String, ControlledView> controllerMap = new HashMap<>();

    public Router()
    {
        super();
    }

    //Add a screen to the collection
    public void addView(String name, Node view){
        ViewMap.put(name, view);
    }

    //Return Node with its name
    public Node getView(String name){
        return ViewMap.get(name);
    }

    //Loads FXML file and adds it to ui.view collection
    //injects controller to the interface
    public boolean loadView(String name, String rsrc){
       System.out.println("[Router] Loading view: " + name);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(rsrc));
            //load FXML file into the parent
            Parent loadView = loader.load();

            //load FXML Controller
            ControlledView controller = loader.getController();
            controllerMap.put(name, controller);

            controller.setViewParent(this);
            addView(name, loadView);
            System.out.println("[Router] Loaded view \"" + name + "\"");

            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    //Function tries to display a ui.view with a specified name
    //Checks to see if a screen has been loaded
    //If more than one screen is being added, second screen is added, the current screen removed
    //If there isn't screen being displayed, new screen is added to root
    public boolean setView(final String name){
        System.out.println("[Router] Switching to view \"" + name + "\"");
        if(ViewMap.get(name) != null) { //ui.view load check
            final DoubleProperty opacity = opacityProperty();

            if (!getChildren().isEmpty()) {
                //fade animation
                Timeline fade = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(opacity, 1.0)),
                        new KeyFrame(new Duration(1000), new EventHandler<ActionEvent>() {
                            @Override
                            public void handle(ActionEvent event) {
                                getChildren().remove(0);                    //remove the displayed screen
                                controllerMap.get(name).beforeShow();
                                getChildren().add(0, ViewMap.get(name));    //add the screen
                                Timeline fadeIn = new Timeline(
                                        new KeyFrame(Duration.ZERO, new KeyValue(opacity, 0.0)),
                                        new KeyFrame(new Duration(800), new KeyValue(opacity, 1.0)));
                                fadeIn.play();
                            }
                        }, new KeyValue(opacity, 0.0)));
                        fade.play();
            } else {
                setOpacity(0.0);
                getChildren().add(ViewMap.get(name)); //no ui.view being display, show anyway
                Timeline fadeIn = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(opacity, 0.0)),
                        new KeyFrame(new Duration(2500), new KeyValue(opacity, 1.0)));
                fadeIn.play();
            }
            return true;
        } else {
            System.out.println("[Router] No ui.view loaded");
            return false;
        }
    }


    //Function to remove a ui.view from the collection
    public boolean unloadView(String name) {
        if (ViewMap.remove(name) == null){
            System.out.println("View doesn't exist\n");
            return false;
        } else {
            return true;
        }
    }


}
