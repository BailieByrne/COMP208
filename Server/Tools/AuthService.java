// auth only now
public class AuthService {
    private final DBManager db;

    //onstructor
    public AuthService(DBManager db) {
        this.db = db;
    }

    // jwt shape check
    /**
     * TODO:
     * Probably going to replace JWT with a diff one
     * @param token
     * @return
     */
    public boolean isJwtShapeValid(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String[] parts = token.split("\\.");
        return parts.length == 3;
    }

    // user lookup
    public boolean userExists(String username) {
        return db.userExists(username);
    }

    // login auth + jwt set
    public boolean authenticateUser(String username, String passwordPlain, String jwt) {
        if (!userExists(username)) return false;
        if (!db.authenticateUser(username, passwordPlain)) return false;
        if (!isJwtShapeValid(jwt)) return false;
        db.setCurrentJwt(username, jwt);
        return true;
    }

    public boolean registerUser(String username, String passwordPlain, String jwt) {
        if (username == null || username.isBlank()) return false;
        if (passwordPlain == null || passwordPlain.isBlank()) return false;
        if (!isJwtShapeValid(jwt)) return false;
        return db.createUser(username.trim(), passwordPlain.trim(), jwt);
    }

    // for packet security checks
    public boolean validateJwtForUser(String username, String jwt) {
        if (!isJwtShapeValid(jwt)) return false;
        return db.validateCurrentJwt(username, jwt);
    }
}
