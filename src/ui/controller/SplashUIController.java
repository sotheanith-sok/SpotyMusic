package ui.controller;

/**
 * The <code>SplashUIController</code> handles initial login and context switching.
 *
 * @author Brian Powell
 * @since 0.0.1
 */

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
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

            DialogPane dPane = noSuchUserAlert.getDialogPane();
            dPane.getStylesheets().add(getClass().getResource("../../ui/view/styleSheets/NeonTheme.css").toExternalForm());
            dPane.getStyleClass().add("myDialog");

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

         DialogPane dPane = loginFailAlert.getDialogPane();
         dPane.getStylesheets().add(getClass().getResource("../../ui/view/styleSheets/NeonTheme.css").toExternalForm());
         dPane.getStyleClass().add("myDialog");

         loginFailAlert.show();

         txtPass.setText("");
         txtUser.setText("");
         txtUser.requestFocus();
      }
   }

   @FXML
   void tryRegister() {
       if (!txtUser.getText().isEmpty() && !txtPass.getText().isEmpty()) {
           try {
               DM.registerUser(txtUser.getText(), txtPass.getText());
               System.out.println("User successfully registered.");
               clickedSignOn();
           } catch (IllegalArgumentException ex) {
               Alert UserExistsAlert = new Alert(Alert.AlertType.INFORMATION);
               UserExistsAlert.setTitle("User Exists");
               UserExistsAlert.setHeaderText("Register Error");
               String message = "This user already exists, please log in.";
               UserExistsAlert.setContentText(message);

               DialogPane dPane = UserExistsAlert.getDialogPane();
               dPane.getStylesheets().add(getClass().getResource("../../ui/view/styleSheets/NeonTheme.css").toExternalForm());
               dPane.getStyleClass().add("myDialog");

               UserExistsAlert.show();
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

    @FXML
    void closeApp() {
        System.exit(0);
    }
}
