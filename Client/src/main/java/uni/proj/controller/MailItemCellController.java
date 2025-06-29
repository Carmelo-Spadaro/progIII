package uni.proj.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import uni.proj.model.protocol.data.SendMailData;

public class MailItemCellController {

    @FXML private Label senderLabel;
    @FXML private Label titleLabel;
    @FXML private Text bodyPreview;
    @FXML private Label receiversLabel;

    public void setData(SendMailData data) {
        senderLabel.setText(data.senderEmail());
        titleLabel.setText(data.title());
        bodyPreview.setText(getBodyPreview(data.body()));
        receiversLabel.setText("To: " + String.join(", ", data.receiversEmail()));
    }

    private String getBodyPreview(String body) {
        return body.length() > 100 ? body.substring(0, 97) + "..." : body;
    }
}
