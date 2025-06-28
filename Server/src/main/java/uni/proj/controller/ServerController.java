package uni.proj.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
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
import uni.proj.model.ClientHandler;
import uni.proj.model.Log;
import uni.proj.model.Server;
import uni.proj.model.protocol.data.RegisterData;

public class ServerController implements Initializable {

    @FXML private ListView<RegisterData> emailListView;
    @FXML private ListView<ClientHandler> socketListView;
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
        emailListView.setItems(server.getEmails());
    }

    @FXML
    private void onSend() {
        String command = inputField.getText().trim();

        Task<Object> task = new Task<>(){
            @Override
            protected Object call() {
                server.execute(command);
                return null;
            }

            @Override
            protected void succeeded() {
                tableView.scrollTo(logs.size() - 1);
            }

            @Override
            protected void failed() {
                tableView.scrollTo(logs.size() - 1);
            }
        };
        new Thread(task).start();

        inputField.clear();
    }

    @FXML
    public void onDisconnect() {
        ClientHandler selected = socketListView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Nessuna selezione");
            alert.setHeaderText(null);
            alert.setContentText("Seleziona un client dalla lista per disconnetterlo.");
            alert.showAndWait();
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                selected.shutdown(); // può lanciare eccezioni
                return null;
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Errore");
                    alert.setHeaderText(null);
                    alert.setContentText("Errore durante la disconnessione del client.");
                    alert.showAndWait();
                });
            }
        };

        new Thread(task).start();
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
