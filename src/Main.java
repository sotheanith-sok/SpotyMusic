import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.stage.Stage;
import persistence.DataManager;
import ui.component.Router;

import javax.xml.crypto.Data;

public class Main extends Application {

    public static String mainID = "mainview";
    public static String mainFile = "MainView.fxml";
    public static String splashID = "splashview";
    public static String splashFile = "SplashUI.fxml";

    @Override
    public void start(Stage primaryStage) throws Exception{

        DataManager.getDataManager().init();

        Router mainRouter = new Router();
        mainRouter.loadView(Main.mainID, Main.mainFile);
        mainRouter.loadView(Main.splashID, Main.splashFile);

        mainRouter.setView(Main.splashID);

        Group root = new Group();
        root.getChildren().addAll(mainRouter);
        Scene scene = new Scene(root, 800, 600);

        primaryStage.setTitle("Spusic");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static String getMainID() {
        return mainID;
    }

    public static String getSplashID(){
        return splashID;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
