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

public class GameTest extends JPanel implements ActionListener, KeyListener {
    
    // --- ENGINE SETTINGS ---
    private final int NATIVE_WIDTH = 320; 
    private final int NATIVE_HEIGHT = 240;
    private final String STARTING_MAP = "Fin_Stuff/Cycle_two_testing/assets/Cycle_two_map.json";
    private final String TILESET_PATH = "Fin_Stuff/Cycle_two_testing/assets/master_tileset.png";
    
    // --- GAME STATE ---
    private Timer timer;
    private Player player = new Player();
    private Image tilesetImage;
    private List<int[][]> mapLayers = new ArrayList<>(); 
    private int[][] mapGrid; // Used for collisions
    private int mapWidth, mapHeight;
    private int mapFirstGid = 1; 
    
    private List<GameObject> interactables = new ArrayList<>();
    private GameObject activeObject = null; 
    private boolean hasCoffee = false;

    public GameTest() {
        setFocusable(true);
        addKeyListener(this); 
        
        // Load Tileset
        tilesetImage = new ImageIcon(TILESET_PATH).getImage(); 
        
        // Load initial Map
        loadMap(STARTING_MAP); 
        
        // Start Game Loop
        timer = new Timer(16, this); 
        timer.start();
    }

    public void loadMap(String filePath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File(filePath));
            
            // 1. Map Dimensions
            mapWidth = root.get("width").asInt();
            mapHeight = root.get("height").asInt();
            
            // 2. Handle Tiled ID Offsets (firstgid)
            if (root.has("tilesets") && root.get("tilesets").size() > 0) {
                mapFirstGid = root.get("tilesets").get(0).get("firstgid").asInt();
            }

            mapGrid = new int[mapHeight][mapWidth];
            mapLayers.clear(); 
            interactables.clear();

