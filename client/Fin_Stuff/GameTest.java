import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
//Neccesary imports for Swing rendering, input handling, and JSON asset loading

/**
 * GameTest - Main Swing-based Tiled RPG game engine for Cycle 2 phase
 * Runs in a separate thread from JavaFX, manages game loop, player input, rendering, and interactions
 * Renders at 16ms intervals (60 FPS) with dynamic scaling to fit any window size
 */
public class GameTest extends JPanel implements ActionListener, KeyListener {
    
    // --- ENGINE SETTINGS ---
    private final int NATIVE_WIDTH = 320; // base game resolution width
    private final int NATIVE_HEIGHT = 240; // base game resolution height
    private final String STARTING_MAP = "client/Fin_Stuff/Cycle_two_testing/assets/Cycle_two_map.json"; // coffee shop map
    private final String TILESET_PATH = "client/Fin_Stuff/Cycle_two_testing/assets/master_tileset.png"; // tile graphics
    
    // --- GAME STATE ---
    private Timer timer; // 16ms game loop timer
    private Player player = new Player(); // the player character
    private Image tilesetImage; // loaded tileset image
    private List<int[][]> mapLayers = new ArrayList<>(); // all tile layers from map
    private int[][] mapGrid; // used for collisions and solid tile checks
    private int mapWidth, mapHeight; // dimensions of current map
    private int mapFirstGid = 1; // tiled firstgid offset for tiles
    
    private List<GameObject> interactables = new ArrayList<>(); // NPCs and doors
    private GameObject activeObject = null; // currently highlighted object
    private boolean hasCoffee = false; // did player get coffee from barista

    /**
     * Constructor - begining of the game. Sets up input handling, loads assets and starts the game loop
     * This runs in the Swing EDT thread, separate from JavaFX main thread
     */
    public GameTest() {
        setFocusable(true);
        addKeyListener(this); // listen for WASD and E key presses
        
        // Load Tileset image
        tilesetImage = new ImageIcon(TILESET_PATH).getImage(); 
        
        // Load initial Map from JSON
        loadMap(STARTING_MAP); 
        
        // Start game loop at 16ms intervals (60 fps)
        timer = new Timer(16, this); 
        timer.start();
    }

    /**
     * loadMap - parse tiled JSON map file and extract tiles, collisions, and interactable objects
     * Jackson ObjectMapper handles JSON parsing
     * Sets up all game world data b4 rendering starts
     */
    public void loadMap(String filePath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File(filePath));
            
            // Get map dimensions from JSON
            mapWidth = root.get("width").asInt();
            mapHeight = root.get("height").asInt();
            
            // Handle Tiled firstgid offset (important for tile ID mapping)
            if (root.has("tilesets") && root.get("tilesets").size() > 0) {
                mapFirstGid = root.get("tilesets").get(0).get("firstgid").asInt();
            }

            mapGrid = new int[mapHeight][mapWidth];
            mapLayers.clear(); // reset layers
            interactables.clear(); // reset NPCs/doors

