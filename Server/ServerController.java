// ServerController.java  main server loop, client handling, game state management
//Move these imports
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//Extends runnable for thread handling
public class ServerController implements Runnable {
    private static final int PORT = 5000;
    private static final String HOST = "127.0.0.1";
    private static final int LOBBY_SECONDS = 5;
    private static final long CYCLE2_DURATION_MS = 15_000;
    private static final int DEFAULT_STEP = 5;
    private static final int POWERUP_STEP = 1;

    //Builds the CPP if it cant find the EXE, need to add a flag here for mac compat in the future
    private static final String BUILD_EXECUTABLE = ".\\build\\bin\\Release\\stock_sim.exe";
    private static final String BUILD_FALLBACK = "cmake --build build --config Release";

    //Data Strcutures
    private final List<ClientSession> sessions = new ArrayList<>();
    private final Map<Integer, ClientState> clientStates = new HashMap<>();
    private final DBManager dbManager = new DBManager("DB/main.db");
    private final AuthService authService = new AuthService(dbManager);

    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private volatile boolean lobbyStarted = false;
    private volatile boolean marketLoopStarted = false;
    private volatile boolean gameSessionRunning = false;
    private int nextClientId = 0;
    private ScheduledExecutorService gameScheduler;

    // game state bits uses defaults
    private int currentDay = 1;  // 1-5
    private int currentDayLoopCount = 0;  // 0-3 for cycles, then cycle1 on day 5
    private String currentTicker = "AAPL";
    private final List<String> availableTickers = new ArrayList<>();
    private DBManager.GameState gameState = null;
    private CycleOneEngine cycleOne;
    private CycleTwoEngine cycleTwo = new CycleTwoEngine(CYCLE2_DURATION_MS);
    private String activeCycle = "";  // Empty until day starts
    private double cycle1HighPrice = 0;
    private double cycle1LowPrice = Double.MAX_VALUE;
    private double cycle1OpenPrice = 0;
    private double cycle1ClosePrice = 0;

