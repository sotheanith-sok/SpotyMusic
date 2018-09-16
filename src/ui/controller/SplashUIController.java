package ui.controller;

/**
 * The <code>SplashUIController</code> handles initial login and context switching.
 *
 * @since 0.0.1
 * @author Brian Powell
 */

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import persistence.DataManager;
import ui.component.ControlledView;
import ui.component.Router;


public class SplashUIController implements ControlledView {

    DataManager DM = DataManager.getDataManager();
    Router router;

    public void setScreenParent(Router viewParent) {
        router = viewParent;
    }

    @FXML
    private PasswordField txtPass;

    @FXML
    private TextField txtUser;

    @FXML
    private Button btnSignIn;

    @FXML
    private Button btnRegister;

    @FXML
    void clickedSignOn() {
        if (!txtUser.getText().isEmpty() && !txtPass.getText().isEmpty())
        {
            if(DM.tryAuth(txtUser.getText(), txtPass.getText())){
                router.setView("mainview");

            } else {
                Alert noSuchUserAlert = new Alert(Alert.AlertType.INFORMATION);
                noSuchUserAlert.setTitle("User Not Found");
                noSuchUserAlert.setHeaderText("Login Error");
                String message = "Please enter a valid Username and Password.";
                noSuchUserAlert.setContentText(message);
                noSuchUserAlert.show();

                txtUser.setText("");
                txtPass.setText("");
                txtUser.requestFocus();
            }
        }
        else
        {
            Alert loginFailAlert =  new Alert(Alert.AlertType.INFORMATION);
            loginFailAlert.setTitle("Login Failed");
            loginFailAlert.setHeaderText("Information Error");
            String message = "Please Enter a Valid Username and Password.";
            loginFailAlert.setContentText(message);
            loginFailAlert.show();

            txtPass.setText("");
            txtUser.setText("");
            txtUser.requestFocus();
        }
    }

    @FXML
    void tryRegister() {

    }
}
