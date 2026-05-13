import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
//Neccesary imports for embedding swing into JavaFX and contolling threads


//Cycle 2 Game Controller handles the tiled RPG game phase embedded in JavaFX
public class Cycle2GameController {
    @FXML private BorderPane rootPane;
    @FXML private SwingNode gameContainer;
    
    private GameTest gamePanel;
    private Timer timeoutTimer;
    //60 second duration for the cycle 2 tiled game phase b4 returning to trading
    private static final int CYCLE_2_DURATION_MS = 60000;

    /**
     * Initialize the Cycle 2 Game Controller, this sets up the swing game panel and embeds it into the swingnode
     * Also starts the 60 second timer for auto close and return to cycle 1
     * This is called automatically when the FXML loads
     */
    @FXML
    public void initialize() {
        System.out.println("Initializing Cycle 2 Game Controler...");
        
        // Create Swing game panel in Swing EDT thread neccesary for AWT/Swing compoents
        SwingUtilities.invokeLater(() -> {
            try {
                gamePanel = new GameTest();
                gamePanel.setFocusable(true);
                gamePanel.requestFocusInWindow();
                
                // switch back to FX thread to add the panel into the SwingNode container
                Platform.runLater(() -> {
                    gameContainer.setContent(gamePanel);
                    gameContainer.requestFocus();
                });
                
                System.out.println("Game panel created and embeded successfully");
            } catch (Exception e) {
                System.err.println("Error creating game panel: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        // Set up 60 second timeout fter this time we close the game and return to cycle 1
        timeoutTimer = new Timer(CYCLE_2_DURATION_MS, e -> endCycle2());
        timeoutTimer.setRepeats(false);
        timeoutTimer.start();
    }

    /**
     * Called when 60 seconds elapse or game is manually closed
     * Cleans up the swing game resources and transitions back to Cycle 1 trading UI
     * This ensures the player returns to continue trading on next day
     */
    private void endCycle2() {      
        // Stop the game loop and clean up swing components.
        if (gamePanel != null) {
            SwingUtilities.invokeLater(() -> {
                gamePanel.stopGame();
            });
        }
        
        // Stop the timeout timer if its still running.
        if (timeoutTimer != null) {
            timeoutTimer.stop();
        }
        
        // Return to Cycle 1 GameUI screen for continued trading
        Platform.runLater(() -> {
            client.getInstance().showGameUI();
        });
    }
}
