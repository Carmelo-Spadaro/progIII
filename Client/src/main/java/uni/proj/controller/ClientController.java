package uni.proj.controller;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import uni.proj.model.Client;
import uni.proj.model.ClientListener;
import uni.proj.model.protocol.data.ErrorData;
import uni.proj.model.protocol.data.ResponseData;
import uni.proj.model.protocol.data.SendMailData;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Stream;

public class ClientController implements Initializable, ClientListener {

    @FXML private Button logout;
    @FXML private StackPane mainStack;
    @FXML private Label errorLabel;
    @FXML private TextField titleField;
    @FXML private TextField bodyField;
    @FXML private TextField commandField;
    @FXML private TextField inputField;
    @FXML private Circle statusIndicator;
    @FXML private VBox login;
    @FXML private VBox main;
    @FXML private TextField emailInput;
    @FXML private FlowPane emailContainer;
    @FXML private ListView<SendMailData> mailList;

    Client client;
    private final Set<String> emailSet = new HashSet<>();
    private ObservableList<SendMailData> mails;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        client = new Client();
        client.registerListener(this);
        mails = client.getMails();
        mailList.setCellFactory(list -> new MailItemCell(this));
        mailList.setItems(mails);
        emailInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String email = emailInput.getText().trim();
                if (!email.isEmpty() && isValidEmail(email) && !emailSet.contains(email)) {
                    emailSet.add(email);
                    Label pill = createEmailPill(email);
                    emailContainer.getChildren().add(pill);
                    emailInput.clear();
                }
            }
        });
        inputField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String email = emailInput.getText().trim();
                if (!email.isEmpty() && isValidEmail(email)) {
                    login(inputField.getText());
                    inputField.clear();
                }
            }
        });

        bindStateIndicator();
        new Thread(client).start();
    }

    @Override
    public void onError(ErrorData error) {

    }

    @Override
    public void onResponse(ResponseData response) {
        Platform.runLater(() -> {
            switch (response.responseTo()) {
                case LOGIN -> {
                    login.setVisible(false);
                    login.setManaged(false);
                    logout.setVisible(true);
                    logout.setManaged(true);
                    main.setVisible(true);
                    main.setManaged(true);
                    client.execute("/getinbox");
                }
                case LOGOUT -> {
                    if(client.getListeners().size() > 1) {
                        for(ClientListener listener : client.getListeners()) {
                            if(listener.equals(this))
                                continue;
                            client.removeListener(listener);
                        }
                    }
                    backToInbox(null);
                    login.setVisible(true);
                    login.setManaged(true);
                    logout.setVisible(false);
                    logout.setManaged(false);
                    main.setVisible(false);
                    main.setManaged(false);
                }
            }
        });
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
                    case OFFLINE -> statusIndicator.setFill(Color.GRAY);
                }
            });
        });
    }

    private Label createEmailPill(String email) {
        Label pill = new Label(email);
        pill.setStyle(
                "-fx-background-color: #e0e0e0;" +
                        " -fx-padding: 5 10 5 10;" +
                        " -fx-background-radius: 20;" +
                        " -fx-text-fill: #333333;"
        );
        System.out.println(pill.getStyleClass());
        pill.setCursor(Cursor.HAND);
        pill.setOnMouseClicked(event -> {
            emailContainer.getChildren().remove(pill);
            emailSet.remove(email);
        });
        return pill;
    }

    @FXML
    public void onSend() {
        String command = commandField.getText().trim();
        client.execute(command);
        commandField.clear();
    }

    @FXML
    public void onSendMessage() {
        String title = titleField.getText();
        String body = bodyField.getText();

        if (title == null || title.trim().isEmpty()) {
            showError("Il campo Oggetto è obbligatorio e non può essere vuoto.");
            return;
        }
        if (body == null || body.trim().isEmpty()) {
            showError("Il campo Contenuto è obbligatorio e non può essere vuoto.");
            return;
        }
        if (emailSet.isEmpty()) {
            showError("Devi inserire almeno un destinatario email.");
            return;
        }

        title = escapeQuotes(title.trim());
        body = escapeQuotes(body.trim());

        String command = "/sendmail \"" + title + "\" \"" + body + "\"";
        for (String email : emailSet) {
            command += " " + email.trim();
        }
        client.execute(command);

        emailSet.clear();
        emailContainer.getChildren().clear();
        titleField.clear();
        bodyField.clear();
    }

    private boolean login(String email) {
        String command = "/login " + email.trim();
        return client.execute(command);
    }

    @FXML
    public void onLogin() {
        String email = inputField.getText();
        if (!email.isEmpty() && isValidEmail(email)) {
            login(email);
            inputField.clear();
        }

    }

    @FXML
    public void onLogout() {
        client.execute("/logout");
    }

    @FXML
    public void onRegister() {
        String command = "/register " + inputField.getText().trim();
        client.execute(command);
        inputField.clear();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    public void shutdown() {
        client.stop();
    }


    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private String escapeQuotes(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void showMailDetail(SendMailData data) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/mail_detail.fxml"));
            Parent detailRoot = loader.load();
            MailDetailController detailController = loader.getController();
            detailController.setData(data, this, client);
            mainStack.getChildren().add(detailRoot); // vista "sopra
            main.setVisible(false);
            main.setManaged(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void backToInbox(ClientListener listener) {
        if (mainStack.getChildren().size() > 1) {
            mainStack.getChildren().removeLast();
            main.setVisible(true);
            main.setManaged(true);
            if(listener != null) {
                client.removeListener(listener);
            }
        }
    }
}
