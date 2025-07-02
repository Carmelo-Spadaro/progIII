package uni.proj.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import uni.proj.model.Client;
import uni.proj.model.ClientListener;
import uni.proj.model.protocol.MessageType;
import uni.proj.model.protocol.data.ErrorData;
import uni.proj.model.protocol.data.ResponseData;
import uni.proj.model.protocol.data.SendMailData;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class MailDetailController implements ClientListener {

    @FXML Button delete;
    @FXML private Label errorForwardLabel;
    @FXML private FlowPane emailContainer;
    @FXML private TextField emailInput;
    @FXML private Label errorLabel;
    @FXML private TextField bodyField;
    @FXML private Label senderLabel;
    @FXML private Label receiversLabel;
    @FXML private Label titleLabel;
    @FXML private TextArea bodyArea;

    private SendMailData data;
    private ClientController inboxController;
    private final Set<String> emailSet = new HashSet<>();
    private Client client;


    public void setData(SendMailData data, ClientController controller, Client client) {
        this.inboxController = controller;
        this.client = client;
        client.registerListener(this);
        this.data = data;
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
        senderLabel.setText("From: " + data.senderEmail());
        receiversLabel.setText("To: " + String.join(", ", data.receiversEmail()));
        titleLabel.setText(data.title());
        bodyArea.setText(data.body());
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
    private void onBack() {
        inboxController.backToInbox(this);
    }

    @FXML
    private void onReply() {
        String body = bodyField.getText();

        if (body == null || body.trim().isEmpty()) {
            showError("Il campo Contenuto è obbligatorio e non può essere vuoto.");
            return;
        }

        body = escapeQuotes(body.trim());


        String command = "/sendmail \"" + data.title() + "\" \"" + body + "\" " + data.senderEmail();
        client.execute(command);
        bodyField.clear();
    }

    @FXML
    private void onReplyAll() {
        String body = bodyField.getText();

        if (body == null || body.trim().isEmpty()) {
            showError("Il campo Contenuto è obbligatorio e non può essere vuoto.");
            return;
        }

        body = escapeQuotes(body.trim());
        String[] sendTo = Stream.concat(Stream.of(data.senderEmail()), Stream.of(data.receiversEmail())).distinct().toArray(String[]::new);
        String command = "/sendmail \"" + escapeQuotes(data.title()) + "\" \"" + body + "\"";
        for(String s : sendTo) {
            command += " " + s;
        }
        client.execute(command);
        bodyField.clear();
    }

    @FXML
    private void onDelete() {
        String command = "/delete " + client.getLoggedMail() + " \"" + escapeQuotes(data.title()) + "\" \"" + escapeQuotes(data.body()) + "\"";
        for(String receiver : data.receiversEmail()) {
            command += " " + receiver;
        }
        client.execute(command);
    }

    @FXML
    private void onForward() {
        if (emailSet.isEmpty()) {
            showForwardError("Devi inserire almeno un destinatario email.");
            return;
        }
        String command = "/forward " + data.senderEmail() + " \"" + data.title() + "\" \"" + data.body() + "\"";
        for(String s : data.receiversEmail()) {
            command += " " + s;
        }
        command += " forwardto";
        for(String s : emailSet) {
            command += " " + s;
        }
        client.execute(command);
        emailSet.clear();
        emailContainer.getChildren().clear();
    }

    private void showForwardError(String message) {
        errorForwardLabel.setText(message);
        errorForwardLabel.setVisible(true);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private String escapeQuotes(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void onResponse(ResponseData response) {
        Platform.runLater(() -> {
            if (response.responseTo().equals(MessageType.DELETE)) {
                System.out.println("eseguo delete");
                client.getMails().remove(data);
                onBack();
            }
        });
    }

    @Override
    public void onError(ErrorData error) {

    }
}
