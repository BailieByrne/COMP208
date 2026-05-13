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
    
    //base resolution
    private final int NATIVE_WIDTH = 320;
    private final int NATIVE_HEIGHT = 240; 
    private final String STARTING_MAP = "client/Fin_Stuff/Cycle_two_testing/assets/Cycle_two_map.json";
    private final String TILESET_PATH = "client/Fin_Stuff/Cycle_two_testing/assets/master_tileset.png";
    
    private Timer timer;
    private Player player = new Player(); 
    private Image tilesetImage; 
    private List<int[][]> mapLayers = new ArrayList<>(); 
    private int[][] mapGrid; 
    private int mapWidth, mapHeight; 
    private int mapFirstGid = 1;
    private List<GameObject> interactables = new ArrayList<>(); 
    private GameObject activeObject = null; 
    private boolean hasCoffee = false;

    public GameTest() {
        setFocusable(true);
        addKeyListener(this);
        // Load the tileset
        tilesetImage = new ImageIcon(TILESET_PATH).getImage(); 
        loadMap(STARTING_MAP); 
        timer = new Timer(16, this); //60fps roughly
        timer.start();
    }

    public void loadMap(String filePath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File(filePath));
            mapWidth = root.get("width").asInt();
            mapHeight = root.get("height").asInt();
            
            if (root.has("tilesets") && root.get("tilesets").size() > 0) mapFirstGid = root.get("tilesets").get(0).get("firstgid").asInt();

            mapGrid = new int[mapHeight][mapWidth];
            mapLayers.clear();
            interactables.clear();

            for (JsonNode layer : root.get("layers")) {
                String layerType = layer.get("type").asText();
                String layerName = layer.has("name") ? layer.get("name").asText() : "";

                if (layerType.equals("tilelayer")) {
                    int[][] newLayer = new int[mapHeight][mapWidth];
                    JsonNode data = layer.get("data");
                    for (int i = 0; i < data.size(); i++) {
                        int tileVal = data.get(i).asInt();
                        newLayer[i / mapWidth][i % mapWidth] = tileVal;
                        //check for collision layer
                        if (layerName.equals("Collision_Tiles")) mapGrid[i / mapWidth][i % mapWidth] = tileVal;
                    }
                    mapLayers.add(newLayer);
                } 
                else if (layerType.equals("objectgroup")) {
                    for (JsonNode obj : layer.get("objects")) {
                        int x = obj.get("x").asInt();
                        int y = obj.get("y").asInt();
                        String name = obj.has("name") ? obj.get("name").asText() : "";

                        if (name.equals("player_spawn")) {
                            player.x = x; player.y = y;
                            player.lastX = x; player.lastY = y;
                            continue; 
                        }

                        int w = obj.has("width") ? obj.get("width").asInt() : 8;
                        int h = obj.has("height") ? obj.get("height").asInt() : 8;
                        String msg = "Hello!", type = "npc", targetMap = "", thankMsg = "Enjoy!";
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
                        newObj.targetMap = targetMap; newObj.spawnX = sx;
                        newObj.spawnY = sy; newObj.thankYouMessage = thankMsg;

                        //setting barista sprites
                        if (name.equals("barista_spawn") || type.equals("npc")) {
                            newObj.sprite = new ImageIcon("client/Fin_Stuff/Cycle_two_testing/assets/barista_holding_cup.png").getImage();
                            newObj.altSprite = new ImageIcon("client/Fin_Stuff/Cycle_two_testing/assets/barista_stood.png").getImage();
                        }
                        interactables.add(newObj);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); } //just print errors to console
    }

    public void checkInteraction() {
        activeObject = null;
        float range = 30.0f; 

        for (GameObject obj : interactables) {
            float px = player.x + 8, py = player.y + 8;
            float ox = obj.x + (obj.width / 2), oy = obj.y + (obj.height / 2);
            double dist = Math.sqrt(Math.pow(px - ox, 2) + Math.pow(py - oy, 2));
            if (dist < range) {
                activeObject = obj;
                break; 
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        
        //scaling stuff
        double scaleX = (double) getWidth() / NATIVE_WIDTH;
        double scaleY = (double) getHeight() / NATIVE_HEIGHT;
        double finalScale = Math.min(scaleX, scaleY); 
        
        int xOff = (int) ((getWidth() - (NATIVE_WIDTH * finalScale)) / 2);
        int yOff = (int) ((getHeight() - (NATIVE_HEIGHT * finalScale)) / 2);
        
        g2d.translate(xOff, yOff);
        g2d.scale(finalScale, finalScale);

        if (mapLayers != null && tilesetImage != null) {
            int imgW = tilesetImage.getWidth(null);
            if (imgW > 0) {
                int cols = imgW / 16;
                for (int[][] layer : mapLayers) {
                    for (int r = 0; r < mapHeight; r++) {
                        for (int c = 0; c < mapWidth; c++) {
                            int id = layer[r][c] - mapFirstGid;
                            if (id >= 0) {
                                int sx = (id % cols) * 16, sy = (id / cols) * 16;
                                g.drawImage(tilesetImage, c*16, r*16, c*16+16, r*16+16, sx, sy, sx+16, sy+16, this);
                            }
                        }
                    }
                }
            }
        }

        for (GameObject obj : interactables) {
            if (obj.sprite != null) {
                int sz = 64;
                g.drawImage(obj.sprite, obj.x - ((sz-16)/2), obj.y - (sz-16), sz, sz, this);
            }
        }

        checkInteraction(); 
        if (activeObject != null) {
            g.setColor(Color.YELLOW);
            g.drawRect(activeObject.x, activeObject.y, activeObject.width, activeObject.height);
            g.setColor(Color.WHITE);
            //hint for interaction
            g.drawString("[E]", activeObject.x + (activeObject.width / 2) - 5, activeObject.y - 5);
        }
        player.draw(g); 
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int s = 4, nx = player.x, ny = player.y;

        if (e.getKeyCode() == KeyEvent.VK_W) ny -= s;
        if (e.getKeyCode() == KeyEvent.VK_S) ny += s;
        if (e.getKeyCode() == KeyEvent.VK_A) nx -= s;
        if (e.getKeyCode() == KeyEvent.VK_D) nx += s;

        //Handle collision
        if (player.canMove(nx, ny, mapGrid, mapWidth, mapHeight)) {
            player.x = nx; player.y = ny;
        }

        if (e.getKeyCode() == KeyEvent.VK_E && activeObject != null) {
            if (activeObject.type.equals("npc")) {
                if (!hasCoffee) {
                    JOptionPane.showMessageDialog(this, activeObject.message);
                    hasCoffee = true;
                    //send packet
                    client.getInstance().requestPowerup("coffee|acquire");
                    if (activeObject.altSprite != null) activeObject.sprite = activeObject.altSprite;
                } else JOptionPane.showMessageDialog(this, activeObject.thankYouMessage);
            } 
            else if (activeObject.type.equals("door")) {
                //switch maps
                loadMap("client/Fin_Stuff/Cycle_two_testing/assets/" + activeObject.targetMap);
                player.x = activeObject.spawnX; player.y = activeObject.spawnY;
            }
        }
    }

    @Override public void keyTyped(KeyEvent e) {} 
    @Override public void keyReleased(KeyEvent e) {} 
    @Override public void actionPerformed(ActionEvent e) { repaint(); }

    public void stopGame() {
        if (timer != null) timer.stop();
    }

    public static void main(String[] args) {
        JFrame f = new JFrame("Coffee Shop RPG");
        GameTest g = new GameTest();
        f.add(g); f.setSize(960, 720);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
    }
}