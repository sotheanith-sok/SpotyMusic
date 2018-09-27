package ui.controller;

/**
 * The <code>SplashUIController</code> handles initial login and context switching.
 *
 * @author Brian Powell
 * @since 0.0.1
 */

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import persistence.DataManager;
import ui.component.ControlledView;
import ui.component.Router;

import java.net.URL;
import java.util.ResourceBundle;

public class SplashUIController implements Initializable, ControlledView {

   DataManager DM = DataManager.getDataManager();
   Router router;
   @FXML
   private PasswordField txtPass;
   @FXML
   private TextField txtUser;
   @FXML
   private Button btnSignIn;
   @FXML
   private Button btnRegister;

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
         if (DM.tryAuth(txtUser.getText(), txtPass.getText())) {
            router.setView("main");

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
      } else {
         Alert loginFailAlert = new Alert(Alert.AlertType.INFORMATION);
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
