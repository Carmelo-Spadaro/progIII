package uni.proj.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.collections.*;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

import uni.proj.model.Log;
import uni.proj.model.Server;

public class ServerController implements Initializable {

    @FXML private TableView<Log> tableView;
    @FXML private TableColumn<Log, String> typeColumn;
    @FXML private TableColumn<Log, String> messageColumn;
    @FXML private TableColumn<Log, String> timeColumn;
    @FXML private TextField inputField;

    private ObservableList<Log> logs;
    private Server server;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        typeColumn.setCellValueFactory(data -> data.getValue().typeProperty());
        messageColumn.setCellValueFactory(data -> data.getValue().messageProperty());
        timeColumn.setCellValueFactory(data -> data.getValue().timeProperty());


        server = new Server();
        server.startServer();

        logs = server.getLogger().getLogs();
        tableView.setItems(logs);
    }

    @FXML
    private void onSend() {
        String command = inputField.getText().trim();
        server.execute(command);
        inputField.clear();
        tableView.scrollTo(logs.size() - 1);
    }

}
