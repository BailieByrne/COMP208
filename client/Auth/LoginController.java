import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

// login screen controller
public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField ipField;
    @FXML private TextField portField;
    @FXML private Label statusLabel;

    @FXML
    private void onLogin() {
        String username = usernameField != null ? usernameField.getText().trim() : "";
        String password = passwordField != null ? passwordField.getText().trim() : "";
        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Enter username and password");
            return;
        }
        
        setStatus("Configuring server connection...");
        configureConnection();
        
        // Wait for connection to be established before logging in
        new Thread(() -> {
            try {
                // Wait up to 3 seconds for connection
                for (int i = 0; i < 30; i++) {
                    if (client.getInstance().isConnected()) {
                        System.out.println("Server connection established");
                        javafx.application.Platform.runLater(() -> {
                            setStatus("Logging in...");
                            client.getInstance().requestLogin(username, password);
                        });
                        return;
                    }
                    Thread.sleep(100);
                }
                // Connection failed after timeout
                javafx.application.Platform.runLater(() -> {
                    setStatus("Failed to connect to server. Check IP and port.");
                });
            } catch (InterruptedException e) {
                javafx.application.Platform.runLater(() -> setStatus("Connection interrupted"));
            }
        }).start();
    }
    
    private void configureConnection() {
        String ip = "localhost";
        int port = 5000;
        
        // Get IP from field if provided
        if (ipField != null && ipField.getText() != null && !ipField.getText().trim().isEmpty()) {
            ip = ipField.getText().trim();
        }
        
        // Get port from field if provided
        if (portField != null && portField.getText() != null && !portField.getText().trim().isEmpty()) {
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException e) {
                setStatus("Invalid port number, using default 5000");
                port = 5000;
            }
        }
        
        client.getInstance().setServerConnection(ip, port);
    }

    public void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text);
        }
    }

    @FXML
    private void onSignup() {
        String username = usernameField != null ? usernameField.getText().trim() : "";
        String password = passwordField != null ? passwordField.getText().trim() : "";
        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Enter username and password");
            return;
        }
        
        setStatus("Configuring server connection...");
        configureConnection();
        
        // Wait for connection to be established before signing up
        new Thread(() -> {
            try {
                // Wait up to 3 seconds for connection
                for (int i = 0; i < 30; i++) {
                    if (client.getInstance().isConnected()) {
                        System.out.println("Server connection established");
                        javafx.application.Platform.runLater(() -> {
                            setStatus("Creating account...");
                            client.getInstance().requestSignup(username, password);
                        });
                        return;
                    }
                    Thread.sleep(100);
                }
                // Connection failed after timeout
                javafx.application.Platform.runLater(() -> {
                    setStatus("Failed to connect to server. Check IP and port.");
                });
            } catch (InterruptedException e) {
                javafx.application.Platform.runLater(() -> setStatus("Connection interrupted"));
            }
        }).start();
    }
}
