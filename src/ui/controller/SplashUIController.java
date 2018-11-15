package ui.controller;

/**
 * The <code>SplashUIController</code> handles initial login and context switching.
 *
 * @author Brian Powell
 * @since 0.0.1
 */

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import mesh.MeshSystem;
import ui.component.ControlledView;
import ui.component.Router;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SplashUIController implements Initializable, ControlledView {

    Router router;
    @FXML
    private PasswordField txtPass;
    @FXML
    private TextField txtUser;

    private TryAuthTask tryAuthTask = null;

    private RegisterTask registerTask = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // TODO
    }

    public void setViewParent(Router viewParent) {
        router = viewParent;
    }

    @FXML
    void clickedSignOn() {
        if (!txtUser.getText().isEmpty() && !txtPass.getText().isEmpty()) {
            if (this.tryAuthTask == null || !this.tryAuthTask.isRunning()) {
                this.tryAuthTask = new TryAuthTask(txtUser.getText(), txtPass.getText());
                this.tryAuthTask.setOnRunning((event) -> this.authRunning());
                this.tryAuthTask.setOnCancelled((event) -> this.authError());
                this.tryAuthTask.setOnFailed((event) -> this.authError());
                this.tryAuthTask.setOnSucceeded((event) -> {
                    boolean authd = (Boolean) event.getSource().getValue();
                    if (authd) this.authSucceed();
                    else this.authFail();
                });
                Thread t = new Thread(this.tryAuthTask);
                t.setName("[SplashUIController][AuthenticatorThread]");
                t.setDaemon(true);
                t.start();

            } else {
                this.authRunning();
            }

        } else {
            Alert loginFailAlert = new Alert(Alert.AlertType.INFORMATION);
            loginFailAlert.setTitle("Sign In Failed");
            loginFailAlert.setHeaderText("Information Error");
            String message = "Please Enter a Valid Username and Password.";
            loginFailAlert.setContentText(message);

            DialogPane dPane = loginFailAlert.getDialogPane();
            dPane.getStylesheets().add(getClass().getResource("../../ui/view/styleSheets/NeonTheme.css").toExternalForm());
            dPane.getStyleClass().add("myDialog");

            loginFailAlert.show();

            txtPass.setText("");
            txtUser.setText("");
            txtUser.requestFocus();
        }
    }

    /**
     * Updates the UI to indicate that there is background work being done
     */
    void authRunning() {
        // TODO: update UI to indicate background work
    }

    /**
     * Update the UI to indicate that there was a problem signing in (other than wrong user/password)
     */
    void authError() {
        Alert noSuchUserAlert = new Alert(Alert.AlertType.INFORMATION);
        noSuchUserAlert.setTitle("Sign In Failed");
        noSuchUserAlert.setHeaderText("Sign In failure");
        String message = "There was a problem signing in :(";
        noSuchUserAlert.setContentText(message);

        DialogPane dPane = noSuchUserAlert.getDialogPane();
        dPane.getStylesheets().add(getClass().getResource("../../ui/view/styleSheets/NeonTheme.css").toExternalForm());
        dPane.getStyleClass().add("myDialog");

        noSuchUserAlert.show();

        txtUser.setText("");
        txtPass.setText("");
        txtUser.requestFocus();
    }

    /**
     * Updates the UI to indicate that the entered username/password didn't match an account
     */
    void authFail() {
        Alert noSuchUserAlert = new Alert(Alert.AlertType.INFORMATION);
        noSuchUserAlert.setTitle("Sign In Failed");
        noSuchUserAlert.setHeaderText("User Not Found");
        String message = "Please enter a valid Username and Password.";
        noSuchUserAlert.setContentText(message);

        DialogPane dPane = noSuchUserAlert.getDialogPane();
        dPane.getStylesheets().add(getClass().getResource("../../ui/view/styleSheets/NeonTheme.css").toExternalForm());
        dPane.getStyleClass().add("myDialog");

        noSuchUserAlert.show();

        txtUser.setText("");
        txtPass.setText("");
        txtUser.requestFocus();
    }

    /**
     * Called when authentication succeeds
     */
    void authSucceed() {
        router.setView("main");
    }

    @FXML
    void tryRegister() {
        if (!txtUser.getText().isEmpty() && !txtPass.getText().isEmpty()) {
            if (this.registerTask == null || !this.registerTask.isRunning()) {
                this.registerTask = new RegisterTask(txtUser.getText(), txtPass.getText());
                this.registerTask.setOnRunning((event) -> this.registerRunning());
                this.registerTask.setOnCancelled((event) -> this.registerError());
                this.registerTask.setOnFailed((event) -> this.registerError());
                this.registerTask.setOnSucceeded((event) -> {
                    boolean success = (Boolean) event.getSource().getValue();
                    if (success) this.registerSuccess();
                    else this.registerFail();
                });
                Thread t = new Thread(this.registerTask);
                t.setDaemon(true);
                t.setName("[SplashUIController][RegistrationThread]");
                t.start();

            } else {
                this.registerRunning();
            }

        } else {
            Alert registerFailAlert = new Alert(Alert.AlertType.INFORMATION);
            registerFailAlert.setTitle("Register Failed");
            registerFailAlert.setHeaderText("Register Error");
            String message = "Please Enter a Valid Username and Password.";
            registerFailAlert.setContentText(message);

            DialogPane dPane = registerFailAlert.getDialogPane();
            dPane.getStylesheets().add(getClass().getResource("../../ui/view/styleSheets/NeonTheme.css").toExternalForm());
            dPane.getStyleClass().add("myDialog");


            registerFailAlert.show();

            txtPass.setText("");
            txtUser.setText("");
            txtUser.requestFocus();
        }
    }

    /**
     * Updates UI to show that background work is being done
     */
    void registerRunning() {

    }

    /***
     * Updates UI to show that there was an error while trying to register
     */
    void registerError() {

    }

    /**
     * Updates UI to show that the system was unable to register the user with the provided
     * username and password.
     */
    void registerFail() {
        Alert UserExistsAlert = new Alert(Alert.AlertType.INFORMATION);
        UserExistsAlert.setTitle("Register Failed");
        UserExistsAlert.setHeaderText("User Exists");
        String message = "This user already exists, please log in.";
        UserExistsAlert.setContentText(message);

        DialogPane dPane = UserExistsAlert.getDialogPane();
        dPane.getStylesheets().add(getClass().getResource("../../ui/view/styleSheets/NeonTheme.css").toExternalForm());
        dPane.getStyleClass().add("myDialog");

        UserExistsAlert.show();
    }

    /**
     * Called when registration succeeds
     */
    void registerSuccess() {
        System.out.println("User successfully registered.");
        router.setView("main");
    }

    @FXML
    void closeApp() {
        System.exit(0);
    }
}
class TryAuthTask extends Task<Boolean> {

    private final String username;

    private final String password;

    public TryAuthTask(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    protected Boolean call() throws Exception {
        Future<Boolean> future = MeshSystem.getInstance().getLibrary().tryAuth(this.username, this.password);

        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("[SplashUIController][TryAuthTask] Unable to authenticate");
            return false;
        }
    }
}
class RegisterTask extends Task<Boolean> {

    private final String username;

    private final String password;

    public RegisterTask(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    protected Boolean call() throws Exception {
        Future<Boolean> future = MeshSystem.getInstance().getLibrary().register(this.username, this.password);

        try {
            return future.get(15, TimeUnit.SECONDS);

        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            System.err.println("[SplashUIController][RegisterTask] Exception while trying to register");
            e.printStackTrace();
            return false;
        }
    }
}