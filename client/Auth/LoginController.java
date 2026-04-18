import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

// login screen controller
public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @FXML
    private void onLogin() {
        String username = usernameField != null ? usernameField.getText().trim() : "";
        String password = passwordField != null ? passwordField.getText().trim() : "";
        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Enter username and password");
            return;
        }
        setStatus("Connecting...");
        client.getInstance().requestLogin(username, password);
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
        setStatus("Creating account...");
        client.getInstance().requestSignup(username, password);
    }
}
