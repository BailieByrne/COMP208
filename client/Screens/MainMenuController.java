import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

public class MainMenuController {
    @FXML private Label welcomeLabel;
    @FXML private Label statusLabel;
    @FXML private Button resumeGameButton;
    @FXML private Button startCycle1Button;
    @FXML private ComboBox<Integer> difficultyCombo;

    private int selectedDifficulty = 2;  // Default
    private boolean hasSavedGame = false;
    private boolean hasActiveGame = false;
    private String savedGameStatus = "NONE";
    private int savedGameDay = 1;
    private int savedGameDifficulty = 2;

    @FXML
    private void initialize() {
        // keep selected difficulty synced to combo
        if (difficultyCombo != null) {
            difficultyCombo.getItems().addAll(1, 2, 3, 4);
            difficultyCombo.setValue(selectedDifficulty);
            difficultyCombo.setOnAction(e -> selectedDifficulty = difficultyCombo.getValue());
        }
        refreshSavedGameUi();
    }

    public void setUser(String username) {
        if (welcomeLabel != null) {
            welcomeLabel.setText("Main Menu - " + username);
        }
    }

    public void applySavedGameState(boolean savedGame, boolean activeGame, String status, int day, int difficulty) {
        hasSavedGame = savedGame;
        hasActiveGame = activeGame;
        savedGameStatus = status == null ? "NONE" : status.trim().toUpperCase();
        if (savedGameStatus.isBlank()) {
            savedGameStatus = savedGame ? "COMPLETED" : "NONE";
        }
        savedGameDay = Math.max(1, day);
        savedGameDifficulty = difficulty < 1 ? 2 : difficulty;
        selectedDifficulty = savedGameDifficulty;

        if (difficultyCombo != null) {
            difficultyCombo.setValue(selectedDifficulty);
        }

        refreshSavedGameUi();
    }

    private void refreshSavedGameUi() {
        if (startCycle1Button != null) {
            startCycle1Button.setText(hasSavedGame ? "Start New Game" : "Start Game");
        }

        if (resumeGameButton != null) {
            resumeGameButton.setManaged(hasActiveGame);
            resumeGameButton.setVisible(hasActiveGame);
        }

        if (statusLabel != null) {
            if (hasActiveGame) {
                statusLabel.setText("Active game found: Day " + savedGameDay + " (" + savedGameStatus + ")");
            } else if (hasSavedGame) {
                statusLabel.setText("Saved game found: Day " + savedGameDay + " (" + savedGameStatus + ")");
            } else {
                statusLabel.setText("Ready");
            }
        }
    }

    public int getSelectedDifficulty() {
        return selectedDifficulty;
    }

    @FXML
    private void onStartCycle1() {
        // server will confirm with GAME_START packet
        client.getInstance().startGame(selectedDifficulty);
        if (statusLabel != null) {
            statusLabel.setText("Starting game...");
        }
    }

    @FXML
    private void onResumeGame() {
        if (!hasActiveGame) {
            if (statusLabel != null) {
                statusLabel.setText("No active game to resume");
            }
            return;
        }
        client.getInstance().resumeGame();
        if (statusLabel != null) {
            statusLabel.setText("Resuming saved game...");
        }
    }

    public void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text);
        }
    }

    @FXML
    private void onTriggerCoffeePowerup() {
        client.getInstance().requestPowerup("coffee");
        if (statusLabel != null) {
            statusLabel.setText("Requested coffee powerup");
        }
    }

    @FXML
    private void onExit() {
        client.getInstance().shutdownClient();
    }
}
