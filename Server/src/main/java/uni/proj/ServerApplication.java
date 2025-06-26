package uni.proj;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import uni.proj.controller.ServerController;

public class ServerApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
        Scene scene = new Scene(loader.load(), 600, 400);
        stage.setScene(scene);
        stage.setTitle("Server");

        ServerController controller = loader.getController();

        stage.setOnCloseRequest(event -> {
            controller.shutdown();
            Platform.exit();
        });
        stage.show();
    }

    public static void launchApplication() {
        ServerApplication.launch(ServerApplication.class);
    }
}
