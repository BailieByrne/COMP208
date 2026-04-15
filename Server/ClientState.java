// ClientState to hold the current powerups etc
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO:
 * -HERE WE NEED TO ADD THE DB INTEGRATION AND LINK CLIENT IP/LOGIN TO RECORDS
 * -THEN WE CHECK THE INVENTORY JSON STRING FOR POWERUP AMOUNT
 * - SEVER ACTS AS TRUTH FOR POWERUP ACTIVATION#
 */

public class ClientState {
    private final int clientID;
    private final Map<String, Long> activePowerups = new ConcurrentHashMap<>();

    // Powerup durations in milliseconds
    //10,000 MS
    public static final long COFFEE_DURATION_MS = 10_000;

    public ClientState(int clientID) {
        this.clientID = clientID;
    }

    public void addPowerup(String name, long durationMs) {
        activePowerups.put(name, System.currentTimeMillis() + durationMs);
    }

    // checks expiry inline no separate cleanup thread 
    public boolean hasPowerup(String name) {
        Long expiry = activePowerups.get(name);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            activePowerups.remove(name);
            return false;
        }
        return true;
    }

    public void removePowerup(String name) {
        activePowerups.remove(name);
    }

    public int getClientID() { return clientID; }
}