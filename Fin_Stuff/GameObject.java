import java.awt.Rectangle;

public class GameObject {

    public int x, y, width, height;
    public String type;    // "sign", "door", "npc"
    public String message; // The text it displays or the map it loads

    public int spawnX; 
    public int spawnY;
    public String targetMap;

    public GameObject(int x, int y, int width, int height, String type, String message) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;
        this.message = message;
    }

    // Helps us check if the player is nearby
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    }



