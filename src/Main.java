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

public class Main extends Application {

    public static String mainID = "main";
    public static String mainFile = "MainView.fxml";
    public static String splashID = "splash";
    public static String splashFile = "SplashUI.fxml";

    @Override
    public void start(Stage primaryStage) throws Exception{

        DataManager.getDataManager().init();
        //Router mainRouter = new Router();
        //mainRouter.loadView(mainID, mainFile);
        //mainRouter.loadView(splashID, splashFile);

        //mainRouter.setView(splashID);

        //System.out.println(DataManager.getDataManager().getCurrentLibrary().get().getSongs().size());
        Parent root = FXMLLoader.load(getClass().getResource("ui/view/MainView.fxml"));

        //Group root = new Group();
        //root.getChildren().addAll(mainRouter);
        Scene scene = new Scene(root, 1280, 720);


        primaryStage.setTitle("Spusic");
          primaryStage.setOnCloseRequest(t -> {
          Platform.exit();
            System.exit(0);
       });
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
