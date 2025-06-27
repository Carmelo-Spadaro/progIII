package uni.proj.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.collections.*;

import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import uni.proj.model.Log;
import uni.proj.model.Server;

public class ServerController implements Initializable {

    @FXML private ListView<Socket> socketListView;
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
        socketListView.setItems(server.getClients());
    }

    @FXML
    private void onSend() {
        String command = inputField.getText().trim();
        server.execute(command);
        inputField.clear();
        tableView.scrollTo(logs.size() - 1);
    }

    @FXML
    private void onHelp() {
        Stage helpStage = new Stage();
        helpStage.setTitle("Elenco Comandi");

        // Contenuto scrollabile
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);

        // Contenitore per i comandi
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // Messaggio introduttivo
        Label introLabel = new Label(
                "Qualsiasi testo che **non** inizia con '/' verrà inviato come messaggio broadcast ai client connessi."
        );
        introLabel.setWrapText(true);

        // Comandi
        Label cmd1 = new Label("/start - Avvia il server");
        Label cmd2 = new Label("/stop - Ferma il server");
        Label cmd3 = new Label("/clear - Pulisce i Log");

        content.getChildren().addAll(introLabel, new Separator(), cmd1, cmd2, cmd3);

        scrollPane.setContent(content);

        // Scene e visualizzazione
        Scene scene = new Scene(scrollPane, 400, 300);
        helpStage.setScene(scene);
        helpStage.initModality(Modality.APPLICATION_MODAL); // Blocca interazione con la finestra principale
        helpStage.showAndWait();
    }

    public void shutdown() {
        if(server.isRunning())
            server.stopServer();
    }
}
