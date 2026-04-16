import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Scanner;

public class GameTest extends JPanel implements ActionListener {
    Timer timer;
    Player player = new Player();
    int[][] mapGrid;
    int mapWidth, mapHeight;
    Image tilesetImage;

    public GameTest() {
        String jsonPath = "C:\\Year 2 Semester 2\\COMP208\\code\\COMP208\\Fin_Stuff\\Cycle_two_testing\\assets\\testing_map_v1.json";
        String imagePath = "C:\\Year 2 Semester 2\\COMP208\\code\\COMP208\\Fin_Stuff\\Cycle_two_testing\\assets\\CP_V1.1.0_nyknck\\CP_V1.1.0_nyknck\\CP_V1.0.4_nyknck\\CP_V1.0.4.png";
        
        loadMap(jsonPath);
        tilesetImage = Toolkit.getDefaultToolkit().getImage(imagePath);

        timer = new Timer(16, this); 
        timer.start();
        setFocusable(true);
        
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
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
            }
        });
    }

    public void loadMap(String filePath) {
        try {
            File myObj = new File(filePath);
            Scanner myReader = new Scanner(myObj);
            StringBuilder jsonContent = new StringBuilder();
            while (myReader.hasNextLine()) { jsonContent.append(myReader.nextLine()); }
            myReader.close();

            String content = jsonContent.toString();
            mapWidth = Integer.parseInt(extractValue(content, "\"width\":"));
            mapHeight = Integer.parseInt(extractValue(content, "\"height\":"));

            String dataSection = content.split("\"data\":\\[")[1].split("\\]")[0];
            String[] tileIds = dataSection.split(",");

            mapGrid = new int[mapHeight][mapWidth];
            for (int i = 0; i < tileIds.length; i++) {
                mapGrid[i / mapWidth][i % mapWidth] = Integer.parseInt(tileIds[i].trim());
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
    }

    private String extractValue(String content, String key) {
        int start = content.indexOf(key) + key.length();
        int end = content.indexOf(",", start);
        return content.substring(start, end).trim();
    }

    
@Override
protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (mapGrid != null) {
        for (int row = 0; row < mapHeight; row++) {
            for (int col = 0; col < mapWidth; col++) {
                int tileID = mapGrid[row][col] - 1; 
                if (tileID >= 0) {
                    // 1024px width / 32px tile = 32 columns
                    int columns = 32; 
                    
                    int sx = (tileID % columns) * 32; 
                    int sy = (tileID / columns) * 32;
                    
                    // Draw a 32x32 slice onto the map
                    // We draw it starting at col*16, but make it 32px wide/tall to match Tiled
                    g.drawImage(tilesetImage, 
                        col * 16, row * 16, col * 16 + 32, row * 16 + 32, 
                        sx, sy, sx + 32, sy + 32, this);
                }
            }
        }
    }
    player.draw(g); 
}

    @Override public void actionPerformed(ActionEvent e) { repaint(); }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Tiled City Test");
        frame.add(new GameTest());
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}