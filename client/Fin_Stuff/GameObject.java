import java.awt.Image;
import java.awt.Rectangle;
//Neccesary imports for rendering game objects and collision detection

/**
 * GameObject class, represents NPCs, doors, and interactable objects in the tiled game world
 * Each object has position, dimensions, sprite, and interaction data
 */
public class GameObject {

    public int x, y, width, height;
    public String type;    
    public String message; 
    public String thankYouMessage; 
    public Image sprite;
    public Image altSprite;

    public int spawnX;
    public int spawnY;
    public String targetMap;

    /**
     *Constructs a new GameObject with position, size, type and message
     *This is the basic data holder for game world interactables
     */
    public GameObject(int x, int y, int width, int height, String type, String message) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;
        this.message = message;
    }

    /**
     * getBounds creates collision rect for this object, helps us check if player is nearby or touching
     * Used for interaction detection
     */
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }
}
