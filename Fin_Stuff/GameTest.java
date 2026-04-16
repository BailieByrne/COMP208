import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GameTest extends JPanel implements ActionListener, KeyListener {
    Timer timer;
    Player player = new Player();
    int[][] mapGrid;
    List<int[][]> mapLayers = new ArrayList<>(); // The stack of layers
    int mapWidth, mapHeight;
    Image tilesetImage;
    List<GameObject> interactables = new ArrayList<>();
    GameObject activeObject = null; 

    // Define initial setup variables here
    private final String STARTING_MAP = "Fin_Stuff/Cycle_two_testing/assets/testing_map_v1.json";
    private final String TILESET_PATH = "Fin_Stuff/Cycle_two_testing/assets/CP_V1.1.0_nyknck/CP_V1.1.0_nyknck/CP_V1.0.4_nyknck/CP_V1.0.4.png";

    public GameTest() {
        setFocusable(true);
        addKeyListener(this); // Listen to keyboard inputs

        // FIXED: Using ImageIcon forces Java to load the image immediately
        tilesetImage = new ImageIcon(TILESET_PATH).getImage();
        
        loadMap(STARTING_MAP); // Load first map automatically

        timer = new Timer(16, this); 
        timer.start();
    }

    public void loadMap(String filePath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File(filePath));

            mapWidth = root.get("width").asInt();
            mapHeight = root.get("height").asInt();
            
            // Clear out old map data
            mapGrid = new int[mapHeight][mapWidth];
            mapLayers.clear(); 

            for (JsonNode layer : root.get("layers")) {
                String layerType = layer.get("type").asText();

                if (layerType.equals("tilelayer")) {
                    // FIXED: Create a fresh layer array for the drawing logic
                    int[][] newLayer = new int[mapHeight][mapWidth];
                    JsonNode data = layer.get("data");
                    
                    for (int i = 0; i < data.size(); i++) {
                        int tileVal = data.get(i).asInt();
                        newLayer[i / mapWidth][i % mapWidth] = tileVal; // Save to visual layer
                        
                        // Save solid tiles to the master grid for Player collision
                        if (tileVal != 0) {
                            mapGrid[i / mapWidth][i % mapWidth] = tileVal;
                        }
                    }
                    // FIXED: Actually add the layer to our list so it gets drawn!
                    mapLayers.add(newLayer);
                } 
                
                if (layerType.equals("objectgroup")) {
                    interactables.clear(); // Clear old objects when switching maps
                    for (JsonNode obj : layer.get("objects")) {
                        int x = obj.get("x").asInt();
                        int y = obj.get("y").asInt();
                        int w = obj.has("width") && obj.get("width").asInt() > 0 ? obj.get("width").asInt() : 16;
                        int h = obj.has("height") && obj.get("height").asInt() > 0 ? obj.get("height").asInt() : 16;

                        String msg = "";
                        String type = "sign";
                        String targetMap = "";
                        int spawnX = 0;
                        int spawnY = 0;

                        if (obj.has("properties")) {
                            for (JsonNode prop : obj.get("properties")) {
                                String pName = prop.get("name").asText();
                                if (pName.equals("message")) msg = prop.get("value").asText();
                                if (pName.equals("type")) type = prop.get("value").asText();
                                if (pName.equals("targetMap")) targetMap = prop.get("value").asText();
                                if (pName.equals("spawnX")) spawnX = prop.get("value").asInt();
                                if (pName.equals("spawnY")) spawnY = prop.get("value").asInt();
                            }
                        }
                        
                        GameObject newObj = new GameObject(x, y, w, h, type, msg);
                        newObj.targetMap = targetMap;
                        newObj.spawnX = spawnX;
                        newObj.spawnY = spawnY;
                        interactables.add(newObj);
                    }
                }
            }
            System.out.println("SUCCESS: Loaded map " + filePath + " with " + mapLayers.size() + " visual layers and " + interactables.size() + " objects.");
        } catch (Exception e) {
            System.out.println("ERROR: Map Load Failed - " + e.getMessage());
        }
    }

    public void checkInteraction() {
        activeObject = null; // Reset every frame
        float detectionRange = 30.0f; 

        for (GameObject obj : interactables) {
            float px = player.x + 8; // Center of 16px player
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
        
        // 1. Draw Map (Now supporting multiple layers!)
        if (mapLayers != null && !mapLayers.isEmpty() && tilesetImage != null) {
            // Wait for image to load to avoid divide-by-zero errors
            int imageWidth = tilesetImage.getWidth(null);
            if (imageWidth > 0) {
                int columns = imageWidth / 16; 
                
                // Loop through EVERY layer we saved (e.g., Layer 0: Roads, Layer 1: Cars)
                for (int[][] currentLayer : mapLayers) {
                    for (int row = 0; row < mapHeight; row++) {
                        for (int col = 0; col < mapWidth; col++) {
                            int tileID = currentLayer[row][col] - 1; 
                            if (tileID >= 0) {
                                int sx = (tileID % columns) * 16; 
                                int sy = (tileID / columns) * 16;
                                
                                // Draw exactly 16x16 slices 
                                g.drawImage(tilesetImage, 
                                    col * 16, row * 16, (col * 16) + 16, (row * 16) + 16, 
                                    sx, sy, sx + 16, sy + 16, this);
                            }
                        }
                    }
                }
            }
        }

        // 2. Draw Interaction Highlights
        checkInteraction();
        if (activeObject != null) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setStroke(new BasicStroke(3));
            g2d.setColor(Color.YELLOW);
            g2d.drawRect(activeObject.x, activeObject.y, activeObject.width, activeObject.height);

            g2d.setColor(Color.WHITE);
            g2d.drawString("[E]", activeObject.x + (activeObject.width / 2) - 5, activeObject.y - 5);
        }

        // 3. Draw Player
        player.draw(g); 
    }

    // --- INPUT HANDLING ---
    @Override
    public void keyPressed(KeyEvent e) {
        // Handle Movement
        int speed = 4;
        int nextX = player.x;
        int nextY = player.y;

        if (e.getKeyCode() == KeyEvent.VK_W) nextY -= speed;
        if (e.getKeyCode() == KeyEvent.VK_S) nextY += speed;
        if (e.getKeyCode() == KeyEvent.VK_A) nextX -= speed;
        if (e.getKeyCode() == KeyEvent.VK_D) nextX += speed;

        if (player.canMove(nextX, nextY, mapGrid)) {
            player.x = nextX;
            player.y = nextY;
        }

        // Handle Interactions
        if (e.getKeyCode() == KeyEvent.VK_E) {
            if (activeObject != null) {
                if (activeObject.type.equals("door")) {
                    System.out.println("Teleporting to: " + activeObject.targetMap);
                    String mapName = activeObject.targetMap;
                    if(!mapName.endsWith(".json")){
                        mapName += ".json";
                    }
                    
                    String nextMapPath = "Fin_Stuff/Cycle_two_testing/assets/" + mapName;
                    loadMap(nextMapPath);
                    player.x = activeObject.spawnX;
                    player.y = activeObject.spawnY;
                } else if (activeObject.type.equals("sign")) {
                    JOptionPane.showMessageDialog(this, activeObject.message);
                }
            }
        }
    }

    @Override public void keyTyped(KeyEvent e) {} 
    @Override public void keyReleased(KeyEvent e) {} 
    @Override public void actionPerformed(ActionEvent e) { repaint(); }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Tiled City Test");
        frame.add(new GameTest());
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}