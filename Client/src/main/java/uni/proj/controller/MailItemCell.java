package uni.proj.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import uni.proj.model.protocol.data.SendMailData;

import java.io.IOException;

public class MailItemCell extends ListCell<SendMailData> {
    private HBox root;
    private MailItemCellController controller;

    public MailItemCell() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/mail_item.fxml"));
            root = loader.load();
            controller = loader.getController();
        } catch (IOException e) {
            e.printStackTrace();
            root = new HBox(); // fallback
        }
    }

    @Override
    protected void updateItem(SendMailData item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            controller.setData(item);
            setGraphic(root);
        }
    }
}
