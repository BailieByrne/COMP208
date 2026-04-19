import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
//Neccesary imports for JavaFX and networking


//Client class extends Application from JavaFX library
public class client extends Application {
    //Main priv attrubutes for the stage and client class et
    private static client instance;
    private Stage primaryStage;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Parent currentScreen;
    private Object currentController;
    private volatile boolean connected = false;
    private boolean hasActiveGame = false;
    private int activeGameDifficulty = 1;
    private int activeGameDay = 1;
    private boolean loginPending = false;

    @Override
    public void start(Stage stage) {
        instance = this;
        primaryStage = stage;
        primaryStage.setTitle("Project Belford");

        // Connect to server
        connectToServer();

        // Show login screen
        showLoginScreen();
    }

    /**
     * Safe method to open a screen , this one will close current screen and shows new one
     * Prevents memory leaks and ensures fullscreen display
     */
    private synchronized void openScreen(String fxmlFileName, String screenName) {
        Platform.runLater(() -> {
            try {
                // Close previous screen to free resources
                if (currentScreen != null) {
                    currentScreen = null;
                }
                currentController = null;

                // Load new FXML file
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/Assets/" + fxmlFileName));
                Parent newScreen = loader.load();
                currentController = loader.getController();

                // Create scene and attach to stage
                Scene scene = new Scene(newScreen);
                primaryStage.setScene(scene);

                // Make fullscreen and bring to top
                primaryStage.setFullScreen(true);
                primaryStage.setAlwaysOnTop(false);
                primaryStage.setFullScreenExitHint("");
                primaryStage.show();
                primaryStage.toFront();
                primaryStage.requestFocus();

                //Update the current screen
                currentScreen = newScreen;
                System.out.println("Screen loaded: " + screenName);
            } catch (Exception e) {
                System.err.println("Failed to load screen: " + screenName);
                e.printStackTrace();
            }
        });
    }

    /**
     * Connect to server at localhost:5000 and start packet listener
     */
    private void connectToServer() {
        new Thread(() -> {
            try {
                //As of now use localhost, plans to move this to online hosting in the future
                socket = new Socket("localhost", 5000);
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                connected = true;
                System.out.println("Connected to server at localhost:5000");

                // Start listening for packets
                listenForPackets();
            } catch (IOException e) {
                System.err.println("Failed to connect to server: " + e.getMessage());
                Platform.runLater(() -> showErrorDialog("Cannot connect to server at localhost:5000"));
            }
        }).start();
    }

