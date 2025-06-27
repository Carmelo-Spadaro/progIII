package uni.proj.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import uni.proj.model.Client;

import java.net.URL;
import java.util.ResourceBundle;

public class ClientController implements Initializable {

    @FXML public TextField inputField;

    Client client;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        client = new Client();
        new Thread(client).start();
    }

    @FXML
    public void onSend(ActionEvent actionEvent) {
        String command = inputField.getText().trim();
        client.execute(command);
        inputField.clear();
    }

    public void shutdown() {
        client.stop();
    }
}