            // 3. Parse Layers
            for (JsonNode layer : root.get("layers")) {
                String layerType = layer.get("type").asText();

                // Handle Tile Layers
                if (layerType.equals("tilelayer")) {
                    int[][] newLayer = new int[mapHeight][mapWidth];
                    JsonNode data = layer.get("data");
                    for (int i = 0; i < data.size(); i++) {
                        int tileVal = data.get(i).asInt();
                        newLayer[i / mapWidth][i % mapWidth] = tileVal;
                        if (tileVal != 0) {
                            mapGrid[i / mapWidth][i % mapWidth] = tileVal;
                        }
                    }
                    mapLayers.add(newLayer);
                } 
                
                // Handle Object Layers (NPCs and Spawns)
                else if (layerType.equals("objectgroup")) {
                    for (JsonNode obj : layer.get("objects")) {
                        int x = obj.get("x").asInt();
                        int y = obj.get("y").asInt();
                        String name = obj.has("name") ? obj.get("name").asText() : "";

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
                            newObj.sprite = new ImageIcon("Fin_Stuff/Cycle_two_testing/assets/barista_holding_cup.png").getImage();
                            newObj.altSprite = new ImageIcon("Fin_Stuff/Cycle_two_testing/assets/barista_stood.png").getImage();
                        }

                        interactables.add(newObj);
                    }
                }
            }
            System.out.println("SUCCESS: Loaded map " + filePath);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void checkInteraction() {
        activeObject = null;
        float detectionRange = 30.0f; 

        for (GameObject obj : interactables) {
            float px = player.x + 8;
            float py = player.y + 8;
            float ox = obj.x + (obj.width / 2);
            float oy = obj.y + (obj.height / 2);

            double dist = Math.sqrt(Math.pow(px - ox, 2) + Math.pow(py - oy, 2));
            if (dist < detectionRange) {
                activeObject = obj;
                break; 
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        
        // --- DYNAMIC SCALING ---
        double scaleX = (double) getWidth() / NATIVE_WIDTH;
        double scaleY = (double) getHeight() / NATIVE_HEIGHT;
        double finalScale = Math.min(scaleX, scaleY);
        
        int xOffset = (int) ((getWidth() - (NATIVE_WIDTH * finalScale)) / 2);
        int yOffset = (int) ((getHeight() - (NATIVE_HEIGHT * finalScale)) / 2);
        
        g2d.translate(xOffset, yOffset);
        g2d.scale(finalScale, finalScale);

        // --- DRAW MAP ---
        if (mapLayers != null && tilesetImage != null) {
            int imageWidth = tilesetImage.getWidth(null);
            if (imageWidth > 0) {
                int columns = imageWidth / 16; 
                for (int[][] currentLayer : mapLayers) {
                    for (int row = 0; row < mapHeight; row++) {
                        for (int col = 0; col < mapWidth; col++) {
                            int tileID = currentLayer[row][col] - mapFirstGid; 
                            if (tileID >= 0) {
                                int sx = (tileID % columns) * 16; 
                                int sy = (tileID / columns) * 16;
                                g.drawImage(tilesetImage, col * 16, row * 16, col * 16 + 16, row * 16 + 16, sx, sy, sx + 16, sy + 16, this);
                            }
                        }
                    }
                }
            }
        }

        // --- DRAW INTERACTABLES (BIGGER BARISTA) ---
        for (GameObject obj : interactables) {
            if (obj.sprite != null) {
                int drawSize = 64; // Change this to 48 if you want her even bigger
                int drawY = obj.y - (drawSize - 16);
                int drawX = obj.x - ((drawSize - 16) / 2);
                g.drawImage(obj.sprite, drawX, drawY, drawSize, drawSize, this);
            }
        }

        // --- DRAW UI ---
        checkInteraction();
        if (activeObject != null) {
            g.setColor(Color.YELLOW);
            g.drawRect(activeObject.x, activeObject.y, activeObject.width, activeObject.height);
            g.setColor(Color.WHITE);
            g.drawString("[E]", activeObject.x + (activeObject.width / 2) - 5, activeObject.y - 5);
        }

        // --- DRAW PLAYER ---
        player.draw(g); 
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int speed = 4;
        int nextX = player.x, nextY = player.y;

        if (e.getKeyCode() == KeyEvent.VK_W) nextY -= speed;
        if (e.getKeyCode() == KeyEvent.VK_S) nextY += speed;
        if (e.getKeyCode() == KeyEvent.VK_A) nextX -= speed;
        if (e.getKeyCode() == KeyEvent.VK_D) nextX += speed;

        if (player.canMove(nextX, nextY, mapGrid)) {
            player.x = nextX; player.y = nextY;
        }

        // --- INTERACTION LOGIC ---
        if (e.getKeyCode() == KeyEvent.VK_E && activeObject != null) {
            if (activeObject.type.equals("npc")) {
                if (!hasCoffee) {
                    JOptionPane.showMessageDialog(this, activeObject.message);
                    hasCoffee = true;
                    if (hasCoffee){
                        System.out.println("hasCoffee == TRUE");
                    }
                    if (activeObject.altSprite != null) activeObject.sprite = activeObject.altSprite;
                } else {
                    JOptionPane.showMessageDialog(this, activeObject.thankYouMessage);
                }
            } 
            else if (activeObject.type.equals("door")) {
                loadMap("Fin_Stuff/Cycle_two_testing/assets/" + activeObject.targetMap);
                // The spawnX/Y comes from Tiled properties on the Door object
                player.x = activeObject.spawnX;
                player.y = activeObject.spawnY;
            }
        }
    }

    @Override public void keyTyped(KeyEvent e) {} 
    @Override public void keyReleased(KeyEvent e) {} 
    @Override public void actionPerformed(ActionEvent e) { repaint(); }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Coffee Shop RPG");
        GameTest game = new GameTest();
        frame.add(game);
        frame.setSize(960, 720); 
        frame.setResizable(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}