import java.util.*;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class main extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        System.out.println("JavaFX is working!");
        loadFXML("loginmenu.fxml");
    }

    public static void loadFXML(String fxmlFile) {
        try {
            java.net.URL resource = main.class.getResource(fxmlFile);
            if (resource == null) {
                System.err.println("FXML file not found: " + fxmlFile);
                return;
            }
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}