import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.UUID;

/**
 * Main server controller listens for client connections and manages game sessions
 */
public class ServerController {
    private static final int PORT = 5000;
    private static ServerController instance;
    private ServerSocket serverSocket;
    private Map<Integer, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    private DBHandler dbHandler;
    private ExecutorService threadPool;

    private ServerController() {
        dbHandler = DBHandler.getInstance();
        threadPool = Executors.newCachedThreadPool();
    }

    //Singleton to get servercontroller, synchronised to avoid multiple 
    public static synchronized ServerController getInstance() {
        if (instance == null) {
            instance = new ServerController();
        }
        return instance;
    }

    /**
     * Start the server and listen for client connections
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);

            //Loop to handle connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected from " + clientSocket.getInetAddress());

                // Handle each client in a separate thread
                ClientHandler handler = new ClientHandler(clientSocket, this);
                threadPool.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Register a connected client
     */
    public void registerClient(int userId, ClientHandler handler) {
        connectedClients.put(userId, handler);
        System.out.println("Client registered: User ID " + userId);
    }

    /**
     * Unregister a disconnected client
     */
    public void unregisterClient(int userId) {
        connectedClients.remove(userId);
        System.out.println("Client unregistered: User ID " + userId);
    }

    /**
     * Get a client handler by user ID
     */
    public ClientHandler getClient(int userId) {
        return connectedClients.get(userId);
    }


    /**
     * main func to handle starting the servber controller and begining the start()
     * @param args
     */
    public static void main(String[] args) {
        ServerController server = ServerController.getInstance();
        server.start();
    }

    /**
     * Inner class to handle individual client connections
     */
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private ServerController server;
        private int userId = -1;
        private int gameId = -1;
        private int currentDay = 1;
        private int difficulty = 1;
        private final Map<String, Double> lastPricePerTicker = new java.util.concurrent.ConcurrentHashMap<>();

        public ClientHandler(Socket socket, ServerController server) {
            this.socket = socket;
            this.server = server;
        }