    /**
     * Listen for packets from server and handle them
     */
    private void listenForPackets() {
        try {
            String packet;
            while ((packet = in.readLine()) != null) {
                handlePacket(packet);
            }
        } catch (IOException e) {
            System.err.println("Connection lost: " + e.getMessage());
            Platform.runLater(() -> showErrorDialog("Lost connection to server"));
        } finally {
            connected = false;
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Parse and handle packets from server
     * Packet format: TYPE|data1|data2|...
     * Packets are disected by | and the first arg determines the type, the rest are agrv similar
     */
    private void handlePacket(String packet) {
         if(packet == null || packet.isEmpty()){
            return;
        }

        String[] parts = packet.split("\\|");

        //validating checksum
        if(parts.length > 1){
            try{
                int rcvdCheck = Integer.parseInt(parts[parts.length - 1]);

                //rebuild packet wout checksum
                String[] fracParts = new String[parts.length - 1];
                System.arraycopy(parts, 0, fracParts, 0, parts.length - 1);
                String data = String.join("|", fracParts);

                //verify checksum
                int calculatedCheckSum = calculateCheckSum(data);
                if(rcvdCheck != calculatedCheckSum){
                    System.err.println("Checksum mismatch: packet corrupted");
                    sendPacket("ERROR|CHECKSUM_FAILED");
                    return;
                }

                parts = fracParts;
            }
            catch(NumberFormatException e){
                System.out.println("No checksum found -- continuing");
            }
        }
        String packetType = parts[0];


        /**
         * Big switch case wrapped in a try block to catch errors or malformed packets.
         */
        try {
            switch (packetType) {
                case "LOGIN_SUCCESS":
                    handleLoginSuccess(parts);
                    break;
                case "LOGIN_FAILED":
                    handleLoginFailed(parts);
                    break;
                case "SIGNUP_SUCCESS":
                    handleSignupSuccess(parts);
                    break;
                case "SIGNUP_FAILED":
                    handleSignupFailed(parts);
                    break;
                case "ACTIVE_GAMES":
                    handleActiveGames(parts);
                    break;
                case "GAME_INFO":
                    handleGameInfo(parts);
                    break;
                case "GAME_START":
                    handleGameStart(parts);
                    break;
                case "GAME_RESUMED":
                    handleGameResumed(parts);
                    break;
                case "PHASE":
                    handlePhaseChange(parts);
                    break;
                case "PRICE_UPDATE":
                    handlePriceUpdate(parts);
                    break;
                case "PORTFOLIO_UPDATE":
                    handlePortfolioUpdate(parts);
                    break;
                case "GAME_DATA":
                    handleGameData(parts);
                    break;
                case "GAME_END":
                    handleGameEnd(parts);
                    break;
                case "GAME_OVER":
                    handleGameOver(parts);
                    break;
                case "ERROR":
                    handleError(parts);
                    break;
                default:
                    System.out.println("Unknown packet type: " + packetType);
            }
        } catch (Exception e) {
            System.err.println("Error handling packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Self Explanatory, handles the successful login and shows main menu, and wairs for active gamne
     * @param parts
     */
    private void handleLoginSuccess(String[] parts) {
        System.out.println("Login successful for user: " + (parts.length > 1 ? parts[1] : ""));
        // Don't show main menu yet - wait for ACTIVE_GAMES/GAME_INFO packets
        loginPending = true;
        // If no active games are found within a timeout, show the menu anyway
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Wait up to 1 second for game info
                if (loginPending) {
                    loginPending = false;
                    showMainMenuAfterLogin();
                }
            } catch (InterruptedException e) {
                // Ignore
            }
        }).start();
        //Try the thread, error isnt caught so arrises as an error (intended)
    }

    /**
     * Handles failed login attempts, shows error message on login screen
     * @param parts
     * Doesnt retur anything
     */
    private void handleLoginFailed(String[] parts) {
        String reason = parts.length > 1 ? parts[1] : "Invalid credentials";
        System.out.println("Login failed: " + reason);
        Platform.runLater(() -> showLoginError(reason));
    }

    /**#
     * Handles successful signup shows main menu immediately since no active game can exist for new user
      * @param parts
     */
    private void handleSignupSuccess(String[] parts) {
        System.out.println("Signup successful");
        Platform.runLater(this::showMainMenuScreen);
    }

    /**
     * Handles failed signup attempts shows error message on login screen given the reason
     * @param parts
     */
    private void handleSignupFailed(String[] parts) {
        String reason = parts.length > 1 ? parts[1] : "Signup failed";
        System.out.println("Signup failed: " + reason);
        Platform.runLater(() -> showLoginError(reason));
    }

    /**
     * Server checks for active games on login
     * -If detected the server will send ACTIVE_GAMES|count followed by GAME_INFO packets for each active game.
     * -If no active games are detected the server will send ACTIVE_GAMES|0 and the client should show the main menu immediately.
     * The main menu adpats dependant on a current game saved or no with RESUME/START options
     * @param parts
     */
    private void handleActiveGames(String[] parts) {
        // Format: ACTIVE_GAMES|count
        int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        System.out.println("Active games found: " + count);
        if (count == 0) {
            // No active games - show menu immediately
            if (loginPending) {
                loginPending = false;
                showMainMenuAfterLogin();
            }
        }
        // If count > 0, wait for GAME_INFO packets
    }



/**
 * Handles game info packets sent by the server for each active game detected on login
 * Sends game info like difficulty and current day, this is used to update the main menu with the correct info for resuming games
 * @param parts
 */
    private void handleGameInfo(String[] parts) {
        // Format: GAME_INFO|game_id|difficulty|current_day|player_cash|ai_cash
        if (parts.length >= 4) {
            try {
                hasActiveGame = true;
                activeGameDifficulty = Integer.parseInt(parts[2]);
                activeGameDay = Integer.parseInt(parts[3]);
                System.out.println("Game info: Day " + activeGameDay + ", Difficulty " + activeGameDifficulty);
                
                // After receiving game info, show the main menu
                if (loginPending) {
                    loginPending = false;
                    showMainMenuAfterLogin();
                }
            } catch (NumberFormatException e) {
                System.err.println("Error parsing game info: " + e.getMessage());
            }
        }
    }


    /**
     * IMPORTANT
     * Handles the start of thr new game, resets active game flags and vars for the new game
     * @param parts
     */
    private void handleGameStart(String[] parts) {
        System.out.println("Game started - showing Cycle 1");
        // Clear active game flags since a new game is starting
        hasActiveGame = false;
        activeGameDay = 1;
        activeGameDifficulty = 1;
        Platform.runLater(() -> openScreen("COMP208GameUI.fxml", "Game UI - Cycle 1"));
    }

    /**
     * Handles resuming an active game sets active game flags and vars for the resumed game and opens the correct screen for the current day of the game.
     * @param parts
     */
    private void handleGameResumed(String[] parts) {
        if (parts.length < 4) return;
        try {
            int gameId = Integer.parseInt(parts[1]);
            int difficulty = Integer.parseInt(parts[2]);
            int currentDay = Integer.parseInt(parts[3]);
            System.out.println("Game resumed - Game ID: " + gameId + ", Day: " + currentDay + ", Difficulty: " + difficulty);
            
            // Clear active game flags since we're now in the game
            hasActiveGame = false;
            activeGameDay = currentDay;
            activeGameDifficulty = difficulty;
            
            Platform.runLater(() -> openScreen("COMP208GameUI.fxml", "Game UI - Cycle 1"));
        } catch (NumberFormatException e) {
            System.err.println("Error parsing game resumed packet: " + e.getMessage());
        }
    }

    //These ocur between cycles and thus self exmplanatory, just opens the correct screen for the current cycle of the game
    private void handlePhaseChange(String[] parts) {
        if (parts.length < 2) return;
        String phase = parts[1];
        System.out.println("Phase change: " + phase);

        switch (phase) {
            case "CYCLE_1":
                Platform.runLater(() -> openScreen("COMP208GameUI.fxml", "Game UI - Cycle 1"));
                break;
            case "CYCLE_2":
                Platform.runLater(() -> openScreen("Cycle2Placeholder.fxml", "Game UI - Cycle 2"));
                break;
            default:
                System.out.println("Unknown phase: " + phase);
        }
    }

    /**
     * Handles price updates from the server updates the game UI with the new price data
     * @param parts
     */
    private void handlePriceUpdate(String[] parts) {
        if (parts.length < 4) return;
        String ticker = parts[1];
        double price = Double.parseDouble(parts[2]);
        int point = Integer.parseInt(parts[3]);
        
        // Update the current GameUIController if it's active
        if (currentController instanceof GameUIController) {
            GameUIController controller = (GameUIController) currentController;
            Platform.runLater(() -> controller.updatePriceData(ticker, price, point));
        }
    }

    /**
     * This is called after BUYS/SELLS to update the UI with the msot up to date data
     * @param parts
     */    private void handlePortfolioUpdate(String[] parts) {
        // Format: PORTFOLIO_UPDATE|cash|ticker1:qty1:price1|ticker2:qty2:price2|...
        if (parts.length < 2) return;
        String cash = parts[1];
        
        //Consider moving this List instance into global scope.
        java.util.List<GameUIController.PortfolioEntry> holdings = new java.util.ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            String[] entry = parts[i].split(":");
            if (entry.length == 3) {
                try {
                    String ticker = entry[0];
                    int quantity = Integer.parseInt(entry[1]);
                    double price = Double.parseDouble(entry[2]);
                    holdings.add(new GameUIController.PortfolioEntry(ticker, quantity, price));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        
        // Update the GameUIController if it's active
        if (currentController instanceof GameUIController) {
            GameUIController controller = (GameUIController) currentController;
            Platform.runLater(() -> controller.updatePortfolio(cash, holdings));
        }
    }

    private void handleGameData(String[] parts) {
        // Handle game data updates (prices, portfolio, etc.)
        System.out.println("Received game data update");
    }

    //Handles the end game
    //RunLater is used to hook into the JAVAFX thread for auto cleanup.
    private void handleGameEnd(String[] parts) {
        System.out.println("Game ended");
        Platform.runLater(this::showMainMenuScreen);
    }

    private void handleGameOver(String[] parts) {
        // Format: GAME_OVER|result|playerNetWorth|aiNetWorth
        String result = parts.length > 1 ? parts[1] : "UNKNOWN";
        String playerNetWorth = parts.length > 2 ? parts[2] : "0";
        String aiNetWorth = parts.length > 3 ? parts[3] : "0";
        
        System.out.println("Game Over - Result: " + result + " | Player: £" + playerNetWorth + " | AI: £" + aiNetWorth);
        Platform.runLater(() -> showGameOverDialog(result, playerNetWorth, aiNetWorth));
    }

    private void handleError(String[] parts) {
        String errorMsg = parts.length > 1 ? parts[1] : "Unknown error";
        System.err.println("Server error: " + errorMsg);
        Platform.runLater(() -> showErrorDialog(errorMsg));
    }

    // ==================== Public API Methods ====================
    //These should be mostly self explanatory.

    public static client getInstance() {
        return instance;
    }

    public void requestLogin(String username, String password) {
        if (!connected) {
            showErrorDialog("Not connected to server");
            return;
        }
        sendPacket("LOGIN|" + username + "|" + password);
    }

    public void requestSignup(String username, String password) {
        if (!connected) {
            showErrorDialog("Not connected to server");
            return;
        }
        sendPacket("SIGNUP|" + username + "|" + password);
    }

    public void startGame(int difficulty) {
        if (!connected) {
            showErrorDialog("Not connected to server");
            return;
        }
        sendPacket("START_GAME|" + difficulty);
    }

    public void resumeGame() {
        if (!connected) {
            showErrorDialog("Not connected to server");
            return;
        }
        sendPacket("RESUME_GAME");
    }

    public void showMainMenuScreen() {
        openScreen("MainMenu.fxml", "Main Menu");
    }
    
    private void showMainMenuAfterLogin() {
        openScreen("MainMenu.fxml", "Main Menu");
        
        // Update main menu with active game info
        Platform.runLater(() -> {
            if (currentController instanceof MainMenuController) {
                MainMenuController controller = (MainMenuController) currentController;
                controller.applySavedGameState(false, hasActiveGame, null, activeGameDay, activeGameDifficulty);
            }
        });
    }

    public void showLoginScreen() {
        openScreen("loginmenu.fxml", "Login");
    }

    public void requestPowerup(String powerupName) {
        if (!connected) {
            showErrorDialog("Not connected to server");
            return;
        }
        sendPacket("POWERUP|" + powerupName);
    }

    public void shutdownClient() {
        System.out.println("Shutting down client...");
        Platform.exit();
    }

    public void requestBuy(String ticker, int quantity, double price) {
        if (!connected) {
            showErrorDialog("Not connected to server");
            return;
        }
        sendPacket("BUY|" + ticker + "|" + quantity + "|" + price);
    }

    public void requestSell(String ticker, int quantity, double price) {
        if (!connected) {
            showErrorDialog("Not connected to server");
            return;
        }
        sendPacket("SELL|" + ticker + "|" + quantity + "|" + price);
    }

    public void requestSellAll(String ticker, double price) {
        if (!connected) {
            showErrorDialog("Not connected to server");
            return;
        }
        sendPacket("SELL_ALL|" + ticker + "|" + price);
    }
    // ==================== Helper Methods ====================
    // I'll work on sending the packets - radu
    
    private int calculateCheckSum(String data){
        int sum = 0;
        for(char c : data.toCharArray()){
            sum += (int) c;
        }
        return sum;
    }

    private void sendPacket(String packet) {
        /**String -> bytes[] 
         * Turn to bytes[]
         * - Add a checksum
         * - then send it
         * USe UTF8 for this @LEO + @RADU
         */
        if (out != null && connected) {
            try{
                int checksum = calculateCheckSum(packet);

                String pktCheck = packet + "|" + checksum;

                out.println(pktCheck);
                System.out.println("Sent packet: " + packet + "checksum: " + checksum);
            }
            catch (Exception e){
                System.err.println("Failed to send packet: " + e.getMessage());
                connected = false;
            }
        }
        else{
            showErrorDialog("Cannot send packet - not connected to the server.");
        }
    }

    private void showLoginError(String message) {
        Platform.runLater(() -> {
            // Update login screen status label using the stored controller reference
            if (currentController instanceof LoginController) {
                LoginController controller = (LoginController) currentController;
                controller.setStatus("Error: " + message);
                System.out.println("Login error displayed: " + message);
            } else {
                System.err.println("Could not update login error, controller not found");
            }
        });
    }

    private void showErrorDialog(String message) {
        System.err.println("Error: " + message);
        // Can be extended to show actual dialog
    }

    private void showGameOverDialog(String result, String playerNetWorth, String aiNetWorth) {
        // Simple console output for now - can be extended to show actual dialog with results
        System.out.println("====== GAME OVER ======");
        System.out.println("Result: " + result);
        System.out.println("Player Net Worth: £" + playerNetWorth);
        System.out.println("AI Net Worth: £" + aiNetWorth);
        System.out.println("=======================");
        
        // Return to main menu after showing game over
        Platform.runLater(this::showMainMenuScreen);
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Shutting down client");
        if (socket != null) {
            socket.close();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
