package ui.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;

import java.net.URL;
import java.util.ResourceBundle;

public class DetailViewController implements Initializable {
   @FXML
   TableView<Person> tableView;
   @FXML
   TableColumn<Person,String> title,artist, album, length;

   int size =1;
   private ObservableList<Person> data;
   /**
    * Called to initialize a controller after its root element has been
    * completely processed.
    *
    * @param location  The location used to resolve relative paths for the root object, or
    *                  <tt>null</tt> if the location is not known.
    * @param resources The resources used to localize the root object, or <tt>null</tt> if
    */
   @Override
   public void initialize(URL location, ResourceBundle resources) {
      data=getPersonList();
      title.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getName()));
      artist.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getAge()));
      album.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getDate()));
      tableView.setItems(data);
      tableView.setRowFactory(tv->{
         TableRow<Person> row = new TableRow<>();
         row.setOnMouseClicked(event -> {
            if (! row.isEmpty() && event.getButton()== MouseButton.PRIMARY
                    && event.getClickCount() == 2) {

               Person clickedRow = row.getItem();
               printRow(clickedRow);
            }
         });
         return row;
      });
   }

   public ObservableList<Person> getPersonList(){
      ObservableList<Person> personObservableList= FXCollections.observableArrayList();
      for (int i =0; i<=100;i++){
         int k=i*size;
         personObservableList.add(new Person(Integer.toString(k),Integer.toString(k+1),Integer.toString(k+2)));
      }
      size+=1;
      return personObservableList;
   }
   private void printRow(Person clickedRow) {
      tableView.setItems(getPersonList());
   }
}
