import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ServerController
 *
 * Listens on PORT for client connections and spawns a clientHandler thread per connection via the thread pool.
 */
public class ServerController {
    private static final int PORT = 5000;


    private static ServerController instance;
    private ServerController() {
        //Creates the DB, and threads
        dbHandler  = DBHandler.getInstance();
        threadPool = Executors.newCachedThreadPool();
    }


    public static synchronized ServerController getInstance() {
        if (instance == null) {
            instance = new ServerController();
        }
        return instance;
    }

    //Setup serverScoekt and threadpool vars and the client map.
    private ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private final Map<Integer, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    private final DBHandler dbHandler;


    /**
     * Starts the server.
     *
     * On startup, removes any stale CSV files left over from a previous run so price geneeration always starts clean. Then enters the accept loop.
     */
    public void start() {
        deleteStaleCSVFiles();

        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected from " + clientSocket.getInetAddress());
                threadPool.execute(new ClientHandler(clientSocket, this));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Delets all CSV, this is a limitation we plan to pipe directly to stdin of JAVA
     */
    private void deleteStaleCSVFiles() {
        Deque<File> stack = new ArrayDeque<>();
        stack.push(new File("."));

        while (!stack.isEmpty()) {
            File dir = stack.pop();
            File[] entries = dir.listFiles();
            if (entries == null) continue;

            for (File entry : entries) {
                if (entry.isDirectory()) {
                    stack.push(entry);
                } else if (entry.getName().toLowerCase().endsWith(".csv")) {
                    if (entry.delete()) {
                        System.out.println("Deleted stale CSV: " + entry.getPath());
                    } else {
                        System.err.println("Could not delete: " + entry.getPath());
                    }
                }
            }
        }
    }


    // -------------------------------------------------------------------------
    // Client registry
    // -------------------------------------------------------------------------

    /**
     * 
     *  Registers an authenticated client so other parts of the server can reach it. */
    public void registerClient(int userId, ClientHandler handler) {
        connectedClients.put(userId, handler);
        System.out.println("Client registered: userId=" + userId);
    }

    /** Removes a client from the registry on disconnect. */
    public void unregisterClient(int userId) {
        connectedClients.remove(userId);
        System.out.println("Client unregistered: userId=" + userId);
    }

    /**
     *  Returns the live handler for a userId, or null if not connected. */
    public ClientHandler getClient(int userId) {
        return connectedClients.get(userId);
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        ServerController.getInstance().start();
    }

    // ======================================================
    // ClientHandler
    // =========================================================================
    static class ClientHandler implements Runnable {

        //ADD additional TIckers here
        private static final String[] TICKERS = {"TULA", "PEARS", "CORN", "RICE", "GRAIN"};
        private static final int TOTAL_DAYS  = 5;
        private static final int POINTS_PER_DAY = 102;
        private static final long CYCLE2_MS   = 60_000;

        // -----------------------------------------------------------------------
        // Connection state
        // -----------------------------------------------------------------------

        private final Socket socket;
        private final ServerController server;
        private PrintWriter out;
        private BufferedReader in;


        //State
        private int userId = -1;
        private int gameId = -1;
        private int currentDay = 1;
        private int difficulty = 1;

        // -----------------------------------------------------------------------
        // Powerup state
        // -----------------------------------------------------------------------
        private volatile boolean coffeeActive = false;
        private volatile long coffeeEndTime = 0;

        // -----------------------------------------------------------------------
        // Price tracking
        // Used when sending portfolio updates so the client always sees the
        // current market price for each holding.
        // -----------------------------------------------------------------------

        private final Map<String, Double> lastPricePerTicker = new ConcurrentHashMap<>();

        // -----------------------------------------------------------------------
        // AI signals
        // This is used to store the pairs, originally was kelly Ciretion but we pivoted to random allocation
        // -----------------------------------------------------------------------

        private final Map<String, List<int[]>> aiSignals = new HashMap<>();

        /**
         * Synchronises AI trade execution across parallel ticker threads.
         * Each ticker streams on its own thread; without this lock, two tickers could read/write AI cash simultaneously.
         * https://medium.com/@premchandu.in/locking-mechanism-in-java-3eaf0d28066c
         * //We had to look this up as the bug was discovered later on.
         */
        private final Object tradeLock = new Object();

        // -----------------------------------------------------------------------
        // Thread-shutdown fields
        // -----------------------------------------------------------------------

        /**
         * Kill-switch for all loops inside this handler.
         *
         * Must be volatile so the JVM never caches its value per-thread.
         * Without volatile, child threads may spin forever after disconnect()
         * sets this to false.
         */
        private volatile boolean running = true;

        private Thread streamingThread;

        // -----------------------------------------------------------------------
        // Constructor
        // -----------------------------------------------------------------------

        ClientHandler(Socket socket, ServerController server) {
            this.socket = socket;
            this.server = server;
        }

        // -----------------------------------------------------------------------
        // Runnable entry point
        // -----------------------------------------------------------------------

        @Override
        public void run() {
            try {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                listenForPackets();
            } catch (IOException e) {
                System.err.println("Client connection error: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        // -----------------------------------------------------------------------
        // Packet I/O
        // -----------------------------------------------------------------------

        /**
         * Blocks reading lines from the socket until the connection closes or an IOException is thrown.
         */
        private void listenForPackets() {
            try {
                String packet;
                while ((packet = in.readLine()) != null) {
                    handlePacket(packet);
                }
            } catch (IOException e) {
                System.err.println("Connection lost: " + e.getMessage());
            }
        }

        /**
         * Parses and dispatches a raw packet string.
         *
         */
        private void handlePacket(String packet) {
            if (packet == null || packet.isEmpty()) return;

            int lastPipe = packet.lastIndexOf('|');
            if (lastPipe <= 0 || lastPipe == packet.length() - 1) {
                System.err.println("Malformed packet (missing checksum): " + packet);
                sendPacket("ERROR|Invalid packet format");
                return;
            }

            String payload  = packet.substring(0, lastPipe);
            String checksumToken = packet.substring(lastPipe + 1).trim();

            int receivedChecksum;
            try {
                receivedChecksum = Integer.parseInt(checksumToken);
            } catch (NumberFormatException e) {
                System.err.println("Non-integer checksum: " + checksumToken);
                sendPacket("ERROR|Invalid checksum");
                return;
            }

            if (receivedChecksum != calculateChecksum(payload)) {
                sendPacket("ERROR|Checksum mismatch");
                return;
            }

            String[] parts      = payload.split("\\|");
            String   packetType = parts[0];

            try {
                switch (packetType) {
                    case "LOGIN":
                        handleLogin(parts);      
                        break;
                    case "SIGNUP":      
                        handleSignup(parts);
                        break;
                    case "START_GAME":
                        handleStartGame(parts); 
                        break;
                    case "RESUME_GAME": 
                        handleResumeGame(parts);
                        break;
                    case "BUY":
                        handleBuy(parts);
                        break;
                    case "SELL":
                        handleSell(parts);
                        break;
                    case "SELL_ALL":
                        handleSellAll(parts);
                        break;
                    case "POWERUP":
                        handlePowerup(parts);
                        break;
                    default:
                        System.out.println("Unknown packet type: " + packetType);
                }
            } catch (Exception e) {
                System.err.println("Error handling packet [" + packetType + "]: " + e.getMessage());
            }
        }

        /**
         * Sends a packet to the client, appending a checksum.
         */
        private void sendPacket(String payload) {
            if (out != null) {
                out.println(payload + "|" + calculateChecksum(payload));
            }
        }

        /**
         * Simple checksum sum of char values, mod 256.
         * Keeps values within one byte; prevents integer overflow on long strings.
         */
        private int calculateChecksum(String payload) {
            int sum = 0;
            for (char c : payload.toCharArray()) sum += c;
            return sum % 256;
        }

        // -----------------------------------------------------------------------
        // Auth handlers
        // -----------------------------------------------------------------------


        private void handleLogin(String[] parts) {
            if (parts.length < 3) { sendPacket("LOGIN_FAILED|Invalid packet format"); return; }

            String username = parts[1];
            String password = parts[2];

            int validatedId = server.dbHandler.validateLogin(username, password);
            if (validatedId == -1) {
                sendPacket("LOGIN_FAILED|Invalid username or password");
                return;
            }

            userId = validatedId;
            server.registerClient(userId, this);
            sendPacket("LOGIN_SUCCESS|" + username);

            List<Map<String, Object>> activeGames = server.dbHandler.getActiveGames(userId);
            if (!activeGames.isEmpty()) {
                sendPacket("ACTIVE_GAMES|" + activeGames.size());
                for (Map<String, Object> game : activeGames) {
                    sendPacket("GAME_INFO|"
                        + game.get("game_id")    + "|"
                        + game.get("difficulty") + "|"
                        + game.get("current_day")+ "|"
                        + game.get("player_cash")+ "|"
                        + game.get("ai_cash"));
                }
            }
        }


        private void handleSignup(String[] parts) {
            if (parts.length < 3) { sendPacket("SIGNUP_FAILED|Invalid packet format"); return; }

            String username = parts[1];
            String password = parts[2];

            int newUserId = server.dbHandler.createUser(username, password);
            if (newUserId == -1) {
                sendPacket("SIGNUP_FAILED|Username already exists or error creating user");
                return;
            }

            userId = newUserId;
            server.registerClient(userId, this);
            sendPacket("SIGNUP_SUCCESS|" + username);
        }

        // -----------------------------------------------------------------------
        // Game lifecycle handlers
        // -----------------------------------------------------------------------

        private void handleStartGame(String[] parts) {
            if (userId == -1) { sendPacket("ERROR|Not authenticated"); return; }
            if (parts.length < 2) { sendPacket("ERROR|Invalid packet format"); return; }

            try {
                difficulty   = Integer.parseInt(parts[1]);
                gameId       = server.dbHandler.createGame(userId, difficulty);
                currentDay   = 1;

                if (gameId == -1) { sendPacket("ERROR|Failed to create game"); return; }

                server.dbHandler.deleteOtherActiveGames(userId, gameId);

                if (server.dbHandler.getStockId("TULA") == -1) {
                    sendPacket("ERROR|Stock not found");
                    return;
                }

                sendPacket("GAME_START|" + gameId + "|" + difficulty);
                sendPacket("PHASE|CYCLE_1");
                sendPortfolioUpdate();
                sendAIPortfolioUpdate();

                streamingThread = new Thread(() -> streamGamePrices(gameId, "TULA"));
                streamingThread.start();

            } catch (NumberFormatException e) {
                sendPacket("ERROR|Invalid difficulty");
            }
        }

        
        private void handleResumeGame(String[] parts) {
            if (userId == -1) { sendPacket("ERROR|Not authenticated"); return; }

            List<Map<String, Object>> activeGames = server.dbHandler.getActiveGames(userId);
            if (activeGames.isEmpty()) { sendPacket("ERROR|No active game found"); return; }

            Map<String, Object> game = activeGames.get(0);
            gameId     = (Integer) game.get("game_id");
            difficulty = Integer.parseInt((String) game.get("difficulty"));
            currentDay = (Integer) game.get("current_day");

            sendPacket("GAME_RESUMED|" + gameId + "|" + difficulty + "|" + currentDay);
            sendPortfolioUpdate();
            sendAIPortfolioUpdate();

            streamingThread = new Thread(() -> streamGamePrices(gameId, null));
            streamingThread.start();
        }

        // -----------------------------------------------------------------------
        // Trade handlers
        // -----------------------------------------------------------------------

        private void handleBuy(String[] parts) {
            if (gameId == -1)     { sendPacket("ERROR|No active game");           return; }
            if (parts.length < 4) { sendPacket("ERROR|Invalid BUY packet format"); return; }

            try {
                String ticker   = parts[1];
                int    quantity = Integer.parseInt(parts[2]);
                double price    = Double.parseDouble(parts[3]);
                double totalCost = quantity * price;

                Map<String, Object> state      = server.dbHandler.getGameData(gameId);
                double              playerCash = ((Number) state.get("player_cash")).doubleValue();

                if (playerCash < totalCost) {
                    sendPacket("ERROR|Insufficient cash: need £" + String.format("%.2f", totalCost) + " but have £" + String.format("%.2f", playerCash));
                    sendPortfolioUpdate();
                    sendAIPortfolioUpdate();
                    return;
                }

                int stockId = server.dbHandler.getStockId(ticker);
                int currentHolding = server.dbHandler.getPlayerHolding(gameId, stockId);
                double newCash = playerCash - totalCost;

                server.dbHandler.updatePlayerCash(gameId, newCash);
                server.dbHandler.updatePlayerHolding(gameId, stockId, currentHolding + quantity);
                server.dbHandler.logTradeAction(userId, gameId, currentDay, "BUY", stockId, quantity, price);

                sendPortfolioUpdate();
                sendAIPortfolioUpdate();

            } catch (Exception e) {
                sendPacket("ERROR|" + e.getMessage());
                e.printStackTrace();
            }
        }



        private void handleSell(String[] parts) {
            if (gameId == -1){ 
                    sendPacket("ERROR|No active game");
                    return;
                }
            if (parts.length < 4){
                sendPacket("ERROR|Invalid SELL packet format");
                return;
            }

            try {
                String ticker = parts[1];
                int quantity = Integer.parseInt(parts[2]);
                double price = Double.parseDouble(parts[3]);
                double proceeds = quantity * price;

                int stockId        = server.dbHandler.getStockId(ticker);
                int currentHolding = server.dbHandler.getPlayerHolding(gameId, stockId);

                if (currentHolding < quantity) {
                    sendPacket("ERROR|Insufficient holdings: have " + currentHolding + " but trying to sell " + quantity);
                    sendPortfolioUpdate();
                    sendAIPortfolioUpdate();
                    return;
                }

                Map<String, Object> state = server.dbHandler.getGameData(gameId);
                double playerCash = ((Number) state.get("player_cash")).doubleValue();
                double newCash = playerCash + proceeds;

                server.dbHandler.updatePlayerCash(gameId, newCash);
                server.dbHandler.updatePlayerHolding(gameId, stockId, currentHolding - quantity);
                server.dbHandler.logTradeAction(userId, gameId, currentDay, "SELL", stockId, quantity, price);

                sendPortfolioUpdate();
                sendAIPortfolioUpdate();

            } catch (Exception e) {
                sendPacket("ERROR|" + e.getMessage());
                e.printStackTrace();
            }
        }

        private void handleSellAll(String[] parts) {
            if (gameId == -1){
                sendPacket("ERROR|No active game");
                return;
            }
            if (parts.length < 3) {
                sendPacket("ERROR|Invalid SELL_ALL packet format");
                return;
            }

            try {
                String ticker  = parts[1];
                double price = Double.parseDouble(parts[2]);
                int stockId = server.dbHandler.getStockId(ticker);
                int held = server.dbHandler.getPlayerHolding(gameId, stockId);

                if (held <= 0) {
                    sendPacket("ERROR|No holdings to sell");
                    return;
                }

                double proceeds  = held * price;
                Map<String, Object> state = server.dbHandler.getGameData(gameId);
                double playerCash = ((Number) state.get("player_cash")).doubleValue();
                double newCash = playerCash + proceeds;

                server.dbHandler.updatePlayerCash(gameId, newCash);
                server.dbHandler.updatePlayerHolding(gameId, stockId, 0);
                server.dbHandler.logTradeAction(userId, gameId, currentDay, "SELL_ALL", stockId, held, price);

                sendPortfolioUpdate();
                sendAIPortfolioUpdate();

            } catch (Exception e) {
                sendPacket("ERROR|" + e.getMessage());
            }
        }

        /**
         * Handless the POWERUPS, acts a blueprint right now for futute powerups.
         */
        private void handlePowerup(String[] parts) {
            if (gameId == -1)     { sendPacket("ERROR|No active game"); return; }
            if (parts.length < 3) { sendPacket("ERROR|Invalid POWERUP packet"); return; }

            try {
                String powerupType = parts[1];
                String action = parts[2];
                
                if ("coffee".equalsIgnoreCase(powerupType)) {
                    if ("acquire".equalsIgnoreCase(action)) {
                        // Player got coffee from barista
                        int newCount = server.dbHandler.getInventoryItem(gameId, "Coffee") + 1;
                        server.dbHandler.updateInventoryItem(gameId, "Coffee", newCount);
                        System.out.println("Game " + gameId + ": Coffee acquired, count: " + newCount);
                        sendPacket("POWERUP|coffee|acquired|" + newCount);
                        
                    } else if ("activate".equalsIgnoreCase(action)) {
                        // Player using coffee in Cycle 1
                        int coffeeCount = server.dbHandler.getInventoryItem(gameId, "Coffee");
                        if (coffeeCount <= 0) {
                            sendPacket("ERROR|No coffee");
                            return;
                        }
                        
                        // Decrement and activate
                        int newCount = coffeeCount - 1;
                        server.dbHandler.updateInventoryItem(gameId, "Coffee", newCount);
                        coffeeActive = true;
                        coffeeEndTime = System.currentTimeMillis() + 10000;
                        
                        System.out.println("Game " + gameId + ": Coffee used, remaining: " + newCount);
                        sendPacket("POWERUP|coffee|activated|" + newCount + "|10000");
                    }
                }
            } catch (Exception e) {
                sendPacket("ERROR|" + e.getMessage());
            }
        }

        // -----------------------------------------------------------------------
        // Portfolio update helpers
        // -----------------------------------------------------------------------
        private void sendPortfolioUpdate() {
            try {
                Map<String, Object> state      = server.dbHandler.getGameData(gameId);
                double              playerCash = ((Number) state.get("player_cash")).doubleValue();

                StringBuilder sb = new StringBuilder("PORTFOLIO_UPDATE|")
                    .append(String.format("%.2f", playerCash));

                for (int i = 0; i < currentDay && i < TICKERS.length; i++) {
                    String ticker  = TICKERS[i];
                    int    stockId = server.dbHandler.getStockId(ticker);
                    int    holding = server.dbHandler.getPlayerHolding(gameId, stockId);
                    double price   = lastPricePerTicker.getOrDefault(ticker, 0.0);
                    sb.append("|").append(ticker).append(":").append(holding)
                      .append(":").append(String.format("%.2f", price));
                }

                sendPacket(sb.toString());

            } catch (Exception e) {
                System.err.println("Error sending portfolio update: " + e.getMessage());
            }
        }

        private void sendAIPortfolioUpdate() {
            try {
                Map<String, Object>  state      = server.dbHandler.getGameData(gameId);
                double               aiCash     = ((Number) state.get("ai_cash")).doubleValue();
                Map<Integer, Integer> aiPortfolio = server.dbHandler.getAIPortfolio(gameId);

                StringBuilder sb = new StringBuilder("AI_PORTFOLIO_UPDATE|")
                    .append(String.format("%.2f", aiCash));

                for (int i = 0; i < currentDay && i < TICKERS.length; i++) {
                    String ticker  = TICKERS[i];
                    int    stockId = server.dbHandler.getStockId(ticker);
                    int    holding = aiPortfolio.getOrDefault(stockId, 0);
                    double price   = lastPricePerTicker.getOrDefault(ticker, 0.0);
                    sb.append("|").append(ticker).append(":").append(holding)
                      .append(":").append(String.format("%.2f", price));
                }

                sendPacket(sb.toString());

            } catch (Exception e) {
                System.err.println("Error sending AI portfolio update: " + e.getMessage());
            }
        }



        // -----------------------------------------------------------------------
        // C++ backend bridge
        // -----------------------------------------------------------------------

        /**
         * Builds the C++ price-generation executable if it does not already exist.
         *
         * Runs cmake configure then cmake build and blocks until both complete.
         *
         * TODO: The exe path is Windows-only. Replace with a better solution like a (or a shell script wrapper) for Mac/Linux support.
         * //I tried gradlew but mac throws to mnay sudo requirements and refused to work.
         */
        private synchronized void buildCppBackend() throws Exception {
            File exeFile = new File("Backend/build/bin/Release/stock_sim.exe");
            if (exeFile.exists()) return;

            System.out.println("Building C++ backend...");

            runProcess(new String[]{"cmake", "-B", "Backend/build", "-S", "."}, "CMAKE");
            runProcess(new String[]{"cmake", "--build", "Backend/build", "--config", "Release"}, "BUILD");

            System.out.println("C++ backend built successfully");
        }

        /**
         * Runs an external process, streams its stdout, and throws if the exit
         * code is non-zero.
         */
        private void runProcess(String[] cmd, String tag) throws Exception {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File("."));
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[" + tag + "] " + line);
                }
            }

            int exit = proc.waitFor();
            if (exit != 0) throw new Exception(tag + " failed with exit code " + exit);
        }

        // -----------------------------------------------------------------------
        // Price streaming
        // -----------------------------------------------------------------------

        private void streamGamePrices(int gameId, String firstTicker) {
            try {
                while (currentDay <= TOTAL_DAYS && running) {
                    System.out.println("Game " + gameId + ": Starting Day " + currentDay);

                    generatePricesForDay(currentDay);
                    loadAISignalsForDay(currentDay);
                    streamTickersInParallel(currentDay);

                    if (!running) break;

                    sendPacket("PHASE|CYCLE_2");
                    sendPortfolioUpdate();
                    sendAIPortfolioUpdate();

                    if (currentDay < TOTAL_DAYS) {
                        pregenerateNextDay(currentDay);
                    }

                    try {
                        Thread.sleep(CYCLE2_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    if (!running) break;

                    if (currentDay >= TOTAL_DAYS) {
                        handleGameCompletion(gameId);
                        break;
                    }

                    currentDay++;
                    server.dbHandler.updateGameDay(gameId, currentDay);
                    System.out.println("Game " + gameId + ": Advanced to Day " + currentDay);

                    sendPacket("PHASE|CYCLE_1");
                    sendPortfolioUpdate();
                    sendAIPortfolioUpdate();
                }
            } catch (Exception e) {
                System.err.println("Error streaming prices: " + e.getMessage());
                sendPacket("ERROR|Price streaming error");
            }
        }


        private void generatePricesForDay(int day) throws Exception {
            for (int i = 0; i < day && i < TICKERS.length; i++) {
                String ticker  = TICKERS[i];
                File   csvFile = new File(csvPath(ticker, day));
                if (csvFile.exists()) continue;

                String seed      = UUID.randomUUID().toString();
                int    stockId   = server.dbHandler.getStockId(ticker);
                server.dbHandler.saveDaySeed(gameId, day, stockId, seed);

                String startPrice = lastPricePerTicker.containsKey(ticker) ? lastPricePerTicker.get(ticker).toString() : "500.0";

                generateCSV(ticker, startPrice, seed, day);
            }
        }

        private void pregenerateNextDay(int currentDay) throws Exception {
            String nextTicker = TICKERS[currentDay]; // index = currentDay because 0-based
            String seed = UUID.randomUUID().toString();
            int stockId = server.dbHandler.getStockId(nextTicker);
            server.dbHandler.saveDaySeed(gameId, currentDay + 1, stockId, seed);

            Thread t = new Thread(() -> {
                try { 
                    generateCSV(nextTicker, "500.0", seed, currentDay + 1); 
                }
                catch (Exception e) { System.err.println("Pregenerate failed: " + e.getMessage()); }
            });
            t.start();
            t.join();
        }

        /**
         * Calls the C++ executable to write a price CSV
         */
        private void generateCSV(String ticker, String startPrice, String seed, int day) throws Exception {
            buildCppBackend();

            String[] cmd = {
                "Backend/build/bin/Release/stock_sim.exe",
                ticker, String.valueOf(difficulty), startPrice, seed
            };
            runProcess(cmd, "GEN");

            File from = new File(ticker + "_stock_prices.csv");
            File to   = new File(csvPath(ticker, day));
            if (from.exists()) from.renameTo(to);
        }

        /** Returns the day-qualified CSV path for a ticker. */
        private String csvPath(String ticker, int day) {
            return ticker + "_day" + day + "_stock_prices.csv";
        }

        /**
         * Loads AI trade signals for every ticker unlocked on the given day.
         */
        private void loadAISignalsForDay(int day) {
            for (int i = 0; i < day && i < TICKERS.length; i++) {
                loadAISignals(TICKERS[i], day);
            }
        }

        /**
         * Streams all unlocked tickers for a day in parallel, waiting for all threads to finish before returning.
         */
        private void streamTickersInParallel(int day) throws InterruptedException {
            List<Thread> threads = new ArrayList<>();

            for (int i = 0; i < day && i < TICKERS.length; i++) {
                final String ticker = TICKERS[i];
                Thread t = new Thread(() -> streamTicker(ticker, day));
                t.start();
                threads.add(t);
            }

            for (Thread t : threads) t.join();

            System.out.println("Game " + gameId + ": Day " + day + " — all tickers finished");
        }

        /**
         * Reads a ticker's CSV and sends price updates to the client.
         *
         * When coffee powerup is active sends every tick instead for 10 seconds instead fo the normal 5th point per second
         *
         * Exits early if running becomes false (
         */
        private void streamTicker(String ticker, int day) {
            try {
                Thread.sleep(1000); // brief wait for CSV to be fully written

                String csvFile = csvPath(ticker, day);
                int pointCount = 0;
                int sentCount  = 0;

                try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                    reader.readLine();

                    String line;
                    while ((line = reader.readLine()) != null
                            && sentCount < POINTS_PER_DAY
                            && running) {

                        pointCount++;
                        String[] cols = line.split(",");
                        if (cols.length < 3){
                            continue;
                        }

                        double price = Double.parseDouble(cols[2]);
                        lastPricePerTicker.put(ticker, price);

                        // Check AI signals on every tick for accuracy
                        executeAITrade(ticker, pointCount, price);

                        // Check if coffee powerup is still active
                        boolean powerupActive = coffeeActive && System.currentTimeMillis() < coffeeEndTime;
                        if (coffeeActive && System.currentTimeMillis() >= coffeeEndTime) {
                            coffeeActive = false;
                        }

                        //every tick if coffee active, every 5th tick otherwise
                        boolean shouldSend = powerupActive ? true : (pointCount % 5 == 0);
                        
                        if (shouldSend) {
                            sendPacket("PRICE_UPDATE|" + ticker + "|" + price + "|" + pointCount);
                            sendPortfolioUpdate();
                            sendAIPortfolioUpdate();
                            sentCount++;
                            
                            // Sleep duration depends on powerup
                            long sleepMs = powerupActive ? 200 : 1000;  // 0.2s with coffee, 1s normally
                            // Sleepign in the thread is okay as it wont block the rest of the application.
                            Thread.sleep(sleepMs);
                        }
                    }
                }

                System.out.println("Game " + gameId + ": Day " + day + " — finished " + ticker);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("Error streaming " + ticker + ": " + e.getMessage());
            }
        }

        // -----------------------------------------------------------------------
        // Game completion
        // -----------------------------------------------------------------------

        /**
         * Determines the winner by comparing final cash balances, sends GAME_OVER and removes the game from the active-games table.
         *
         * @Note: only cheks cash needing the  player to liquidate first,
         * Also a finalised end game screen ahsnt been implemented, yet is is printed to console
         */
        private void handleGameCompletion(int gameId) {
            try {
                Map<String, Object> state = server.dbHandler.getGameFinalState(gameId);

                double playerNetWorth = state != null ? ((Number) state.get("player_cash")).doubleValue() : 10_000.0;
                double aiNetWorth = state != null ? ((Number) state.get("ai_cash")).doubleValue() : 10_000.0;

                String result;
                if (playerNetWorth > aiNetWorth) {
                    result = "WIN";
                } else if (aiNetWorth > playerNetWorth) {
                    result = "LOSS";
                } else {
                    result = "DRAW";
                }

                sendPacket("GAME_OVER|" + result + "|" + playerNetWorth + "|" + aiNetWorth);
                server.dbHandler.deleteActiveGame(gameId);

            } catch (Exception e) {
                System.err.println("Error handling game completion: " + e.getMessage());
                sendPacket("ERROR|Game completion error");
            }
        }

        // -----------------------------------------------------------------------
        // AI opponent
        // -----------------------------------------------------------------------

        /**
         * Loads AI trade signals from CSV into memory for a specific ticker/day.
         *
         */
        private void loadAISignals(String ticker, int day) {
            List<int[]> signals   = new ArrayList<>();
            String[]    candidates = {ticker + "_day" + day + "_ai_signals.csv", ticker + "_ai_signals.csv"
            };

            for (String filename : candidates) {
                File f = new File(filename);
                if (!f.exists()) continue;

                try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                    r.readLine(); // skip header
                    String line;
                    while ((line = r.readLine()) != null) {
                        String[] cols = line.split(",");
                        if (cols.length < 3) continue;
                        try {
                            int    timeIdx = Integer.parseInt(cols[0].trim());
                            String action  = cols[2].trim().toUpperCase();
                            signals.add(new int[]{ timeIdx, "BUY".equals(action) ? 0 : 1 });
                        } catch (NumberFormatException ignored) {}
                    }
                    System.out.println("AI signals loaded for " + ticker
                        + " Day " + day + ": " + signals.size() + " from " + filename);
                } catch (IOException e) {
                    System.err.println("Failed to read AI signals: " + filename);
                }
                break;
            }

            aiSignals.put(ticker, signals);
        }

        /**
         * Executes an AI trade if a signal exists for the given tick index.
         *
         * 
         * 
         * BUY:
         * This is where i tried to add KElly criterion but it was too unstable
         * 
         * spends a random percentage of available cash uses difficulty for bounds, higher difficulty can bet more agggressively.
         */
        private void executeAITrade(String ticker, int timeIdx, double price) {
            List<int[]> signals = aiSignals.get(ticker);
            if (signals == null) return;

            for (int[] signal : signals) {
                if (signal[0] != timeIdx) continue;

                boolean isBuy = signal[1] == 0;

                synchronized (tradeLock) {
                    Map<String, Object> state   = server.dbHandler.getGameData(gameId);
                    double aiCash  = ((Number) state.get("ai_cash")).doubleValue();
                    int stockId = server.dbHandler.getStockId(ticker);

                    if (isBuy) {
                        double[] spendOptions = spendPercentages();
                        double pct = spendOptions[new Random().nextInt(spendOptions.length)];
                        int qty = (int) ((aiCash * pct) / price);

                        if (qty <= 0) {
                            System.out.printf("[AI] BUY skipped — insufficient cash (£%.2f)%n", aiCash);
                            return;
                        }

                        double cost = qty * price;
                        int held = server.dbHandler.getAIHolding(gameId, stockId);
                        server.dbHandler.updateAICash(gameId, aiCash - cost);
                        server.dbHandler.updateAIHolding(gameId, stockId, held + qty);
                    } else {
                        int held = server.dbHandler.getAIHolding(gameId, stockId);
                        if (held <= 0) {
                            return;
                        }
                        double proceeds = held * price;
                        server.dbHandler.updateAICash(gameId, aiCash + proceeds);
                        server.dbHandler.updateAIHolding(gameId, stockId, 0);
                    }
                }
                break;
            }
        }


        private double[] spendPercentages() {
            // This is the substitute for kelly criterion 

            if (difficulty == 1) return new double[]{0.10, 0.20, 0.30};
            if (difficulty == 2) return new double[]{0.20, 0.30, 0.50};
            return new double[]{0.30, 0.40, 0.50};
        }

        // -----------------------------------------------------------------------
        // Disconnect and shutdown
        // -----------------------------------------------------------------------


        private void disconnect() {
            //stop all child threads
            running = false;
            if (streamingThread != null) {
                streamingThread.interrupt();
            }

            //save
            try {
                if (gameId != -1) {
                    if (currentDay >= 1 && currentDay < TOTAL_DAYS) {
                        // Disconnected mid-game advance the stored day so a resume
                        server.dbHandler.updateGameDay(gameId, currentDay + 1);
                        System.out.println("Game " + gameId + " saved at Day " + (currentDay + 1));
                    } else if (currentDay == TOTAL_DAYS) {
                        // Game was completeed
                        server.dbHandler.deleteActiveGame(gameId);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error persisting game state on disconnect: " + e.getMessage());
            }

            //close socket and deregister
            try {
                if (userId != -1) server.unregisterClient(userId);
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }

            System.out.println("Client disconnected — all threads stopped");
        }
    }
}