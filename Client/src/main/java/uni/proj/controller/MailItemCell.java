package uni.proj.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import uni.proj.model.protocol.data.SendMailData;

import java.io.IOException;

public class MailItemCell extends ListCell<SendMailData> {
    private HBox root;
    private MailItemCellController controller;

    public MailItemCell(ClientController clientController) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/mail_item.fxml"));
            root = loader.load();
            controller = loader.getController();
        } catch (IOException e) {
            e.printStackTrace();
            root = new HBox(); // fallback
        }
        setOnMouseClicked(event -> {
            if (!isEmpty() && event.getClickCount() == 2) {
                openDetailedView(getItem(), clientController);
            }
        });
    }

    private void openDetailedView(SendMailData data, ClientController clientController) {
        clientController.showMailDetail(data);
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
