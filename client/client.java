import java.io.*;
import java.net.*;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class client extends Application {
    private Thread clientThread;
    
    @Override
    public void start(Stage stage) throws Exception {
        // Load and display the login menu
        Parent root = FXMLLoader.load(getClass().getResource("loginmenu.fxml"));
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();

        /**
         * IMPORTANT:
         * This is how to extract interacvtions from the fxml files
         * in the FXML define a fx:id = {name} on a button or other interactable element
         * then have a root.lookup('name') for that id and if its not null (been pressed)
         * set an on action to do a func
         */
        Button loginButton = (Button) root.lookup("#loginButton");
        if (loginButton != null) {
            //Use a lamda to def button actions
            loginButton.setOnAction(event -> {
                if (clientThread != null && clientThread.isAlive()) {
                    stopClientThread();
                    loginButton.setText("Start");
                    System.out.println("Stopping client thread...");
                } else {
                    startClientThread();
                    loginButton.setText("Stop");
                    System.out.println("Starting client thread...");
                }
            });
        }else{
            System.out.println("Login button not found in FXML");
        }

        //IF a button starts a threa dMAKE SURE ITS CLOSED ON STAGE CLOSE
        //otherwise itll stay open and leak memory/performance
        stage.setOnCloseRequest(event -> {
            if (clientThread != null && clientThread.isAlive()) {
                clientThread.interrupt();
            }
        });
    }

    private void startClientThread() {
        if (clientThread != null && clientThread.isAlive()) {
            return;
        }
        clientThread = new Thread(new ClientConnector());
        clientThread.setDaemon(true);
        clientThread.start();
    }
    
    private void stopClientThread() {
        if (clientThread != null && clientThread.isAlive()) {
            clientThread.interrupt();
        }
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
                    out.println(counter);
                    System.out.println("Sent: " + counter);
                    counter++;
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
