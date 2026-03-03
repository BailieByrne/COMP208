import java.io.*;
import java.net.*;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class client extends Application {
    
    @Override
    public void start(Stage stage) throws Exception {
        // Load and display the login menu
        Parent root = FXMLLoader.load(getClass().getResource("loginmenu.fxml"));
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
        
        // Start client connection in background thread
        Thread clientThread = new Thread(new ClientConnector());
        clientThread.setDaemon(true);
        clientThread.start();
    }
    
    // Inner class to handle socket connection
    private static class ClientConnector implements Runnable {
        @Override
        public void run() {
            try (Socket socket = new Socket("127.0.0.1", 5000)) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                int counter = 0;
                //Here we add the main loop for the game
                while (!Thread.currentThread().isInterrupted()) {
                    out.println(counter++);
                    Thread.sleep(1000);
                }
            } catch (IOException | InterruptedException e) {
                if (!(e instanceof InterruptedException)) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
