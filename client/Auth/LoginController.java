import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;


//Basic for demo for now will add proper sanitisation and error handling later, also will add a remember me function to bypass this screen on login success, stores credentials in local JSON
public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @FXML
    private void onLogin() {
        // basic client-side field check
        String username = usernameField != null ? usernameField.getText().trim() : "";
        String password = passwordField != null ? passwordField.getText().trim() : "";

        if (username.isEmpty() || password.isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText("Enter username and password");
            }
            return;
        }

        String result = client.getInstance().requestLogin(username, password);
        if ("LOGIN_OK".equals(result)) {
            if (statusLabel != null) {
                statusLabel.setText("Login accepted");
            }
            client.getInstance().onLoginSuccess(username, password);
        } else {
            if (statusLabel != null) {
                statusLabel.setText("Login failed");
            }
        }
    }

    @FXML
    private void onSignup() {
        // signup then user logs in explicitly
        String username = usernameField != null ? usernameField.getText().trim() : "";
        String password = passwordField != null ? passwordField.getText().trim() : "";

        if (username.isEmpty() || password.isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText("Enter username and password");
            }
            return;
        }

        String result = client.getInstance().requestSignup(username, password);
        if ("SIGNUP_OK".equals(result)) {
            if (statusLabel != null) {
                statusLabel.setText("Signup complete, now login");
            }
        } else {
            if (statusLabel != null) {
                statusLabel.setText("Signup failed (user exists?)");
            }
        }
    }
}
