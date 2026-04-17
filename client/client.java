import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

//Main Client app
public class client extends Application {
    private static client instance;

    private Stage primaryStage;
    private Thread clientThread;
    private volatile boolean clientRunning = false;

    private String username = "guest";
    private String password = "password";
    private String jwtToken = "header.payload.signature";
    private boolean hasSavedGame = false;
    private boolean hasActiveGame = false;
    private String savedGameStatus = "NONE";
    private int savedGameDay = 1;
    private int savedGameDifficulty = 2;
    //^Default values for saved game state until login response is processed

    private GameUIController gameUIController;
    private MainMenuController mainMenuController;
    private Cycle2Controller cycle2Controller;

    public static client getInstance() {
        return instance;
    }
    
    //Start of JavaFX application lifecycle
    @Override
    public void start(Stage stage) throws Exception {
        instance = this;
        primaryStage = stage;
        primaryStage.setFullScreenExitHint("");
        primaryStage.setFullScreen(true);
        showLoginScreen();

        stage.setOnCloseRequest(event -> {
            shutdownClient();
        });
    }

    //Shows the loginFXML
    private void showLoginScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Assets/loginmenu.fxml"));
            Parent root = loader.load();
            primaryStage.setTitle("COMP208 - Login");
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //MainScreen
    public void showMainMenuScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Assets/MainMenu.fxml"));
            Parent root = loader.load();
            mainMenuController = loader.getController();
            if (mainMenuController != null) {
                mainMenuController.setUser(username);
                mainMenuController.applySavedGameState(hasSavedGame, hasActiveGame, savedGameStatus, savedGameDay, savedGameDifficulty);
                //take state from server ^ and apply to main menu for display and interaction purposes
            }
            primaryStage.setTitle("Project Belford");
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showCycle1Screen() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Assets/COMP208GameUI.fxml"));
            Parent root = loader.load();
            gameUIController = loader.getController();
            if (gameUIController != null) {
                gameUIController.setConnectionStatus("Connecting to server...");
            }
            primaryStage.setTitle("COMP208 - Cycle 1");
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
            startClientThread();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //PLACEHOLDER CYCLE 2 SCREEN, REPLACE WITH ACTUAL CYCLE 2 UI
    public void showCycle2Screen() {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(new File("client/Assets/Cycle2Placeholder.fxml").toURI().toURL());
                Parent root = loader.load();
                cycle2Controller = loader.getController();
                primaryStage.setTitle("COMP208 - Cycle 2");
                primaryStage.setScene(new Scene(root));
                primaryStage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void onLoginSuccess(String user, String pass) {
        username = user;
        password = pass;
        showMainMenuScreen();
    }

    public void applySavedGameState(boolean savedGame, boolean activeGame, String status, int day, int difficulty) {
        hasSavedGame = savedGame;
        hasActiveGame = activeGame;
        savedGameStatus = (status == null || status.isBlank()) ? (savedGame ? "COMPLETED" : "NONE") : status.trim().toUpperCase();
        savedGameDay = Math.max(1, day);
        savedGameDifficulty = difficulty < 1 ? 2 : difficulty;

        if (mainMenuController != null) {
            mainMenuController.applySavedGameState(hasSavedGame, hasActiveGame, savedGameStatus, savedGameDay, savedGameDifficulty);
        }
    }

    //JWT for authentication, not secure so will chnage later
    private String buildJwtToken(String user) {
        // fake jwt shape for dev auth path
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("header".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString((user + ":" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("signature".getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + signature;
    }


    public String requestLogin(String user, String pass) {
        // short one-shot login socket before game socket
        String jwt = buildJwtToken(user);
        try (Socket socket = new Socket("127.0.0.1", 5000);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("LOGIN:" + user + ":" + pass + ":" + jwt);
            String line = in.readLine();
            if (line != null && line.contains("\"TYPE\":\"LOGIN_OK\"")) {
                hasSavedGame = extractBoolean(line, "HAS_SAVED_GAME");
                hasActiveGame = extractBoolean(line, "HAS_ACTIVE_GAME");
                savedGameStatus = extractString(line, "GAME_STATUS");
                savedGameDay = extractInt(line, "DAY");
                savedGameDifficulty = extractInt(line, "DIFFICULTY");
                if (savedGameStatus.isBlank()) {
                    savedGameStatus = hasSavedGame ? "COMPLETED" : "NONE";
                } else {
                    savedGameStatus = savedGameStatus.toUpperCase();
                }
                if (savedGameDay < 1) {
                    savedGameDay = 1;
                }
                if (savedGameDifficulty < 1) {
                    savedGameDifficulty = 2;
                }
                username = user;
                password = pass;
                jwtToken = jwt;
                return "LOGIN_OK";
            }
            return "LOGIN_FAIL";
        } catch (IOException e) {
            return "LOGIN_FAIL";
        }
    }

    public String requestSignup(String user, String pass) {
        // one-shot signup request
        String jwt = buildJwtToken(user);
        try (Socket socket = new Socket("127.0.0.1", 5000);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("SIGNUP:" + user + ":" + pass + ":" + jwt);
            String line = in.readLine();
            if (line != null && line.contains("\"TYPE\":\"SIGNUP_OK\"")) {
                return "SIGNUP_OK";
            }
            return "SIGNUP_FAIL";
        } catch (IOException e) {
            return "SIGNUP_FAIL";
        }
    }

    //Begin client thread and send start game command to server, server will respond with GAME_START packet if successful, or START_GAME_DENY if not (with reason)
    public void startGame(int difficulty) {
        // ensure connector exists before sending command
        startClientThread();
        ClientConnector.sendCommand("START_GAME:" + difficulty + ":" + jwtToken);
    }

    //Jut resuems active game if found
    public void resumeGame() {
        startClientThread();
        ClientConnector.sendCommand("RESUME_GAME:" + jwtToken);
    }

    private void startClientThread() {
        if (clientThread != null && clientThread.isAlive()) {
            return;
        }
        clientRunning = true;
        clientThread = new Thread(new ClientConnector());
        clientThread.setDaemon(true);
        clientThread.start();
    }
    
    private void stopClientThread() {
        clientRunning = false;
        if (clientThread != null && clientThread.isAlive()) {
            clientThread.interrupt();
        }
    }

    //Here we can send powerup requests with our token for valedation
    public void requestPowerup(String name) {
        ClientConnector.sendCommand("POWERUP:" + name + ":" + jwtToken);
    }

    public void shutdownClient() {
        stopClientThread();
        Platform.exit();
    }


    //Extractors to read JSON withouth a library, just using regex for simplicity since we control the format and only need a few fields, not ideal but works for now
    /**
     * TODO: replace with proper JSON parsing if time allows, this is very rudimentary and only for demo purposes, not production code
     * @param message
     * @param key
     * @return
     */
    private static double extractDouble(String message, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static int extractInt(String message, String key) {
        double value = extractDouble(message, key);
        if (Double.isNaN(value)) {
            return -1;
        }
        return (int) value;
    }

    private static boolean extractBoolean(String message, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return false;
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static String extractString(String message, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    //Connector class handles server commms
    private static class ClientConnector implements Runnable {
        private static volatile PrintWriter sharedOut;

        //Queue for sequential commands like BUY SELL and powerups 
        private static final Queue<String> pendingCommands = new ConcurrentLinkedQueue<>();

        public static void sendCommand(String cmd) {
            PrintWriter out = sharedOut;
            if (out != null) {
                out.println(cmd);
                out.flush();
            } else {
                pendingCommands.offer(cmd);
            }
        }

        private static void flushPendingCommands() {
            PrintWriter out = sharedOut;
            if (out == null) return;

            String cmd;
            while ((cmd = pendingCommands.poll()) != null) {
                out.println(cmd);
                out.flush();
            }
        }


        @Override
        public void run() {
            // persistent game socket loop
            while (!Thread.currentThread().isInterrupted() && client.getInstance().clientRunning) {
                try (Socket socket = new Socket("127.0.0.1", 5000);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    sharedOut = out;
                    out.println("LOGIN:" + client.getInstance().username + ":" + client.getInstance().password + ":" + client.getInstance().jwtToken);
                    flushPendingCommands();

                    //JavaFX UI updates must be run on the Application thread, so we use Platform.runLater to update the UI from this background thread when we receive packets from the server
                    Platform.runLater(() -> {
                        if (client.getInstance().gameUIController != null) {
                            client.getInstance().gameUIController.setConnectionStatus("Connected to server");
                        }
                        if (client.getInstance().mainMenuController != null) {
                            client app = client.getInstance();
                            if (app.hasSavedGame || app.hasActiveGame) {
                                app.mainMenuController.applySavedGameState(app.hasSavedGame, app.hasActiveGame, app.savedGameStatus, app.savedGameDay, app.savedGameDifficulty);
                            } else {
                                app.mainMenuController.setStatus("Connected. Waiting for start...");
                            }
                        }
                    });

                    String line;
                    while (!Thread.currentThread().isInterrupted() && (line = in.readLine()) != null) {
                        final String packet = line;
                        //AGAIn tcp decoding needed here packet will ahev checksum and be bytes, need to decode and verify before processing, for now we assume all packets are well formed and valid for simplicity
                        Platform.runLater(() -> {
                            // route packet by TYPE only
                            //Here is where we utilise all server packet logic
                            client app = client.getInstance();
                            String packetType = extractString(packet, "TYPE");
                            if ("GAME_COMPLETE".equals(packetType)) {
                                app.applySavedGameState(true, false, extractString(packet, "GAME_STATUS"), extractInt(packet, "DAY"), app.savedGameDifficulty);
                                if (app.gameUIController != null) {
                                    app.gameUIController.setConnectionStatus("Game complete. Start a new game to play again.");
                                }
                                app.showMainMenuScreen();
                                return;
                            }
                            if ("GAME_START".equals(packetType)) {
                                app.showCycle1Screen();
                                return;
                            }
                            if ("START_GAME_DENY".equals(packetType)) {
                                if (app.mainMenuController != null) {
                                    app.mainMenuController.setStatus("Start denied: " + extractString(packet, "REASON"));
                                }
                                return;
                            }
                            if ("LOGIN_FAIL".equals(packetType)) {
                                if (app.mainMenuController != null) {
                                    app.mainMenuController.setStatus("Connection login failed");
                                }
                                return;
                            }
                            if ("PHASE".equals(packetType) && packet.contains("\"CYCLE\":\"CYCLE2\"")) {
                                app.showCycle2Screen();
                                return;
                            }
                            if ("PHASE".equals(packetType) && packet.contains("\"CYCLE\":\"CYCLE1\"")) {
                                app.showCycle1Screen();
                                return;
                            }
                            if ("CYCLE2".equals(packetType)) {
                                long remaining = (long) extractDouble(packet, "REMAINING_MS");
                                if (app.cycle2Controller != null) {
                                    app.cycle2Controller.updateCycle2("Java Tiled world placeholder running...", remaining);
                                }
                                return;
                            }
                            if ("PRICE".equals(packetType) && app.gameUIController != null) {
                                String ticker = extractString(packet, "TICKER");
                                double price = extractDouble(packet, "PRICE");
                                int time = extractInt(packet, "TIME");
                                app.gameUIController.handlePricePacket(ticker, price, time, packet);
                                return;
                            }
                            if ("AI_PRICE".equals(packetType) && app.gameUIController != null) {
                                String ticker = extractString(packet, "TICKER");
                                double price = extractDouble(packet, "PRICE");
                                int time = extractInt(packet, "TIME");
                                app.gameUIController.handleAIPacket(ticker, price, time, packet);
                            }
                        });
                    }
                } catch (IOException e) {
                    sharedOut = null;
                    //Runlayer again to update UI on disconnect and attempt reconnect after a short delay, this will loop until the client is closed or connection is re-established
                    Platform.runLater(() -> {
                        if (client.getInstance().gameUIController != null) {
                            client.getInstance().gameUIController.setConnectionStatus("Waiting for server...");
                        }
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