        @Override
        public void run() {
            try {
                /**
                 * Setup the input and output buffers to write to client
                 */
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                //Func to listen for packets
                listenForPackets();
            } catch (IOException e) {
                System.err.println("Client connection error: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        /**
         * Listen for incoming packets from client
         */
        private void listenForPackets() {
            try {
                String packet;
                while ((packet = in.readLine()) != null) {
                    /**
                     * TODO:
                     * HANDLE TCP + CHECKSUM HERE
                     */
                    //Handles packet here
                    handlePacket(packet);
                }
            } catch (IOException e) {
                System.err.println("Connection lost: " + e.getMessage());
            }
        }

        /**
         * Handle incoming packets from client
         */
        private void handlePacket(String packet) {
            if (packet == null || packet.isEmpty()) return;

            // Expected format: PAYLOAD|CHECKSUM
            int lastSeparator = packet.lastIndexOf('|');
            if (lastSeparator <= 0 || lastSeparator == packet.length() - 1) {
            System.err.println("Invalid packet format (missing checksum): " + packet);
            sendPacket("ERROR|Invalid packet format");
            return;
            }

            String payload = packet.substring(0, lastSeparator);
            String checksumText = packet.substring(lastSeparator + 1).trim();

            int receivedChecksum;
            try {
            receivedChecksum = Integer.parseInt(checksumText);
            } catch (NumberFormatException e) {
            System.err.println("Invalid checksum value: " + checksumText);
            sendPacket("ERROR|Invalid checksum");
            return;
            }

            int calculatedChecksum = calculateCheckSum(payload);
            if (receivedChecksum != calculatedChecksum) {
            System.err.println("Checksum mismatch. Received: " + receivedChecksum +
                       ", Calculated: " + calculatedChecksum +
                       ", Payload: " + payload);
            sendPacket("ERROR|Checksum mismatch");
            return;
            }

            // Checksum is valid, now parse packet payload
            String[] parts = payload.split("\\|");
            String packetType = parts[0];

            /**
             * Switch case for recognised packet types
             */
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
                    default:
                        System.out.println("Unknown packet type: " + packetType);
                }
            } catch (Exception e) {
                System.err.println("Error handling packet: " + e.getMessage());
            }
        }

        /**
         * Handle LOGIN packet
         * Format: LOGIN|username|password
         */
        private void handleLogin(String[] parts) {
            if (parts.length < 3) {
                sendPacket("LOGIN_FAILED|Invalid packet format");
                return;
            }
            String username = parts[1];
            String password = parts[2];
            // Validate login via DBHandler
            int validatedUserId = server.dbHandler.validateLogin(username, password);
            if (validatedUserId != -1) {
                userId = validatedUserId;
                server.registerClient(userId, this);
                sendPacket("LOGIN_SUCCESS|" + username);

                // Check for active games
                List<Map<String, Object>> activeGames = server.dbHandler.getActiveGames(userId);
                if (!activeGames.isEmpty()) {
                    sendPacket("ACTIVE_GAMES|" + activeGames.size());
                    for (Map<String, Object> game : activeGames) {
                        String gameInfo = "GAME_INFO|" +
                                game.get("game_id") + "|" +
                                game.get("difficulty") + "|" +
                                game.get("current_day") + "|" +
                                game.get("player_cash") + "|" +
                                game.get("ai_cash");
                        sendPacket(gameInfo);
                    }
                }
            } else {
                //Sends the error packet
                sendPacket("LOGIN_FAILED|Invalid username or password");
            }
        }

        /**
         * Handle SIGNUP packet
         * Format: SIGNUP|username|password
         */
        private void handleSignup(String[] parts) {
            if (parts.length < 3) {
                sendPacket("SIGNUP_FAILED|Invalid packet format");
                return;
            }

            String username = parts[1];
            String password = parts[2];

            // Create new user via DBHandler
            int newUserId = server.dbHandler.createUser(username, password);

            if (newUserId != -1) {
                userId = newUserId;
                server.registerClient(userId, this);
                sendPacket("SIGNUP_SUCCESS|" + username);
            } else {
                sendPacket("SIGNUP_FAILED|Username already exists or error creating user");
            }
        }

        /**
         * Handle START_GAME packet
         * Format: START_GAME|difficulty
         */
        private void handleStartGame(String[] parts) {
            if (userId == -1) {
                sendPacket("ERROR|Not authenticated");
                return;
            }

            if (parts.length < 2) {
                sendPacket("ERROR|Invalid packet format");
                return;
            }

            try {
                int difficulty = Integer.parseInt(parts[1]);
                this.difficulty = difficulty; // Store for use in streamGamePrices

                // Create new game in database
                int gameId = server.dbHandler.createGame(userId, difficulty);

                if (gameId != -1) {
                    // Store gameId in handler
                    this.gameId = gameId;
                    this.currentDay = 1; // Reset to day 1
                    
                    // Delete any other active games for this user
                    server.dbHandler.deleteOtherActiveGames(userId, gameId);

                    // Get AAPL stock ID (always Day 1 stock) - verify it exists
                    int aaplStockId = server.dbHandler.getStockId("AAPL");
                    if (aaplStockId != -1) {
                        // Send game start packet
                        sendPacket("GAME_START|" + gameId + "|" + difficulty);
                        sendPacket("PHASE|CYCLE_1");
                        sendPortfolioUpdate();
                        
                        // Send initial portfolio with starting cash
                        
                        
                        // Start streaming prices in background thread (handles all generation and DB updates)
                        new Thread(() -> streamGamePrices(gameId, "AAPL")).start();
                    } else {
                        sendPacket("ERROR|Stock not found");
                    }
                } else {
                    sendPacket("ERROR|Failed to create game");
                }
            } catch (NumberFormatException e) {
                sendPacket("ERROR|Invalid difficulty");
            }
        }

        /**
         * Handle RESUME_GAME packet
         * Format: RESUME_GAME
         */
        private void handleResumeGame(String[] parts) {
            if (userId == -1) {
                sendPacket("ERROR|Not authenticated");
                return;
            }

            // Get first active game, add the multiplayer conn here
            List<Map<String, Object>> activeGames = server.dbHandler.getActiveGames(userId);

            if (!activeGames.isEmpty()) {
                Map<String, Object> game = activeGames.get(0);
                int gameId = (Integer) game.get("game_id");
                String difficultyStr = (String) game.get("difficulty");
                int resumeDifficulty = Integer.parseInt(difficultyStr);
                int resumeDay = (Integer) game.get("current_day");

                this.gameId = gameId;
                this.difficulty = resumeDifficulty;
                this.currentDay = resumeDay;

                sendPacket("GAME_RESUMED|" + gameId + "|" + resumeDifficulty + "|" + resumeDay);
                
                // Send initial portfolio
                sendPortfolioUpdate();
                
                // Resume price streaming from current day
                new Thread(() -> streamGamePrices(gameId, null)).start();

            } else {
                sendPacket("ERROR|No active game found");
            }
        }


        /**
         * Handles BUY packets and has guard for malformed packets and incorr gameIDS
         */
        private void handleBuy(String[] parts) {
            if (gameId == -1) {
                sendPacket("ERROR|No active game");
                return;
            }
            if (parts.length < 4) {
                sendPacket("ERROR|Invalid BUY packet format");
                return;
            }

            try {
                String ticker = parts[1];
                int quantity = Integer.parseInt(parts[2]);
                double price = Double.parseDouble(parts[3]);
                
                double totalCost = quantity * price;
                
                // Get current game state from DB
                Map<String, Object> gameState = server.dbHandler.getGameData(gameId);
                double playerCash = ((Number) gameState.get("player_cash")).doubleValue();
                
                // Validate player has enough cash
                if (playerCash < totalCost) {
                    sendPacket("ERROR|Insufficient cash: need £" + String.format("%.2f", totalCost) + " but have £" + String.format("%.2f", playerCash));
                    sendPortfolioUpdate();
                    return;
                }
                
                // Execute trade: deduct cash, add stock
                double newCash = playerCash - totalCost;
                int stockId = server.dbHandler.getStockId(ticker);
                
                // Update player cash
                server.dbHandler.updatePlayerCash(gameId, newCash);
                
                // Update holdings
                int currentHolding = server.dbHandler.getPlayerHolding(gameId, stockId);
                server.dbHandler.updatePlayerHolding(gameId, stockId, currentHolding + quantity);
                
                System.out.println("Game " + gameId + ": BUY " + quantity + " x " + ticker + " @ £" + price + " = £" + totalCost + " | Cash: £" + playerCash + " -> £" + newCash);
                
                sendPortfolioUpdate();
            } catch (Exception e) {
                sendPacket("ERROR|" + e.getMessage());
                e.printStackTrace();
            }
        }

        private void handleSell(String[] parts) {
            if (gameId == -1) {
                sendPacket("ERROR|No active game");
                return;
            }
            if (parts.length < 4) {
                sendPacket("ERROR|Invalid SELL packet format");
                return;
            }

            try {
                String ticker = parts[1];
                int quantity = Integer.parseInt(parts[2]);
                double price = Double.parseDouble(parts[3]);
                
                double totalProceeds = quantity * price;
                
                // Get current game state from DB
                Map<String, Object> gameState = server.dbHandler.getGameData(gameId);
                double playerCash = ((Number) gameState.get("player_cash")).doubleValue();
                
                // Get current holdings
                int stockId = server.dbHandler.getStockId(ticker);
                int currentHolding = server.dbHandler.getPlayerHolding(gameId, stockId);
                
                // Validate player has enough stock
                if (currentHolding < quantity) {
                    sendPacket("ERROR|Insufficient holdings: have " + currentHolding + " but trying to sell " + quantity);
                    sendPortfolioUpdate();
                    return;
                }
                
                // Execute trade: add cash, remove stock
                double newCash = playerCash + totalProceeds;
                
                // Update player cash
                server.dbHandler.updatePlayerCash(gameId, newCash);
                
                // Update holdings
                server.dbHandler.updatePlayerHolding(gameId, stockId, currentHolding - quantity);
                
                System.out.println("Game " + gameId + ": SELL " + quantity + " x " + ticker + " @ £" + price + " = £" + totalProceeds + " | Cash: £" + playerCash + " -> £" + newCash);
                
                sendPortfolioUpdate();
            } catch (Exception e) {
                sendPacket("ERROR|" + e.getMessage());
                e.printStackTrace();
            }
        }

        private void handleSellAll(String[] parts) {
            if (gameId == -1) {
                sendPacket("ERROR|No active game");
                return;
            }
            if (parts.length < 3) {
                sendPacket("ERROR|Invalid SELL_ALL packet format");
                return;
            }

            try {
                String ticker = parts[1];
                double price = Double.parseDouble(parts[2]);
                
                // Get current holdings
                int stockId = server.dbHandler.getStockId(ticker);
                int currentHolding = server.dbHandler.getPlayerHolding(gameId, stockId);
                
                if (currentHolding <= 0) {
                    sendPacket("ERROR|No holdings to sell");
                    return;
                }
                
                // Calculate cash to add
                double cashFromSale = currentHolding * price;
                
                // Get current cash and update
                Map<String, Object> gameState = server.dbHandler.getGameData(gameId);
                double playerCash = ((Number) gameState.get("player_cash")).doubleValue();
                double newCash = playerCash + cashFromSale;
                
                // Update DB
                server.dbHandler.updatePlayerCash(gameId, newCash);
                server.dbHandler.updatePlayerHolding(gameId, stockId, 0);
                
                System.out.println("Game " + gameId + ": SELL_ALL " + currentHolding + " x " + ticker + " @ £" + 
                    String.format("%.3f", price) + " = £" + String.format("%.2f", cashFromSale) + 
                    " | Cash: £" + String.format("%.2f", playerCash) + " -> £" + String.format("%.2f", newCash));
                
                sendPortfolioUpdate();
            } catch (Exception e) {
                sendPacket("ERROR|" + e.getMessage());
            }
        }

        private void sendPortfolioUpdate() {
            try {
                // Get current game data from DB
                Map<String, Object> gameState = server.dbHandler.getGameData(gameId);
                double playerCash = ((Number) gameState.get("player_cash")).doubleValue();
                
                // Format: PORTFOLIO_UPDATE|cash|ticker1:qty1:price1|...
                StringBuilder packet = new StringBuilder("PORTFOLIO_UPDATE|" + String.format("%.2f", playerCash));
                String[] stocks = {"AAPL", "TSLA", "GOOGL", "NVDA", "AMZN"};
                
                // Add all unlocked stocks for current day with current holdings and latest price
                for (int i = 0; i < currentDay && i < stocks.length; i++) {
                    String ticker = stocks[i];
                    int stockId = server.dbHandler.getStockId(ticker);
                    int holding = server.dbHandler.getPlayerHolding(gameId, stockId);
                    // Use the last streamed price for this ticker, or 0 if not yet streamed
                    double price = lastPricePerTicker.getOrDefault(ticker, 0.0);
                    packet.append("|").append(ticker).append(":").append(holding).append(":").append(String.format("%.2f", price));
                }
                
                sendPacket(packet.toString());
            } catch (Exception e) {
                System.err.println("Error sending portfolio update: " + e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * Send packet to client
         */
        private void sendPacket(String packet) {
            if (out != null) {
                int checksum = calculateCheckSum(packet);
                out.println(packet + "|" + checksum);
            }
        }

        /**
         * Build C++ backend executable if not already built
         */
        private synchronized void buildCppBackend() throws Exception {
            File exeFile = new File("Backend/build/bin/Release/stock_sim.exe");
            if (exeFile.exists()) {
                System.out.println("C++ backend executable already exists");
                return;
            }
            
            System.out.println("Building C++ backend...");
            File rootDir = new File(".");
            
            // Run: cmake -B Backend/build -S . (CMakeLists.txt is in project root)
            ProcessBuilder cmake = new ProcessBuilder("cmake", "-B", "Backend/build", "-S", ".");
            cmake.directory(rootDir);
            cmake.redirectErrorStream(true);
            Process cmakeProcess = cmake.start();
            
            // Read output from CMake
            java.io.BufferedReader cmakeReader = new java.io.BufferedReader(
                new java.io.InputStreamReader(cmakeProcess.getInputStream()));
            String line;
            System.out.println("=== CMake Configuration Output ===");
            while ((line = cmakeReader.readLine()) != null) {
                System.out.println("[CMAKE] " + line);
            }
            
            int cmakeExit = cmakeProcess.waitFor();
            
            if (cmakeExit != 0) {
                System.err.println("CMake configuration failed with exit code " + cmakeExit);
                throw new Exception("CMake configuration failed");
            }
            
            // Run: cmake --build Backend/build --config Release
            ProcessBuilder build = new ProcessBuilder("cmake", "--build", "Backend/build", "--config", "Release");
            build.directory(rootDir);
            build.redirectErrorStream(true);
            Process buildProcess = build.start();
            
            // Read output from build
            java.io.BufferedReader buildReader = new java.io.BufferedReader(
                new java.io.InputStreamReader(buildProcess.getInputStream()));
            System.out.println("=== CMake Build Output ===");
            while ((line = buildReader.readLine()) != null) {
                System.out.println("[BUILD] " + line);
            }
            
            int buildExit = buildProcess.waitFor();
            
            if (buildExit != 0) {
                System.err.println("CMake build failed with exit code " + buildExit);
                throw new Exception("CMake build failed");
            }
            
            System.out.println("C++ backend built successfully");
        }

        /**
         * Call C++ backend to generate stock prices
         */
        private void callCppBackend(String ticker, int difficulty, double startingPrice, String seed) {
            new Thread(() -> {
                try {
                    // Ensure backend is built
                    buildCppBackend();
                    
                    String[] cmd = {"Backend/build/bin/Release/stock_sim.exe", ticker, String.valueOf(difficulty), 
                                   String.valueOf(startingPrice), seed};
                    
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(new File("."));
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        System.out.println("C++ backend completed for " + ticker);
                    } else {
                        System.err.println("C++ backend failed with exit code " + exitCode);
                    }
                } catch (Exception e) {
                    System.err.println("Error calling C++ backend: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        }

        /**
         * Stream game prices across multiple days (5 days total)
         * Day progression: Day 1 (AAPL) -> Day 2 (AAPL+TSLA) -> ... -> Day 5 (all stocks)
         * Each day streams ALL available tickers in CYCLE_1, then CYCLE_2 for regeneration
         */
        private void streamGamePrices(int gameId, String firstTicker) {
            String[] allTickers = {"AAPL", "TSLA", "GOOGL", "NVDA", "AMZN"};
            
            try {
                while (currentDay <= 5) {
                    System.out.println("Game " + gameId + ": Starting Day " + currentDay);
                    
                    // Generate prices for all available tickers on this day
                    for (int i = 0; i < currentDay && i < allTickers.length; i++) {
                        String ticker = allTickers[i];
                        String csvFile = ticker + "_day" + currentDay + "_stock_prices.csv";
                        File priceFile = new File(csvFile);
                        
                        // Generate this ticker's prices if not already generated
                        if (!priceFile.exists()) {
                            String seed = UUID.randomUUID().toString();
                            int tickerStockId = server.dbHandler.getStockId(ticker);
                            server.dbHandler.saveDaySeed(gameId, currentDay, tickerStockId, seed);
                            
                            // Generate in background and wait
                            Thread genThread = new Thread(() -> {
                                try {
                                    buildCppBackend();
                                    String[] cmd = {"Backend/build/bin/Release/stock_sim.exe", ticker,
                                                   String.valueOf(difficulty), "500.0", seed};
                                    ProcessBuilder pb = new ProcessBuilder(cmd);
                                    pb.directory(new File("."));
                                    pb.redirectErrorStream(true);
                                    Process process = pb.start();
                                    process.waitFor();
                                    
                                    // Rename the generated CSV to include day number
                                    File oldFile = new File(ticker + "_stock_prices.csv");
                                    File newFile = new File(ticker + "_day" + currentDay + "_stock_prices.csv");
                                    if (oldFile.exists()) {
                                        oldFile.renameTo(newFile);
                                    }
                                } catch (Exception e) {
                                    System.err.println("Error generating " + ticker + ": " + e.getMessage());
                                }
                            });
                            genThread.start();
                            genThread.join();
                        }
                    }
                    
                    // Stream all available tickers for this day IN PARALLEL
                    List<Thread> streamingThreads = new ArrayList<>();
                    for (int i = 0; i < currentDay && i < allTickers.length; i++) {
                        String ticker = allTickers[i];
                        
                        // Create a thread for each ticker to stream prices in parallel
                        Thread streamThread = new Thread(() -> {
                            try {
                                // Wait for C++ to finish generating CSV if needed
                                Thread.sleep(1000);
                                
                                String csvFile = ticker + "_day" + currentDay + "_stock_prices.csv";
                                BufferedReader csvReader = new BufferedReader(new FileReader(csvFile));
                                String line;
                                int pointCount = 0;
                                int sendCount = 0;
                                csvReader.readLine(); // Skip header
                                
                                // Stream 102 price points for this ticker
                                while ((line = csvReader.readLine()) != null && sendCount < 102) {
                                    pointCount++;
                                    
                                    // Send every 5th point
                                    if (pointCount % 5 == 0) {
                                        String[] parts = line.split(",");
                                        if (parts.length >= 3) {
                                            double price = Double.parseDouble(parts[2]);
                                            lastPricePerTicker.put(ticker, price);  // Track latest price
                                            sendPacket("PRICE_UPDATE|" + ticker + "|" + price + "|" + pointCount);
                                            sendPortfolioUpdate();
                                            sendCount++;
                                        }
                                        Thread.sleep(1000); // 1 second between each 5th point
                                    }
                                }
                                csvReader.close();
                                System.out.println("Game " + gameId + ": Day " + currentDay + " - Finished streaming " + ticker);
                            } catch (Exception e) {
                                System.err.println("Error streaming " + ticker + ": " + e.getMessage());
                            }
                        });
                        streamThread.start();
                        streamingThreads.add(streamThread);
                    }
                    
                    // Wait for all streaming threads to complete
                    for (Thread thread : streamingThreads) {
                        thread.join();
                    }
                    System.out.println("Game " + gameId + ": Day " + currentDay + " - All tickers finished streaming");
                    
                    // Switch to CYCLE_2 (10 second hidden phase for regeneration)
                    sendPacket("PHASE|CYCLE_2");
                    sendPortfolioUpdate();
                    System.out.println("Game " + gameId + ": Day " + currentDay + " ending, entering CYCLE_2");
                    
                    // Regenerate prices for newly unlocked ticker during CYCLE_2 (hidden from user)
                    if (currentDay < 5) {
                        String nextTicker = allTickers[currentDay]; // Next ticker to unlock
                        String newSeed = UUID.randomUUID().toString();
                        int tickerStockId = server.dbHandler.getStockId(nextTicker);
                        server.dbHandler.saveDaySeed(gameId, currentDay + 1, tickerStockId, newSeed);
                        
                        // Generate prices in background thread
                        Thread regenerateThread = new Thread(() -> {
                            try {
                                buildCppBackend();
                                String[] cmd = {"Backend/build/bin/Release/stock_sim.exe", nextTicker,
                                               String.valueOf(difficulty), "500.0", newSeed};
                                ProcessBuilder pb = new ProcessBuilder(cmd);
                                pb.directory(new File("."));
                                pb.redirectErrorStream(true);
                                Process process = pb.start();
                                int exitCode = process.waitFor();
                                if (exitCode == 0) {
                                    // Rename to include next day number
                                    File oldFile = new File(nextTicker + "_stock_prices.csv");
                                    File newFile = new File(nextTicker + "_day" + (currentDay + 1) + "_stock_prices.csv");
                                    if (oldFile.exists()) {
                                        oldFile.renameTo(newFile);
                                    }
                                    System.out.println("Regenerated prices for Day " + (currentDay + 1) + " (" + nextTicker + ")");
                                } else {
                                    System.err.println("Failed to regenerate " + nextTicker);
                                }
                            } catch (Exception e) {
                                System.err.println("Error regenerating prices: " + e.getMessage());
                            }
                        });
                        regenerateThread.start();
                        regenerateThread.join(); // Wait for regeneration to complete
                    }
                    
                    Thread.sleep(10000); // CYCLE_2 lasts 10 seconds
                    
                    // Check if game is complete (after day 5)
                    if (currentDay >= 5) {
                        System.out.println("Game " + gameId + ": All 5 days completed");
                        handleGameCompletion(gameId);
                        break;
                    }
                    
                    // Move to next day and update database
                    currentDay++;
                    server.dbHandler.updateGameDay(gameId, currentDay);
                    System.out.println("Game " + gameId + ": Advanced to Day " + currentDay + ", DB updated");
                    
                    sendPortfolioUpdate();
                    
                    // Switch back to CYCLE_1 for next day
                    sendPacket("PHASE|CYCLE_1");
                    sendPortfolioUpdate();
                    System.out.println("Game " + gameId + ": Starting Day " + currentDay);
                }
            } catch (Exception e) {
                System.err.println("Error streaming prices: " + e.getMessage());
                sendPacket("ERROR|Price streaming error");
            }
        }
        
        /**
         * Handle game completion - determine winner and clean up
         */
        private void handleGameCompletion(int gameId) {
            try {
                // Get final game state (cash for player and AI)
                Map<String, Object> gameState = server.dbHandler.getGameFinalState(gameId);
                
                double playerCash = gameState != null ? ((Number) gameState.get("player_cash")).doubleValue() : 10000.0;
                double aiCash = gameState != null ? ((Number) gameState.get("ai_cash")).doubleValue() : 10000.0;
                
                // Calculate net worth (cash + holdings value)
                // For now, simplified - in production would sum all positions
                double playerNetWorth = playerCash;
                double aiNetWorth = aiCash;
                
                String result;
                if (playerNetWorth > aiNetWorth) {
                    result = "WIN";
                    System.out.println("Game " + gameId + ": Player WINS (" + playerNetWorth + " vs " + aiNetWorth + ")");
                } else if (aiNetWorth > playerNetWorth) {
                    result = "LOSS";
                    System.out.println("Game " + gameId + ": Player LOSES (" + playerNetWorth + " vs " + aiNetWorth + ")");
                } else {
                    result = "DRAW";
                    System.out.println("Game " + gameId + ": DRAW");
                }
                
                sendPacket("GAME_OVER|" + result + "|" + playerNetWorth + "|" + aiNetWorth);
                
                // Delete game from active games
                server.dbHandler.deleteActiveGame(gameId);
                System.out.println("Game " + gameId + ": Deleted from active games");
            } catch (Exception e) {
                System.err.println("Error handling game completion: " + e.getMessage());
                sendPacket("ERROR|Game completion error");
            }
        }

        /**
         * Calculate checksum for payload validation
         */
        private int calculateCheckSum(String payload) {
            int checksum = 0;
            for (char c : payload.toCharArray()) {
                checksum += c;
            }
            return checksum;
        }

        /**
         * Disconnect client and clean up active game if mid-progress
         */
        private void disconnect() {
            try {
                // If client disconnected mid-game, mark the day as completed and advance to next day
                if (gameId != -1 && currentDay >= 1 && currentDay < 5) {
                    int nextDay = currentDay + 1;
                    server.dbHandler.updateGameDay(gameId, nextDay);
                    System.out.println("Client disconnected mid-game (Game " + gameId + ", Day " + currentDay + "). Advanced to Day " + nextDay);
                } else if (gameId != -1 && currentDay >= 5) {
                    // Game was at or past day 5, mark as complete
                    server.dbHandler.updateGameDay(gameId, 5);
                }
                
                if (userId != -1) {
                    server.unregisterClient(userId);
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
            System.out.println("Client disconnected");
        }
    }
}
