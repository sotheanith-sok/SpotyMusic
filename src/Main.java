import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import persistence.DataManager;
import ui.component.Router;

import java.io.IOException;

public class Main extends Application {

    public static String mainID = "main";
    public static String mainFile = "../../ui/view/MainView.fxml";
    public static String splashID = "splash";
    public static String splashFile = "../../ui/view/SplashUI.fxml";

    @Override
    public void start(Stage primaryStage) {

        DataManager.getDataManager().init();

        Router mainRouter = new Router();
        mainRouter.loadView(mainID, mainFile);
        mainRouter.loadView(splashID, splashFile);

        Parent root = null;
        try {
            root = FXMLLoader.load(getClass().getResource("ui/view/MainView.fxml"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        mainRouter.setView(splashID);

       /* Group root = new Group();
        root.getChildren().addAll(mainRouter);*/
        Scene scene = new Scene(root, 800, 600);

        primaryStage.setTitle("Spusic");
          primaryStage.setOnCloseRequest(t -> {
          Platform.exit();
            System.exit(0);
       });
        primaryStage.setScene(scene);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
