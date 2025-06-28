package uni.proj.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import uni.proj.model.Client;

import java.net.URL;
import java.util.ResourceBundle;

public class ClientController implements Initializable {

    @FXML private TextField inputField;
    @FXML private Circle statusIndicator;

    Client client;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        client = new Client();
        bindStateIndicator();
        new Thread(client).start();
    }

    private void bindStateIndicator() {
        // Listener per lo stato
        client.getStateProperty().addListener((obs, oldState, newState) -> {
            Platform.runLater(() -> {
                switch (newState) {
                    case STOPPED -> statusIndicator.setFill(Color.RED);
                    case INITIALIZED -> statusIndicator.setFill(Color.ORANGE);
                    case STARTED -> statusIndicator.setFill(Color.GREEN);
                    case LOGGED -> statusIndicator.setFill(Color.DODGERBLUE);
                }
            });
        });
    }

    @FXML
    public void onSend() {
        String command = inputField.getText().trim();
        client.execute(command);
        inputField.clear();
    }

    public void shutdown() {
        client.stop();
    }
}
