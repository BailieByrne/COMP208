import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class Cycle2Controller {
    @FXML private Label messageLabel;
    @FXML private Label remainingLabel;

    public void updateCycle2(String message, long remainingMs) {
        // simple placeholder status binding
        if (messageLabel != null) {
            messageLabel.setText(message);
        }
        if (remainingLabel != null) {
            remainingLabel.setText("Remaining: " + (remainingMs / 1000) + "s");
        }
    }

    @FXML
    private void onBackToMenu() {
        // quick return for placeholder
        client.getInstance().showMainMenuScreen();
    }
}
