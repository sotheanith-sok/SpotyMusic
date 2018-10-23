import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import persistence.DataManager;
import ui.component.Router;

public class Main extends Application {

    public static String mainID = "main";
    public static String mainFile = "../../ui/view/MainView.fxml";
    public static String splashID = "splash";
    public static String splashFile = "../../ui/view/SplashUI.fxml";
    public static String logoFile = "../resources/logo.png";

    @Override
    public void start(Stage primaryStage) {

        DataManager.getDataManager().init();

        Router mainRouter = new Router();
        mainRouter.loadView(mainID, mainFile);
        mainRouter.loadView(splashID, splashFile);

        /*Parent root = null;
        try {
            root = FXMLLoader.load(getClass().getResource("ui/view/MainView.fxml"));
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        mainRouter.setView(splashID);
        Scene scene = new Scene(mainRouter, 1280, 720);

        primaryStage.setTitle("SpotyMusic");
        //primaryStage.getIcons().add(new Image(logoFile));

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