            // Parse all layers from the JSON (tile layers + object layers)
            for (JsonNode layer : root.get("layers")) {
                String layerType = layer.get("type").asText();
                String layerName = layer.has("name") ? layer.get("name").asText() : "";

                // Handle Tile Layers (visual map tiles)
                if (layerType.equals("tilelayer")) {
                    int[][] newLayer = new int[mapHeight][mapWidth];
                    JsonNode data = layer.get("data");
                    for (int i = 0; i < data.size(); i++) {
                        int tileVal = data.get(i).asInt();
                        newLayer[i / mapWidth][i % mapWidth] = tileVal;
                        // Only use the collision layer for mapGrid collision detection
                        if (layerName.equals("Collision_Tiles")) {
                            mapGrid[i / mapWidth][i % mapWidth] = tileVal;
                        }
                    }
                    mapLayers.add(newLayer);
                } 
                
                // Handle Object Layers these are NPCs, doors, and spawn points
                else if (layerType.equals("objectgroup")) {
                    for (JsonNode obj : layer.get("objects")) {
                        int x = obj.get("x").asInt();
                        int y = obj.get("y").asInt();
                        String name = obj.has("name") ? obj.get("name").asText() : ""; // object name from tiled

                        // Handle Player Spawn Point
                        if (name.equals("player_spawn")) {
                            player.x = x;
                            player.y = y;
                            player.lastX = x;
                            player.lastY = y;
                            continue; 
                        }

                        // Handle Interactable Objects (Barista, Doors, etc.)
                        int w = obj.has("width") ? obj.get("width").asInt() : 16;
                        int h = obj.has("height") ? obj.get("height").asInt() : 16;
                        String msg = "Hello!";
                        String type = obj.has("type") ? obj.get("type").asText() : "npc";
                        String targetMap = "";
                        String thankMsg = "Enjoy!";
                        int sx = 0, sy = 0;

                        if (obj.has("properties")) {
                            for (JsonNode prop : obj.get("properties")) {
                                String pName = prop.get("name").asText().trim();
                                if (pName.equals("message")) msg = prop.get("value").asText();
                                if (pName.equals("type")) type = prop.get("value").asText();
                                if (pName.equals("targetMap")) targetMap = prop.get("value").asText();
                                if (pName.equals("spawnX")) sx = prop.get("value").asInt();
                                if (pName.equals("spawnY")) sy = prop.get("value").asInt();
                                if (pName.equals("thankYouMessage")) thankMsg = prop.get("value").asText();
                            }
                        }
                        
                        GameObject newObj = new GameObject(x, y, w, h, type, msg);
                        newObj.targetMap = targetMap;
                        newObj.spawnX = sx;
                        newObj.spawnY = sy;
                        newObj.thankYouMessage = thankMsg;

                        // Set sprites for Barista
                        if (name.equals("barista_spawn") || type.equals("npc")) {
                            newObj.sprite = new ImageIcon("client/Fin_Stuff/Cycle_two_testing/assets/barista_holding_cup.png").getImage();
                            newObj.altSprite = new ImageIcon("client/Fin_Stuff/Cycle_two_testing/assets/barista_stood.png").getImage();
                        }

                        interactables.add(newObj);
                    }
                }
            }
            System.out.println("SUCCESS: Loaded map " + filePath);
        } catch (Exception e) {
            System.err.println("Error loading map: " + e.getMessage()); // Map load failure - critical
            e.printStackTrace(); 
        }
    }

    /**
     * checkInteraction - determine which interactable object is closest to player (if any)
     * Uses distance calculation from player center to object center
     * 30px detection range for highlighting nearby NPCs/doors
     */
    public void checkInteraction() {
        activeObject = null;
        float detectionRange = 30.0f; // how close player needs to b4 highlighting

        for (GameObject obj : interactables) {
            float px = player.x + 8; // player center x
            float py = player.y + 8; // player center y
            float ox = obj.x + (obj.width / 2); // object center x
            float oy = obj.y + (obj.height / 2); // object center y

            double dist = Math.sqrt(Math.pow(px - ox, 2) + Math.pow(py - oy, 2)); // euclidean distance
            if (dist < detectionRange) {
                activeObject = obj;
                break; 
            }
        }
    }

    /**
     * paintComponent - main rendering method called every 16ms
     * Handles scaling, map rendering, object rendering, and player rendering
     * Dynamic scaling keeps 16x16 tiles crisp at any window size
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        
        // --- DYNAMIC SCALING ---
        // calculate scale factors to maintain aspect ratio
        double scaleX = (double) getWidth() / NATIVE_WIDTH;
        double scaleY = (double) getHeight() / NATIVE_HEIGHT;
        double finalScale = Math.min(scaleX, scaleY); // use smaller scale to fit window
        
        // center game on screen
        int xOffset = (int) ((getWidth() - (NATIVE_WIDTH * finalScale)) / 2);
        int yOffset = (int) ((getHeight() - (NATIVE_HEIGHT * finalScale)) / 2);
        
        g2d.translate(xOffset, yOffset);
        g2d.scale(finalScale, finalScale);

        // --- DRAW MAP TILES ---
        if (mapLayers != null && tilesetImage != null) {
            int imageWidth = tilesetImage.getWidth(null);
            if (imageWidth > 0) {
                int columns = imageWidth / 16; // tileset columns
                for (int[][] currentLayer : mapLayers) {
                    for (int row = 0; row < mapHeight; row++) {
                        for (int col = 0; col < mapWidth; col++) {
                            int tileID = currentLayer[row][col] - mapFirstGid; // convert to 0-based
                            if (tileID >= 0) {
                                int sx = (tileID % columns) * 16; // source x in tileset
                                int sy = (tileID / columns) * 16; // source y in tileset
                                g.drawImage(tilesetImage, col * 16, row * 16, col * 16 + 16, row * 16 + 16, sx, sy, sx + 16, sy + 16, this);
                            }
                        }
                    }
                }
            }
        }

        // --- DRAW NPCs AND OBJECTS ---
        for (GameObject obj : interactables) {
            if (obj.sprite != null) {
                int drawSize = 64; // npc sprite size
                int drawY = obj.y - (drawSize - 16); // offset for bottom alignment
                int drawX = obj.x - ((drawSize - 16) / 2); // center horizontally
                g.drawImage(obj.sprite, drawX, drawY, drawSize, drawSize, this);
            }
        }

        // --- DRAW UI ELEMENTS ---
        checkInteraction(); // update nearby object
        if (activeObject != null) {
            g.setColor(Color.YELLOW); // highlight yellow
            g.drawRect(activeObject.x, activeObject.y, activeObject.width, activeObject.height);
            g.setColor(Color.WHITE);
            g.drawString("[E]", activeObject.x + (activeObject.width / 2) - 5, activeObject.y - 5); // press E hint
        }

        // --- DRAW PLAYER CHARACTER ---
        player.draw(g); 
    }



    /**
     * keyPressed - handle player input (WASD movement, E interaction)
     * Called whenever a key is pressed, update player position and check interactions
     */
    @Override
    public void keyPressed(KeyEvent e) {
        int speed = 4; // pixels per keypress
        int nextX = player.x, nextY = player.y;

        // WASD movement keys
        if (e.getKeyCode() == KeyEvent.VK_W) nextY -= speed; // up
        if (e.getKeyCode() == KeyEvent.VK_S) nextY += speed; // down
        if (e.getKeyCode() == KeyEvent.VK_A) nextX -= speed; // left
        if (e.getKeyCode() == KeyEvent.VK_D) nextX += speed; // right

        // check if movement is valid (no collision with walls or boundaries)
        if (player.canMove(nextX, nextY, mapGrid, mapWidth, mapHeight)) {
            player.x = nextX; player.y = nextY;
        }

        // --- INTERACTION WITH NPCS/DOORS ---
        if (e.getKeyCode() == KeyEvent.VK_E && activeObject != null) {
            if (activeObject.type.equals("npc")) {
                if (!hasCoffee) {
                    JOptionPane.showMessageDialog(this, activeObject.message); // show barista message
                    hasCoffee = true; // player got the coffee!
                    // TODO: Send to db that we have coffee

                    // if (hasCoffee){
                    //     System.out.println("hasCoffee == TRUE"); // debug: confirm flag
                    // }
                    if (activeObject.altSprite != null) activeObject.sprite = activeObject.altSprite; // swap barista sprite
                } else {
                    JOptionPane.showMessageDialog(this, activeObject.thankYouMessage); // already have coffee message
                }
            } 
            else if (activeObject.type.equals("door")) {
                // load new map and respawn player at target location
                loadMap("client/Fin_Stuff/Cycle_two_testing/assets/" + activeObject.targetMap);
                player.x = activeObject.spawnX;
                player.y = activeObject.spawnY;
            }
        }
    }

    // ignore unused keyboard events
    @Override public void keyTyped(KeyEvent e) {} 
    @Override public void keyReleased(KeyEvent e) {} 
    /**
     * actionPerformed - called every 16ms by the timer, triggers repaint
     */
    @Override public void actionPerformed(ActionEvent e) { repaint(); }

    /**
     * stopGame - stop the game loop timer when cycle 2 ends
     * Called by Cycle2GameController after 60 seconds or manual game end
     */
    public void stopGame() {
        if (timer != null) {
            timer.stop(); // stop rendering
        }
    }

    /**
     * main - standalone launcher for testing the game (debugging only)
     * In production this is embedded via Cycle2GameController + SwingNode
     */
    public static void main(String[] args) {
        JFrame frame = new JFrame("Coffee Shop RPG");
        GameTest game = new GameTest();
        frame.add(game);
        frame.setSize(960, 720); // window size
        frame.setResizable(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
