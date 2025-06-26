package uni.proj;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ServerApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
        Scene scene = new Scene(loader.load(), 600, 400);
        stage.setScene(scene);
        stage.setTitle("Server");
        stage.show();
    }

    public static void launchApplication() {
        ServerApplication.launch(ServerApplication.class);
    }
}
