import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// db stuff lives here
public class DBManager {
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_LEN = 12;
    // DEV KEY. swap this from env in prod
    private static final String DEV_KEY_SOURCE = "COMP208_DEV_AES_256_KEY_CHANGE_ME";

    private final String dbPath;

    public DBManager(String dbPath) {
        this.dbPath = dbPath;
        loadSqliteDriver();
        initSchema();
    }

    private void loadSqliteDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found on classpath.");
        }
    }

    private void initSchema() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement()) {

            // users table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                " user_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " username TEXT UNIQUE NOT NULL," +
                " password_enc TEXT NOT NULL," +
                " current_jwt TEXT" +
                ")"
            );

            // current game table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS current_games (" +
                " user_id INTEGER PRIMARY KEY," +
                " difficulty INTEGER NOT NULL DEFAULT 2," +
                " day INTEGER NOT NULL DEFAULT 1," +
                " game_status TEXT NOT NULL DEFAULT 'IN_PROGRESS'," +
                " completed_day INTEGER NOT NULL DEFAULT 0," +
                " capital REAL NOT NULL DEFAULT 10000.0," +
                " aapl_amount INTEGER NOT NULL DEFAULT 0," +
                " tsla_amount INTEGER NOT NULL DEFAULT 0," +
                " nvda_amount INTEGER NOT NULL DEFAULT 0," +
                " googl_amount INTEGER NOT NULL DEFAULT 0," +
                " amzn_amount INTEGER NOT NULL DEFAULT 0," +
                " stress_level REAL NOT NULL DEFAULT 0.0," +
                " FOREIGN KEY(user_id) REFERENCES users(user_id)" +
                ")"
            );

            ensureCurrentGamesColumn(conn, "game_status", "TEXT NOT NULL DEFAULT 'IN_PROGRESS'");
            ensureCurrentGamesColumn(conn, "completed_day", "INTEGER NOT NULL DEFAULT 0");
            normalizeLegacyGameState(conn);

            // game info + seeds + activity
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS game_info (" +
                " user_id INTEGER PRIMARY KEY," +
                " day_1_seed TEXT," +
                " day_2_seed TEXT," +
                " day_3_seed TEXT," +
                " day_4_seed TEXT," +
                " day_5_seed TEXT," +
                " activity TEXT DEFAULT ''," +
                " FOREIGN KEY(user_id) REFERENCES users(user_id)" +
                ")"
            );

            // make one dev user if empty
            ensureDevUser(conn, "player1", "password");
            ensureDevUser(conn, "Bob", "password");
        } catch (Exception e) {
            // keep quiet in dev mode
        }
    }

    private void ensureCurrentGamesColumn(Connection conn, String columnName, String columnDefinition) {
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(current_games)");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        } catch (Exception ignored) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE current_games ADD COLUMN " + columnName + " " + columnDefinition);
        } catch (Exception ignored) {
        }
    }

    private void normalizeLegacyGameState(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE current_games SET game_status='COMPLETED', completed_day=day WHERE day >= 5 AND (game_status IS NULL OR game_status='' OR game_status='IN_PROGRESS')");
            stmt.executeUpdate("UPDATE current_games SET game_status='IN_PROGRESS', completed_day=0 WHERE day < 5 AND (game_status IS NULL OR game_status='')");
        } catch (Exception ignored) {
        }
    }

    private void ensureDevUser(Connection conn, String username, String password) {
        try (PreparedStatement check = conn.prepareStatement("SELECT user_id FROM users WHERE username=?")) {
            check.setString(1, username);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        } catch (Exception ignored) {
            return;
        }

        try (PreparedStatement ins = conn.prepareStatement(
            "INSERT INTO users(username,password_enc,current_jwt) VALUES(?,?,?)")) {
            ins.setString(1, username);
            ins.setString(2, encryptPassword(password));
            ins.setString(3, "header.payload.signature");
            ins.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private SecretKeySpec getAesKey() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(DEV_KEY_SOURCE.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

    // encrypt pass with aes-256 gcm
    public String encryptPassword(String passwordPlain) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_MODE);
        byte[] iv = new byte[GCM_IV_LEN];
        java.security.SecureRandom.getInstanceStrong().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, getAesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ct = cipher.doFinal(passwordPlain.getBytes(StandardCharsets.UTF_8));

        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.getEncoder().encodeToString(out);
    }

    public String decryptPassword(String passwordEnc) throws Exception {
        byte[] in = Base64.getDecoder().decode(passwordEnc);
        byte[] iv = new byte[GCM_IV_LEN];
        byte[] ct = new byte[in.length - GCM_IV_LEN];
        System.arraycopy(in, 0, iv, 0, GCM_IV_LEN);
        System.arraycopy(in, GCM_IV_LEN, ct, 0, ct.length);

        Cipher cipher = Cipher.getInstance(AES_MODE);
        cipher.init(Cipher.DECRYPT_MODE, getAesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plain = cipher.doFinal(ct);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public boolean userExists(String username) {
        if (username == null || username.isBlank()) return false;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM users WHERE username=? LIMIT 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return username.length() >= 3;
        }
    }

    public boolean authenticateUser(String username, String passwordPlain) {
        if (username == null || username.isBlank()) return false;
        if (passwordPlain == null || passwordPlain.isBlank()) return false;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement("SELECT password_enc FROM users WHERE username=? LIMIT 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String enc = rs.getString("password_enc");
                String plain = decryptPassword(enc);
                return passwordPlain.equals(plain);
            }
        } catch (Exception e) {
            // fallback dev auth
            return userExists(username);
        }
    }

    public boolean createUser(String username, String passwordPlain, String jwt) {
        if (username == null || username.isBlank()) return false;
        if (passwordPlain == null || passwordPlain.isBlank()) return false;
        if (userExists(username)) return false;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO users(username,password_enc,current_jwt) VALUES(?,?,?)")) {
            ps.setString(1, username.trim());
            ps.setString(2, encryptPassword(passwordPlain.trim()));
            ps.setString(3, jwt);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean setCurrentJwt(String username, String jwt) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement("UPDATE users SET current_jwt=? WHERE username=?")) {
            ps.setString(1, jwt);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateCurrentJwt(String username, String jwt) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement("SELECT current_jwt FROM users WHERE username=? LIMIT 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String stored = rs.getString("current_jwt");
                return stored != null && stored.equals(jwt);
            }
        } catch (Exception e) {
            return jwt != null && !jwt.isBlank();
        }
    }

    public Integer getUserId(String username) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM users WHERE username=? LIMIT 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("user_id");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public GameState getGameState(String username) {
        Integer uid = getUserId(username);
        if (uid == null) return null;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT difficulty, day, game_status, completed_day, capital, aapl_amount, tsla_amount, nvda_amount, googl_amount, amzn_amount, stress_level FROM current_games WHERE user_id=? LIMIT 1")) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new GameState(
                    uid,
                    username,
                    rs.getInt("day"),
                    rs.getInt("difficulty"),
                    rs.getString("game_status"),
                    rs.getInt("completed_day"),
                    rs.getDouble("capital"),
                    rs.getInt("aapl_amount"),
                    rs.getInt("tsla_amount"),
                    rs.getInt("nvda_amount"),
                    rs.getInt("googl_amount"),
                    rs.getInt("amzn_amount"),
                    rs.getDouble("stress_level")
                );
            }
        } catch (Exception e) {
            return null;
        }
    }

    public GameState createOrResetGame(String username, int difficulty) {
        Integer uid = getUserId(username);
        if (uid == null) return null;
        if (difficulty < 1 || difficulty > 4) difficulty = 2;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO current_games(user_id,difficulty,day,game_status,completed_day,capital,aapl_amount,tsla_amount,nvda_amount,googl_amount,amzn_amount,stress_level) " +
                "VALUES(?,?,1,'IN_PROGRESS',0,10000.0,0,0,0,0,0,0.0) " +
                "ON CONFLICT(user_id) DO UPDATE SET difficulty=excluded.difficulty, day=1, game_status='IN_PROGRESS', completed_day=0, capital=10000.0, aapl_amount=0, tsla_amount=0, nvda_amount=0, googl_amount=0, amzn_amount=0, stress_level=0.0")) {
            ps.setInt(1, uid);
            ps.setInt(2, difficulty);
            ps.executeUpdate();
        } catch (Exception e) {
            return null;
        }

        // ensure game info row
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO game_info(user_id,activity) VALUES(?, '') ON CONFLICT(user_id) DO NOTHING")) {
            ps.setInt(1, uid);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }

        return getGameState(username);
    }

    public boolean setGameStatus(int userId, String gameStatus, int completedDay) {
        if (gameStatus == null || gameStatus.isBlank()) {
            return false;
        }

        String normalizedStatus = gameStatus.trim().toUpperCase();
        if (!"IN_PROGRESS".equals(normalizedStatus) && !"COMPLETED".equals(normalizedStatus)) {
            return false;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE current_games SET game_status=?, completed_day=? WHERE user_id=?")) {
            ps.setString(1, normalizedStatus);
            ps.setInt(2, Math.max(0, completedDay));
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean advanceToNextDay(int userId) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement("UPDATE current_games SET day=day+1 WHERE user_id=?")) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean setDaySeed(int userId, int day, String seed) {
        if (day < 1 || day > 5) return false;
        String col = "day_" + day + "_seed";
        String sql = "UPDATE game_info SET " + col + "=? WHERE user_id=?";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, seed);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public String getDaySeed(int userId, int day) {
        if (day < 1 || day > 5) return null;
        String col = "day_" + day + "_seed";
        String sql = "SELECT " + col + " FROM game_info WHERE user_id=? LIMIT 1";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(col);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // format {DAY,ACTION,AMOUNT}
    public boolean appendActivity(int userId, int day, String action, double amount) {
        String row = "{" + day + "," + action + "," + amount + "}";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE game_info SET activity = CASE WHEN activity IS NULL OR activity='' THEN ? ELSE activity || ';' || ? END WHERE user_id=?")) {
            ps.setString(1, row);
            ps.setString(2, row);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public String getTickerForDay(int day) {
        String[] tickers = { "AAPL", "TSLA", "NVDA", "GOOGL", "AMZN" };
        if (day < 1 || day > 5) return null;
        return tickers[day - 1];
    }

    public static class GameState {
        public int userId;
        public String username;
        public int currentDay;
        public int difficulty;
        public String gameStatus;
        public int completedDay;
        public double capital;
        public int aaplAmount;
        public int tslaAmount;
        public int nvdaAmount;
        public int googlAmount;
        public int amznAmount;
        public double stressLevel;

        public GameState(int userId, String username, int currentDay, int difficulty, double capital,
                         int aaplAmount, int tslaAmount, int nvdaAmount, int googlAmount, int amznAmount, double stressLevel) {
            this(userId, username, currentDay, difficulty, "IN_PROGRESS", 0, capital, aaplAmount, tslaAmount, nvdaAmount, googlAmount, amznAmount, stressLevel);
        }

        public GameState(int userId, String username, int currentDay, int difficulty, String gameStatus, int completedDay, double capital,
                         int aaplAmount, int tslaAmount, int nvdaAmount, int googlAmount, int amznAmount, double stressLevel) {
            this.userId = userId;
            this.username = username;
            this.currentDay = currentDay;
            this.difficulty = difficulty;
            this.gameStatus = gameStatus == null || gameStatus.isBlank() ? "IN_PROGRESS" : gameStatus;
            this.completedDay = Math.max(0, completedDay);
            this.capital = capital;
            this.aaplAmount = aaplAmount;
            this.tslaAmount = tslaAmount;
            this.nvdaAmount = nvdaAmount;
            this.googlAmount = googlAmount;
            this.amznAmount = amznAmount;
            this.stressLevel = stressLevel;
        }
    }
}
