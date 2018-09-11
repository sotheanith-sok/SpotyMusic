package ui.controller;

/**
 * The <code>SplashUIController</code> handles initial login and context switching.
 *
 * @since 0.0.1
 * @author Brian Powell
 */

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import persistence.DataManager;

import java.io.IOException;

public class SplashUIController {

    DataManager DM = DataManager.getDataManager();

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
          if(DM.tryAuth(txtUser.getText(), txtPass.getText()))
          {
               //try auth and router handling needs to happen
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