    //Logging with timestamps
    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + timestamp + "] " + message);
    }

    //Main RUN loop for server, accepts clients and starts lobby/game loops
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(HOST));
            log("Server listening on " + HOST + ":" + PORT);

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    ClientSession session = new ClientSession(nextClientId++, socket);
                    clientStates.put(session.clientId, new ClientState(session.clientId));

                    synchronized (sessions) {
                        sessions.add(session);
                    }

                    log("CLIENT CONNECTED: id=" + session.clientId + " " + session.clientIp + ":" + socket.getPort());

                    Thread clientReader = new Thread(() -> handleClientMessages(session));
                    clientReader.setDaemon(true);
                    clientReader.start();

                } catch (SocketException e) {
                    if (running) {
                        log("Socket error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log("Server error: " + e.getMessage());
        }
    }

    private void startLobbyIfNeeded() {
        if (lobbyStarted) {
            return;
        }
        lobbyStarted = true;

        Thread lobbyThread = new Thread(() -> {
            try {
                log("Lobby opened. Starting game in " + LOBBY_SECONDS + " seconds...");
                Thread.sleep(LOBBY_SECONDS * 1000L);
                
                // Initialize game from DB or create new
                if (!initializeGame()) {
                    log("ERROR: Failed to initialize game");
                    return;
                }
                
                startFiveDayGame();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        lobbyThread.setDaemon(true);
        lobbyThread.start();
    }

    // init game from db
    private boolean initializeGame() {
        // temp user for boot
        String testUser = "player1";
        gameState = dbManager.getGameState(testUser);
        
        if (gameState == null) {
            int difficulty = 2;  // Default difficulty
            gameState = dbManager.createOrResetGame(testUser, difficulty);
            if (gameState == null) {
                log("WARNING: DB unavailable, using dev mode defaults");
                gameState = new DBManager.GameState(1, testUser, 1, difficulty, 10000.0, 0, 0, 0, 0, 0, 0.0);
            } else {
                log("Created new game for user=" + testUser + " difficulty=" + difficulty);
            }
        } else {
            log("Loaded stored game for user=" + testUser + " day=" + gameState.currentDay);
        }

        currentDay = gameState.currentDay;
        currentTicker = dbManager.getTickerForDay(currentDay);
        if (currentTicker == null) currentTicker = "AAPL";
        refreshUnlockedTickers();
        return true;
    }

    private void refreshUnlockedTickers() {
        availableTickers.clear();
        for (int day = 1; day <= currentDay; day++) {
            String ticker = dbManager.getTickerForDay(day);
            if (ticker != null && !availableTickers.contains(ticker)) {
                availableTickers.add(ticker);
            }
        }
        if (availableTickers.isEmpty()) {
            availableTickers.add("AAPL");
        }
    }

    // main game loop boot
    private void startFiveDayGame() {
        if (gameSessionRunning) return;
        if (gameScheduler != null && !gameScheduler.isShutdown() && !gameScheduler.isTerminated()) return;
        marketLoopStarted = true;
        gameSessionRunning = true;

        log("=" + "=".repeat(50));
        log("STARTING 5-DAY GAME - Difficulty: " + gameState.difficulty);
        log("=" + "=".repeat(50));

        gameScheduler = Executors.newSingleThreadScheduledExecutor();
        gameScheduler.scheduleAtFixedRate(this::tickGame, 0, 1, TimeUnit.SECONDS);
    }

    // main tick
    private synchronized void tickGame() {
        if (!running || !gameSessionRunning || gameState == null) return;

        // start day if needed
        if (!activeCycle.startsWith("CYCLE")) {
            log("[TICK] Starting new day, current activeCycle=" + (activeCycle.isEmpty() ? "EMPTY" : activeCycle));
            startNewDay();
        }

        // tick active cycle
        if ("CYCLE1".equals(activeCycle)) {
            if (cycleOne == null) {
                log("ERROR: [TICK] cycleOne is null!");
                return;
            }
            tickCycleOne();
            if (cycleOne.isComplete()) {
                endCycle1();
                startCycle2();
            }
        } else if ("CYCLE2".equals(activeCycle)) {
            broadcastToAll(cycleTwo.placeholderPacket());
            if (cycleTwo.isComplete()) {
                endCycle2();
                // Check if game is done
                if (currentDay < 5) {
                    // move day forward
                    advanceToNextDay();
                } else if (currentDayLoopCount < 4) {
                    // day5 extra loop
                    currentDayLoopCount++;
                    startCycle1();
                } else {
                    // game done
                    endGame();
                }
            }
        }
    }

    private boolean hasAnyInGameSessions() {
        synchronized (sessions) {
            for (ClientSession s : sessions) {
                if (s.startedGame) {
                    return true;
                }
            }
        }
        return false;
    }

    private synchronized void handleDisconnectNoActionDay(ClientSession session) {
        if (!session.startedGame) {
            return;
        }
        if (!marketLoopStarted || gameState == null) {
            return;
        }
        if (hasAnyInGameSessions()) {
            return;
        }

        log("No active players left. Forfeit day " + currentDay + " as no-action.");
        dbManager.appendActivity(gameState.userId, currentDay, "NO_ACTION_DISCONNECT", 0.0);

        if ("CYCLE1".equals(activeCycle) && cycleOne != null) {
            cycleOne.stop();
        }
        if ("CYCLE2".equals(activeCycle) && cycleTwo != null) {
            cycleTwo.stop();
        }

        if (currentDay < 5) {
            advanceToNextDay();
        } else {
            endGame();
        }
    }

    // start a new day
    private void startNewDay() {
        log("\n" + "=".repeat(60));
        log("DAY " + currentDay + ": Starting with ticker " + currentTicker);
        log("Unlocked tickers: " + availableTickers);
        log("=".repeat(60));

        // make sure data exists for all tickers
        List<String> csvPaths = new ArrayList<>();
        for (String ticker : availableTickers) {
            if (!ensureTickerDataAvailable(ticker, gameState.difficulty)) {
                log("ERROR: Could not generate price data for " + ticker);
                return;
            }
            csvPaths.add(ticker + "_stock_prices.csv");
        }
        cycleOne = new CycleOneEngine(csvPaths);

        cycle1HighPrice = 0;
        cycle1LowPrice = Double.MAX_VALUE;
        cycle1OpenPrice = 0;
        cycle1ClosePrice = 0;

        startCycle1();
    }

    // get/generate csv for this ticker
    private boolean ensureTickerDataAvailable(String ticker, int difficulty) {
        String csvPath = ticker + "_stock_prices.csv";
        Path path = Path.of(csvPath);

        // csv already there
        if (Files.exists(path)) {
            log("Using existing CSV: " + csvPath);
            return true;
        }

        // no csv, gen it now
        log("CSV not found, generating via stock_sim.exe...");
        
        // try prev close as S0
        double S0 = 500.0;
        if (currentDay > 1) {
            Double prevClose = getPreviousDayCloseFromCsv();
            if (prevClose != null && prevClose > 0) {
                S0 = prevClose;
                log("Using previous close as S0: " + String.format("%.2f", S0));
            }
        }

        // run stock_sim
        boolean generated = runStockSimulator(ticker, difficulty, S0);
        if (!generated) {
            log("ERROR: Could not generate CSV for " + ticker);
            return false;
        }
        return true;
    }

    // run sim exe. fallback = cmake build
    private boolean runStockSimulator(String ticker, int difficulty, double S0) {
        try {
            // try exe first
            ProcessBuilder pb = new ProcessBuilder(BUILD_EXECUTABLE, ticker, String.valueOf(difficulty), String.format("%.2f", S0));
            pb.directory(new java.io.File("."));
            pb.redirectErrorStream(true);

            Process p = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                log("  [stock_sim] " + line);
            }

            int exitCode = p.waitFor();
            if (exitCode == 0) {
                log("Generated " + ticker + "_stock_prices.csv");
                return true;
            }

            log("ERROR: stock_sim.exe exited with code " + exitCode);
            log("Attempting rebuild via cmake...");

            // build fallback
            pb = new ProcessBuilder("cmd", "/c", BUILD_FALLBACK);
            pb.directory(new java.io.File("."));
            pb.redirectErrorStream(true);

            p = pb.start();
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = br.readLine()) != null) {
                if (line.contains("error") || line.contains("Error")) {
                    log("  [cmake] " + line);
                }
            }

            exitCode = p.waitFor();
            log("Cmake build exited with code " + exitCode);

            if (exitCode == 0) {
                // retry sim
                pb = new ProcessBuilder(BUILD_EXECUTABLE, ticker, String.valueOf(difficulty), String.format("%.2f", S0));
                pb.directory(new java.io.File("."));
                pb.redirectErrorStream(true);

                p = pb.start();
                br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                while ((line = br.readLine()) != null) {
                    log("  [stock_sim] " + line);
                }

                exitCode = p.waitFor();
                if (exitCode == 0) {
                    log("Generated " + ticker + "_stock_prices.csv after rebuild");
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            log("ERROR running stock_sim: " + e.getMessage());
            return false;
        }
    }

    private void startCycle1() {
        activeCycle = "CYCLE1";
        if (cycleOne != null) {
            cycleOne.start();
        }
        broadcastToAll("{\"TYPE\":\"PHASE\",\"CYCLE\":\"CYCLE1\"}");
        log("CYCLE1 started for ticker=" + currentTicker);
    }

    private void endCycle1() {
        cycleOne.stop();
        // todo: store ohlc if needed
        log("CYCLE1 complete for " + currentTicker);
    }

    private void startCycle2() {
        activeCycle = "CYCLE2";
        cycleTwo.start();
        broadcastToAll("{\"TYPE\":\"PHASE\",\"CYCLE\":\"CYCLE2\"}");
        log("CYCLE2 started");
    }

    private void endCycle2() {
        cycleTwo.stop();
        log("CYCLE2 complete");
    }

    private void advanceToNextDay() {
        currentDayLoopCount = 0;
        currentDay++;
        if (currentDay > 5) {
            currentDay = 5;  // safety guard
            return;
        }

        currentTicker = dbManager.getTickerForDay(currentDay);
        refreshUnlockedTickers();
        dbManager.advanceToNextDay(gameState.userId);
        gameState.currentDay = currentDay;

        log("\n*** ADVANCING TO DAY " + currentDay + " ***");
        activeCycle = "";  // force day start next tick
    }

    private Double getPreviousDayCloseFromCsv() {
        try {
            String prevTicker = dbManager.getTickerForDay(currentDay - 1);
            if (prevTicker == null) return null;
            Path p = Path.of(prevTicker + "_stock_prices.csv");
            if (!Files.exists(p)) return null;

            String last = null;
            for (String line : Files.readAllLines(p)) {
                if (line != null && !line.isBlank()) {
                    last = line;
                }
            }
            if (last == null || last.startsWith("Time,")) return null;
            String[] parts = last.split(",");
            if (parts.length < 3) return null;
            return Double.parseDouble(parts[2].trim());
        } catch (Exception e) {
            return null;
        }
    }

    private void endGame() {
        log("\n" + "=".repeat(60));
        log("GAME COMPLETE!");
        log("=".repeat(60));

        if (gameState != null) {
            dbManager.setGameStatus(gameState.userId, "COMPLETED", currentDay);
            gameState.gameStatus = "COMPLETED";
            gameState.completedDay = currentDay;
        }

        gameSessionRunning = false;
        marketLoopStarted = false;
        activeCycle = "";

        if (gameScheduler != null) {
            gameScheduler.shutdownNow();
            gameScheduler = null;
        }

        broadcastToAll("{\"TYPE\":\"GAME_COMPLETE\",\"DAY\":" + currentDay + ",\"GAME_STATUS\":\"COMPLETED\"}");
    }

    private void tickCycleOne() {
        List<ClientSession> snapshot;
        synchronized (sessions) {
            snapshot = new ArrayList<>(sessions);
        }

        for (ClientSession session : snapshot) {
            int step = getGranularityStep(session.clientId);
            int index = cycleOne.advanceAndGetIndexForClient(session.clientId, step);
            if (index < 0) {
                continue;
            }

            for (String ticker : availableTickers) {
                String packet = cycleOne.pricePacketAtIndex(ticker, index);
                if (packet != null) {
                    sendToSession(session, packet);
                }

                String aiPacket = cycleOne.aiPacketAtIndex(ticker, index);
                if (aiPacket != null) {
                    sendToSession(session, aiPacket);
                }
            }
        }
    }

    private int getGranularityStep(int clientId) {
        ClientState state = clientStates.get(clientId);
        if (state != null && state.hasPowerup("coffee")) {
            return POWERUP_STEP;
        }
        return DEFAULT_STEP;
    }

    private void handleClientMessages(ClientSession session) {
        try {
            String line;
            while ((line = session.reader.readLine()) != null) {
                if (line.startsWith("LOGIN:")) {
                    handleLogin(session, line);
                } else if (line.startsWith("SIGNUP:")) {
                    handleSignup(session, line);
                } else if (line.startsWith("START_GAME:")) {
                    handleStartGame(session, line);
                } else if (line.startsWith("RESUME_GAME:")) {
                    handleResumeGame(session, line);
                } else if (line.startsWith("POWERUP:")) {
                    handlePowerup(session, line);
                }
            }
        } catch (IOException e) {
            log("Client disconnected: id=" + session.clientId + " reason=" + e.getMessage());
        } finally {
            removeSession(session);
        }
    }

    private void handleStartGame(ClientSession session, String message) {
        // START_GAME:<difficulty>:<jwt>
        String[] parts = message.split(":", 3);
        if (parts.length < 3) {
            sendToSession(session, "{\"TYPE\":\"ERROR\",\"MSG\":\"INVALID_START_GAME_FORMAT\"}");
            return;
        }

        if (session.username == null || session.username.isBlank()) {
            sendToSession(session, "{\"TYPE\":\"START_GAME_DENY\",\"REASON\":\"LOGIN_REQUIRED\"}");
            return;
        }

        String jwt = parts[2].trim();
        if (!authService.isJwtShapeValid(jwt) || !authService.validateJwtForUser(session.username, jwt)) {
            sendToSession(session, "{\"TYPE\":\"START_GAME_DENY\",\"REASON\":\"JWT_MISMATCH\"}");
            return;
        }

        try {
            int difficulty = Integer.parseInt(parts[1].trim());
            if (difficulty < 1 || difficulty > 4) difficulty = 2;

            String testUser = session.username;
            DBManager.GameState gs = dbManager.createOrResetGame(testUser, difficulty);

            if (gs == null) {
                gs = new DBManager.GameState(1, testUser, 1, difficulty, 10000.0, 0, 0, 0, 0, 0, 0.0);
            }

            gameState = gs;
            currentDay = gameState.currentDay;
            currentTicker = dbManager.getTickerForDay(currentDay);
            if (currentTicker == null) currentTicker = "AAPL";
            refreshUnlockedTickers();
            session.startedGame = true;

            startFiveDayGame();
            
            sendToSession(session, "{\"TYPE\":\"GAME_START\",\"DAY\":" + currentDay + ",\"TICKER\":\"" + currentTicker + "\"}");
            log("START_GAME client=" + session.clientId + " difficulty=" + difficulty + " user=" + testUser);
        } catch (NumberFormatException e) {
            sendToSession(session, "{\"TYPE\":\"ERROR\",\"MSG\":\"INVALID_DIFFICULTY\"}");
        }
    }

    private void handleResumeGame(ClientSession session, String message) {
        // RESUME_GAME:<jwt>
        String[] parts = message.split(":", 2);
        if (parts.length < 2) {
            sendToSession(session, "{\"TYPE\":\"ERROR\",\"MSG\":\"INVALID_RESUME_GAME_FORMAT\"}");
            return;
        }

        if (session.username == null || session.username.isBlank()) {
            sendToSession(session, "{\"TYPE\":\"START_GAME_DENY\",\"REASON\":\"LOGIN_REQUIRED\"}");
            return;
        }

        String jwt = parts[1].trim();
        if (!authService.isJwtShapeValid(jwt) || !authService.validateJwtForUser(session.username, jwt)) {
            sendToSession(session, "{\"TYPE\":\"START_GAME_DENY\",\"REASON\":\"JWT_MISMATCH\"}");
            return;
        }

        DBManager.GameState gs = dbManager.getGameState(session.username);
        if (gs == null) {
            sendToSession(session, "{\"TYPE\":\"START_GAME_DENY\",\"REASON\":\"NO_SAVED_GAME\"}");
            return;
        }

        if (!"IN_PROGRESS".equalsIgnoreCase(gs.gameStatus)) {
            sendToSession(session, "{\"TYPE\":\"START_GAME_DENY\",\"REASON\":\"NO_ACTIVE_GAME\"}");
            return;
        }

        gameState = gs;
        currentDay = gameState.currentDay;
        currentTicker = dbManager.getTickerForDay(currentDay);
        if (currentTicker == null) currentTicker = "AAPL";
        refreshUnlockedTickers();
        session.startedGame = true;

        dbManager.setGameStatus(gameState.userId, "IN_PROGRESS", gameState.completedDay);
        gameState.gameStatus = "IN_PROGRESS";

        startFiveDayGame();

        sendToSession(session, "{\"TYPE\":\"GAME_START\",\"DAY\":" + currentDay + ",\"TICKER\":\"" + currentTicker + "\"}");
        log("RESUME_GAME client=" + session.clientId + " user=" + session.username + " day=" + currentDay);
    }

    private void handleSignup(ClientSession session, String message) {
        // SIGNUP:<username>:<password>:<jwt>
        String[] parts = message.split(":", 4);
        if (parts.length < 4) {
            sendToSession(session, "{\"TYPE\":\"ERROR\",\"MSG\":\"INVALID_SIGNUP_FORMAT\"}");
            return;
        }

        String username = parts[1].trim();
        String password = parts[2].trim();
        String jwt = parts[3].trim();

        if (username.isEmpty() || password.isEmpty()) {
            sendToSession(session, "{\"TYPE\":\"SIGNUP_FAIL\",\"REASON\":\"EMPTY_FIELDS\"}");
            return;
        }

        boolean created = authService.registerUser(username, password, jwt);
        if (created) {
            sendToSession(session, "{\"TYPE\":\"SIGNUP_OK\",\"USER\":\"" + username + "\"}");
            log("SIGNUP_OK client=" + session.clientId + " user=" + username);
        } else {
            sendToSession(session, "{\"TYPE\":\"SIGNUP_FAIL\",\"REASON\":\"USER_EXISTS_OR_INVALID\"}");
            log("SIGNUP_FAIL client=" + session.clientId + " user=" + username);
        }
    }

    private void handleLogin(ClientSession session, String message) {
        // LOGIN:<username>:<jwt> OR LOGIN:<username>:<password>:<jwt>
        String[] parts = message.split(":", 4);
        if (parts.length < 3) {
            sendToSession(session, "{\"TYPE\":\"ERROR\",\"MSG\":\"INVALID_LOGIN_FORMAT\"}");
            return;
        }

        String username = parts[1].trim();
        String password = "password"; // compat fallback for old client packets
        String jwt;

        if (parts.length >= 4) {
            password = parts[2].trim();
            jwt = parts[3].trim();
        } else {
            jwt = parts[2].trim();
        }

        boolean ok = authService.authenticateUser(username, password, jwt);

        if (ok) {
            session.username = username;
            session.jwt = jwt;
            DBManager.GameState gs = dbManager.getGameState(username);
            boolean hasSavedGame = gs != null;
            boolean hasActiveGame = gs != null && "IN_PROGRESS".equalsIgnoreCase(gs.gameStatus);
            String gameStatus = hasSavedGame ? (gs.gameStatus == null || gs.gameStatus.isBlank() ? "COMPLETED" : gs.gameStatus.toUpperCase()) : "NONE";
            int day = hasSavedGame ? gs.currentDay : 1;
            int difficulty = hasSavedGame ? gs.difficulty : 2;
            sendToSession(session, "{\"TYPE\":\"LOGIN_OK\",\"USER\":\"" + username + "\",\"HAS_SAVED_GAME\":" + hasSavedGame + ",\"HAS_ACTIVE_GAME\":" + hasActiveGame + ",\"GAME_STATUS\":\"" + gameStatus + "\",\"DAY\":" + day + ",\"DIFFICULTY\":" + difficulty + "}");
            log("LOGIN_OK client=" + session.clientId + " user=" + username);
        } else {
            sendToSession(session, "{\"TYPE\":\"LOGIN_FAIL\"}");
            log("LOGIN_FAIL client=" + session.clientId);
        }
    }

    private void handlePowerup(ClientSession session, String message) {
        // POWERUP:<name>:<jwt>
        String[] parts = message.split(":", 3);
        if (parts.length < 3) {
            sendToSession(session, "{\"TYPE\":\"ERROR\",\"MSG\":\"INVALID_POWERUP_FORMAT\"}");
            return;
        }

        String powerup = parts[1].trim().toLowerCase();
        String jwt = parts[2].trim();

        if (!authService.isJwtShapeValid(jwt)) {
            sendToSession(session, "{\"TYPE\":\"POWERUP_DENY\",\"REASON\":\"INVALID_JWT\"}");
            return;
        }

        if (session.username == null || !authService.userExists(session.username)) {
            sendToSession(session, "{\"TYPE\":\"POWERUP_DENY\",\"REASON\":\"LOGIN_REQUIRED\"}");
            return;
        }

        if (!authService.validateJwtForUser(session.username, jwt)) {
            sendToSession(session, "{\"TYPE\":\"POWERUP_DENY\",\"REASON\":\"JWT_MISMATCH\"}");
            return;
        }

        if ("coffee".equals(powerup)) {
            ClientState state = clientStates.get(session.clientId);
            if (state != null) {
                state.addPowerup("coffee", ClientState.COFFEE_DURATION_MS);
                sendToSession(session, "{\"TYPE\":\"POWERUP_OK\",\"NAME\":\"coffee\",\"STEP\":1}");
                log("POWERUP coffee client=" + session.clientId + " -> step=1");
            }
            return;
        }

        sendToSession(session, "{\"TYPE\":\"POWERUP_DENY\",\"REASON\":\"UNKNOWN_POWERUP\"}");
    }

    private void broadcastToAll(String payload) {
        List<ClientSession> snapshot;
        synchronized (sessions) {
            snapshot = new ArrayList<>(sessions);
        }
        for (ClientSession session : snapshot) {
            sendToSession(session, payload);
        }
    }

    //Instead of sockets need to make TCP implemention here, this is where we will send packets to clients
    private void sendToSession(ClientSession session, String payload) {
        try {
            session.writer.write(payload);
            session.writer.write("\n");
            session.writer.flush();
        } catch (IOException e) {
            log("Send failed for client=" + session.clientId + ": " + e.getMessage());
        }
    }

    private void removeSession(ClientSession session) {
        synchronized (sessions) {
            sessions.remove(session);
        }
        clientStates.remove(session.clientId);
        try {
            session.socket.close();
        } catch (IOException ignored) {
        }
        log("CLIENT DISCONNECTED: id=" + session.clientId);
        handleDisconnectNoActionDay(session);
    }

    public void stop() {
        running = false;
        gameSessionRunning = false;
        marketLoopStarted = false;
        if (gameScheduler != null) {
            gameScheduler.shutdownNow();
            gameScheduler = null;
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                log("Server stopped");
            }
        } catch (IOException e) {
            log("Error closing server: " + e.getMessage());
        }
    }

    //Session class to hold client info and streams, again were updatin to TCP so streams do for now, will need to add packet queue and TCP send/recv logic here in the future
    private static class ClientSession {
        private final int clientId;
        private final Socket socket;
        private final String clientIp;
        private final BufferedReader reader;
        private final BufferedWriter writer;
        private String username;
        private String jwt;
        private boolean startedGame;

        private ClientSession(int clientId, Socket socket) throws IOException {
            this.clientId = clientId;
            this.socket = socket;
            this.clientIp = socket.getInetAddress().getHostAddress();
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.startedGame = false;
        }
    }
}