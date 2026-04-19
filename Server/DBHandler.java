import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/**
 * Centralized database handler for all server transactions
 * Handles user authentication, game state and data persistence
 * //Right now this keeps all DB logic and creates a new connection on each method whcih should be chnaged
 */
public class DBHandler {
    private static final String DB_PATH = "DB/main.db";
    private static DBHandler instance;

    private DBHandler() {
        loadSqliteDriver();
        initSchema();
    }

    //Extartcs current instance for methods and synchronizes to ensure only one instance exists
    public static synchronized DBHandler getInstance() {
        if (instance == null) {
            instance = new DBHandler();
        }
        return instance;
    }

    //Loads thre driveer
    private void loadSqliteDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found: " + e.getMessage());
        }
    }

    /**
     * Creates the database if it doesnt exist 
     * No return values
     */
    private void initSchema() {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();

            // Create users table
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "user_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "username VARCHAR(50) UNIQUE NOT NULL," +
                    "password_hash TEXT NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Create games table
            stmt.execute("CREATE TABLE IF NOT EXISTS games (" +
                    "game_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id INTEGER NOT NULL," +
                    "difficulty VARCHAR(20) NOT NULL," +
                    "current_day INTEGER DEFAULT 1," +
                    "player_cash DECIMAL(12,2) DEFAULT 10000.00," +
                    "ai_cash DECIMAL(12,2) DEFAULT 10000.00," +
                    "stress INTEGER DEFAULT 0," +
                    "active INTEGER DEFAULT 1," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE)");

            // Create game_days table (stores seeds per day per stock)
            stmt.execute("CREATE TABLE IF NOT EXISTS game_days (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "game_id INTEGER NOT NULL," +
                    "day INTEGER NOT NULL," +
                    "stock_id INTEGER NOT NULL," +
                    "seed TEXT NOT NULL," +
                    "FOREIGN KEY(game_id) REFERENCES games(game_id) ON DELETE CASCADE," +
                    "FOREIGN KEY(stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE," +
                    "UNIQUE(game_id, day, stock_id))");

            // Create stocks table
            stmt.execute("CREATE TABLE IF NOT EXISTS stocks (" +
                    "stock_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "symbol VARCHAR(10) UNIQUE NOT NULL," +
                    "name VARCHAR(100))");

            // Create portfolios table
            stmt.execute("CREATE TABLE IF NOT EXISTS portfolios (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "game_id INTEGER NOT NULL," +
                    "owner_type VARCHAR(10) NOT NULL," +
                    "stock_id INTEGER NOT NULL," +
                    "quantity INTEGER DEFAULT 0," +
                    "FOREIGN KEY(game_id) REFERENCES games(game_id) ON DELETE CASCADE," +
                    "FOREIGN KEY(stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE," +
                    "UNIQUE(game_id, owner_type, stock_id))");

            // Create inventory table
            stmt.execute("CREATE TABLE IF NOT EXISTS inventory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "game_id INTEGER NOT NULL," +
                    "item_name VARCHAR(50) NOT NULL," +
                    "quantity INTEGER DEFAULT 0," +
                    "FOREIGN KEY(game_id) REFERENCES games(game_id) ON DELETE CASCADE," +
                    "UNIQUE(game_id, item_name))");

            // Create user_activity_log table
            stmt.execute("CREATE TABLE IF NOT EXISTS user_activity_log (" +
                    "log_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id INTEGER NOT NULL," +
                    "game_id INTEGER NOT NULL," +
                    "day INTEGER NOT NULL," +
                    "action VARCHAR(10) NOT NULL," +
                    "stock_id INTEGER," +
                    "quantity INTEGER," +
                    "price DECIMAL(12,2)," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE," +
                    "FOREIGN KEY(game_id) REFERENCES games(game_id) ON DELETE CASCADE," +
                    "FOREIGN KEY(stock_id) REFERENCES stocks(stock_id) ON DELETE SET NULL)");

            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_games_user ON games(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_portfolios_game ON portfolios(game_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_inventory_game ON inventory(game_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_log_game ON user_activity_log(game_id)");

            // Add active column to games table if it doesn't exist (migration)
            try {
                stmt.execute("ALTER TABLE games ADD COLUMN active INTEGER DEFAULT 1");
            } catch (SQLException e) {
                // Column already exists, ignore
            }

            // Insert default stocks if they don't exist
            stmt.execute("INSERT OR IGNORE INTO stocks (symbol, name) VALUES ('TULA', 'TTULA car companyu')");
            stmt.execute("INSERT OR IGNORE INTO stocks (symbol, name) VALUES ('PEARS', 'Pears Phomne company')");
            stmt.execute("INSERT OR IGNORE INTO stocks (symbol, name) VALUES ('CORN', 'Corn Farming company')");
            stmt.execute("INSERT OR IGNORE INTO stocks (symbol, name) VALUES ('RICE', 'Rice food company')");
            stmt.execute("INSERT OR IGNORE INTO stocks (symbol, name) VALUES ('GRAIN', 'Grain farming company')");

            conn.commit();
            stmt.close();
            System.out.println("Database schema initialized successfully");
        } catch (SQLException e) {
            System.err.println("Error initializing database schema: " + e.getMessage());
        }
    }

    /**
     * Get database connection
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    /**
     * Hash password using SHA-256 (NOT AES-256)
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            System.err.println("Error hashing password: " + e.getMessage());
            return null;
        }
    }

    /**
     * Validate login credentials
     * Returns: user_id if valid, -1 if invalid
     */
    public int validateLogin(String username, String password) {
        try (Connection conn = getConnection()) {
            String query = "SELECT user_id, password_hash FROM users WHERE username = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String storedHash = rs.getString("password_hash");
                        String providedHash = hashPassword(password);
                        if (storedHash.equals(providedHash)) {
                            return rs.getInt("user_id");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error validating login: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Create new usr signup block
     * Returns: user_id if successful, -1 if user already exists or error
     */
    public int createUser(String username, String password) {
        // Check if user already exists
        try (Connection conn = getConnection()) {
            String checkQuery = "SELECT user_id FROM users WHERE username = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setString(1, username);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("User already exists: " + username);
                        return -1;
                    }
                }
            }

            // Create new user
            String insertQuery = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, username);
                insertStmt.setString(2, hashPassword(password));
                insertStmt.executeUpdate();

                try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int userId = generatedKeys.getInt(1);
                        System.out.println("User created successfully: " + username + " (ID: " + userId + ")");
                        return userId;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error creating user: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Get active games for a user
     * Returns list of game info: {game_id, difficulty, current_day, player_cash, ai_cash, stress}
     */
    public List<Map<String, Object>> getActiveGames(int userId) {
        List<Map<String, Object>> games = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String query = "SELECT game_id, difficulty, current_day, player_cash, ai_cash, stress " +
                    "FROM games WHERE user_id = ? AND active = 1";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> game = new HashMap<>();
                        game.put("game_id", rs.getInt("game_id"));
                        game.put("difficulty", rs.getString("difficulty"));
                        game.put("current_day", rs.getInt("current_day"));
                        game.put("player_cash", rs.getDouble("player_cash"));
                        game.put("ai_cash", rs.getDouble("ai_cash"));
                        game.put("stress", rs.getInt("stress"));
                        games.add(game);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching active games: " + e.getMessage());
        }
        return games;
    }

    /**
     * Get saved games for a user (completed games)
     */
    public List<Map<String, Object>> getSavedGames(int userId) {
        List<Map<String, Object>> games = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String query = "SELECT game_id, difficulty, current_day FROM games WHERE user_id = ? AND current_day = 1";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> game = new HashMap<>();
                        game.put("game_id", rs.getInt("game_id"));
                        game.put("difficulty", rs.getString("difficulty"));
                        game.put("current_day", rs.getInt("current_day"));
                        games.add(game);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching saved games: " + e.getMessage());
        }
        return games;
    }

    /**
     * Create a new game for a user
     * Returns: game_id if successful, -1 if error
     */
    public int createGame(int userId, int difficulty) {
        try (Connection conn = getConnection()) {
            String query = "INSERT INTO games (user_id, difficulty, current_day, player_cash, ai_cash, stress) " +
                    "VALUES (?, ?, 1, 10000.00, 10000.00, 0)";
            try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, difficulty);
                pstmt.executeUpdate();

                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int gameId = generatedKeys.getInt(1);
                        System.out.println("Game created: ID " + gameId + " for user " + userId);
                        return gameId;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error creating game: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Get game data for a specific game
     */
    public Map<String, Object> getGameData(int gameId) {
        Map<String, Object> gameData = new HashMap<>();
        try (Connection conn = getConnection()) {
            String query = "SELECT user_id, difficulty, current_day, player_cash, ai_cash, stress " +
                    "FROM games WHERE game_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, gameId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        gameData.put("user_id", rs.getInt("user_id"));
                        gameData.put("difficulty", rs.getString("difficulty"));
                        gameData.put("current_day", rs.getInt("current_day"));
                        gameData.put("player_cash", rs.getDouble("player_cash"));
                        gameData.put("ai_cash", rs.getDouble("ai_cash"));
                        gameData.put("stress", rs.getInt("stress"));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching game data: " + e.getMessage());
        }
        return gameData;
    }

    /**
     * Update game state
     */
    public boolean updateGameState(int gameId, int currentDay, double playerCash, double aiCash, int stress) {
        try (Connection conn = getConnection()) {
            String query = "UPDATE games SET current_day = ?, player_cash = ?, ai_cash = ?, stress = ? WHERE game_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, currentDay);
                pstmt.setDouble(2, playerCash);
                pstmt.setDouble(3, aiCash);
                pstmt.setInt(4, stress);
                pstmt.setInt(5, gameId);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error updating game state: " + e.getMessage());
        }
        return false;
    }

    /**
     * Update game's current day (used for marking incomplete days as completed on disconnect)
     */
    public boolean updateGameDay(int gameId, int newDay) {
        try (Connection conn = getConnection()) {
            String query = "UPDATE games SET current_day = ? WHERE game_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, newDay);
                pstmt.setInt(2, gameId);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error updating game day: " + e.getMessage());
        }
        return false;
    }

    /**
     * Get player portfolio for a game
     */
    public Map<Integer, Integer> getPlayerPortfolio(int gameId) {
        Map<Integer, Integer> portfolio = new HashMap<>();
        try (Connection conn = getConnection()) {
            String query = "SELECT stock_id, quantity FROM portfolios WHERE game_id = ? AND owner_type = 'player'";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, gameId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        portfolio.put(rs.getInt("stock_id"), rs.getInt("quantity"));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching player portfolio: " + e.getMessage());
        }
        return portfolio;
    }

    /**
     * Get AI portfolio for a game
     */
    public Map<Integer, Integer> getAIPortfolio(int gameId) {
        Map<Integer, Integer> portfolio = new HashMap<>();
        try (Connection conn = getConnection()) {
            String query = "SELECT stock_id, quantity FROM portfolios WHERE game_id = ? AND owner_type = 'ai'";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, gameId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        portfolio.put(rs.getInt("stock_id"), rs.getInt("quantity"));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching AI portfolio: " + e.getMessage());
        }
        return portfolio;
    }

    /**
     * Get player's holding quantity for a specific stock
     */
    public int getPlayerHolding(int gameId, int stockId) {
        try (Connection conn = getConnection()) {
            String query = "SELECT quantity FROM portfolios WHERE game_id = ? AND owner_type = 'player' AND stock_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, gameId);
                pstmt.setInt(2, stockId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("quantity");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching player holding: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Update player's cash
     */
    public boolean updatePlayerCash(int gameId, double newCash) {
        try (Connection conn = getConnection()) {
            String query = "UPDATE games SET player_cash = ? WHERE game_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setDouble(1, newCash);
                pstmt.setInt(2, gameId);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error updating player cash: " + e.getMessage());
        }
        return false;
    }

    /**
     * Update player's holding quantity for a specific stock
     */
    public boolean updatePlayerHolding(int gameId, int stockId, int newQuantity) {
        try (Connection conn = getConnection()) {
            // First check if record exists
            String checkQuery = "SELECT id FROM portfolios WHERE game_id = ? AND owner_type = 'player' AND stock_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setInt(1, gameId);
                checkStmt.setInt(2, stockId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        // Update existing record
                        String updateQuery = "UPDATE portfolios SET quantity = ? WHERE game_id = ? AND owner_type = 'player' AND stock_id = ?";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                            updateStmt.setInt(1, newQuantity);
                            updateStmt.setInt(2, gameId);
                            updateStmt.setInt(3, stockId);
                            return updateStmt.executeUpdate() > 0;
                        }
                    } else {
                        // Insert new record
                        String insertQuery = "INSERT INTO portfolios (game_id, owner_type, stock_id, quantity) VALUES (?, 'player', ?, ?)";
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                            insertStmt.setInt(1, gameId);
                            insertStmt.setInt(2, stockId);
                            insertStmt.setInt(3, newQuantity);
                            return insertStmt.executeUpdate() > 0;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error updating player holding: " + e.getMessage());
        }
        return false;
    }

    public boolean logTradeAction(int userId, int gameId, int day, String action, int stockId, int quantity, double price) {
        try (Connection conn = getConnection()) {
            String query = "INSERT INTO user_activity_log (user_id, game_id, day, action, stock_id, quantity, price) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, gameId);
                pstmt.setInt(3, day);
                pstmt.setString(4, action.toUpperCase());
                pstmt.setInt(5, stockId);
                pstmt.setInt(6, quantity);
                pstmt.setDouble(7, price);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error logging trade action: " + e.getMessage());
        }
        return false;
    }

    /**
     * Save day seed for a specific game, day, and stock
     */
    public boolean saveDaySeed(int gameId, int day, int stockId, String seedUUID) {
        try (Connection conn = getConnection()) {
            String query = "INSERT OR REPLACE INTO game_days (game_id, day, stock_id, seed) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, gameId);
                pstmt.setInt(2, day);
                pstmt.setInt(3, stockId);
                pstmt.setString(4, seedUUID);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error saving day seed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Get seed for a specific game, day, and stock
     */
    public String getDaySeed(int gameId, int day, int stockId) {
        try (Connection conn = getConnection()) {
            String query = "SELECT seed FROM game_days WHERE game_id = ? AND day = ? AND stock_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, gameId);
                pstmt.setInt(2, day);
                pstmt.setInt(3, stockId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("seed");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching day seed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get stock ID by symbol
     */
    public int getStockId(String symbol) {
        try (Connection conn = getConnection()) {
            String query = "SELECT stock_id FROM stocks WHERE symbol = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, symbol);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("stock_id");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching stock ID: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Get stock symbol by ID
     */
    public String getStockSymbol(int stockId) {
        try (Connection conn = getConnection()) {
            String query = "SELECT symbol FROM stocks WHERE stock_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, stockId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("symbol");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching stock symbol: " + e.getMessage());
        }
        return null;
    }

    /**
     * Delete all other active games for a user (keep only current game)
     */
    public boolean deleteOtherActiveGames(int userId, int keepGameId) {
        try (Connection conn = getConnection()) {
            String query = "UPDATE games SET active = 0 WHERE user_id = ? AND game_id != ? AND active = 1";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, keepGameId);
                pstmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error marking other games as inactive: " + e.getMessage());
        }
        return false;
    }

    /**
     * Delete a game from active games (mark as inactive)
     */
    public boolean deleteActiveGame(int gameId) {
        try (Connection conn = getConnection()) {
            String query = "UPDATE games SET active = 0 WHERE game_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, gameId);
                pstmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error marking game as inactive: " + e.getMessage());
        }
        return false;
    }

    /**
     * Get game final state (cash for both player and AI)
     */
    public Map<String, Object> getGameFinalState(int gameId) {
        Map<String, Object> gameState = new HashMap<>();
        try (Connection conn = getConnection()) {
            String query = "SELECT player_cash, ai_cash FROM games WHERE game_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, gameId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        gameState.put("player_cash", rs.getDouble("player_cash"));
                        gameState.put("ai_cash", rs.getDouble("ai_cash"));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching game final state: " + e.getMessage());
        }
        return gameState;
    }

    /**
     * Get AI holding for a specific stock
     */
    public int getAIHolding(int gameId, int stockId) {
        try (Connection conn = getConnection()) {
            String query = "SELECT quantity FROM portfolios WHERE game_id = ? AND owner_type = 'ai' AND stock_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, gameId);
                pstmt.setInt(2, stockId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("quantity");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching AI holding: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Update AI's cash
     */
    public boolean updateAICash(int gameId, double newCash) {
        try (Connection conn = getConnection()) {
            String query = "UPDATE games SET ai_cash = ? WHERE game_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setDouble(1, newCash);
                pstmt.setInt(2, gameId);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error updating AI cash: " + e.getMessage());
        }
        return false;
    }

    /**
     * Update AI's holding quantity for a specific stock
     */
    public boolean updateAIHolding(int gameId, int stockId, int newQuantity) {
        try (Connection conn = getConnection()) {
            // First check if record exists
            String checkQuery = "SELECT id FROM portfolios WHERE game_id = ? AND owner_type = 'ai' AND stock_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setInt(1, gameId);
                checkStmt.setInt(2, stockId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        // Update existing record
                        String updateQuery = "UPDATE portfolios SET quantity = ? WHERE game_id = ? AND owner_type = 'ai' AND stock_id = ?";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                            updateStmt.setInt(1, newQuantity);
                            updateStmt.setInt(2, gameId);
                            updateStmt.setInt(3, stockId);
                            return updateStmt.executeUpdate() > 0;
                        }
                    } else {
                        // Insert new record
                        String insertQuery = "INSERT INTO portfolios (game_id, owner_type, stock_id, quantity) VALUES (?, 'ai', ?, ?)";
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                            insertStmt.setInt(1, gameId);
                            insertStmt.setInt(2, stockId);
                            insertStmt.setInt(3, newQuantity);
                            return insertStmt.executeUpdate() > 0;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error updating AI holding: " + e.getMessage());
        }
        return false;
    }
}
