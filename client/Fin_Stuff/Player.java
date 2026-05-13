import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import javax.swing.ImageIcon;

public class Player {
    public int x = 100, y = 100; //pos
    public int drawSize = 64; 
    
    private Image[] upFrames = new Image[4]; 
    private Image[] downFrames = new Image[4]; 
    private Image[] leftFrames = new Image[4]; 
    private Image[] rightFrames = new Image[4];

    public int lastX = x, lastY = y; 
    private int animCounter = 0; 
    private int currentFrameIndex = 0; 
    private String currentDirection = "DOWN"; 
    private int framesSinceLastMove = 100; 

    public Player() {
        String path = "client/Fin_Stuff/Cycle_two_testing/assets/"; 
        /* This loop goes through and loads every single 
        png file for the animations so we have them 
        ready to draw later on
        */
        for (int i = 0; i < 4; i++) {
            upFrames[i] = new ImageIcon(path + "up_" + i + ".png").getImage();
            downFrames[i] = new ImageIcon(path + "down_" + i + ".png").getImage();
            leftFrames[i] = new ImageIcon(path + "left_" + i + ".png").getImage();
            rightFrames[i] = new ImageIcon(path + "right_" + i + ".png").getImage();
        }
    }

    public boolean canMove(int nextX, int nextY, int[][] mapGrid, int mapWidth, int mapHeight) {
        int mapPixelWidth = mapWidth * 16;  
        int mapPixelHeight = mapHeight * 16;
        
        //collision box setup
        int pw = 16, ph = 16;
        int minX = nextX - (pw / 2), maxX = nextX + (pw / 2);
        int minY = nextY - (ph / 2), maxY = nextY + (ph / 2);
        
        if (nextX < 0 || nextX >= mapPixelWidth || nextY < 0 || nextY >= mapPixelHeight) return false;
        
        int sX = Math.max(0, minX / 16), sY = Math.max(0, minY / 16);
        int eX = Math.min(mapWidth - 1, maxX / 16), eY = Math.min(mapHeight - 1, maxY / 16);
        
        /*
          We need to check every tile the player is 
          touching to make sure none of them are 
          solid walls or objects
        */
        for (int ty = sY; ty <= eY; ty++) {
            for (int tx = sX; tx <= eX; tx++) {
                if (mapGrid[ty][tx] != 0) return false; 
            }
        }
        return true;
    }

    public void draw(Graphics g) {
        //movement check
        if (x != lastX || y != lastY) {
            framesSinceLastMove = 0; 
            if (x > lastX) currentDirection = "RIGHT";
            else if (x < lastX) currentDirection = "LEFT";
            else if (y > lastY) currentDirection = "DOWN";
            else if (y < lastY) currentDirection = "UP";
        } else framesSinceLastMove++; 

        //animation logic
        if (framesSinceLastMove < 5) {
            animCounter++;
            /*
              If the counter hits 10 we move to the 
              next frame in the array to make the 
              walking look smooth
            */
            if (animCounter > 10) {
                currentFrameIndex = (currentFrameIndex + 1) % 4; 
                animCounter = 0;          
            }
        } else {
            currentFrameIndex = 0;
            animCounter = 0;
        }

        lastX = x; lastY = y;

        Image[] active = downFrames;
        switch (currentDirection) {
            case "UP": active = upFrames; break;
            case "DOWN": active = downFrames; break;
            case "LEFT": active = leftFrames; break;
            case "RIGHT": active = rightFrames; break;
        }

        Image frame = active[currentFrameIndex];
        if (frame != null && frame.getWidth(null) > 0) {
            //offset to center
            int dx = x - ((drawSize - 16) / 2), dy = y - (drawSize - 16);
            g.drawImage(frame, dx, dy, drawSize, drawSize, null);
        } else {
            // fallback incase the image didnt load right
            g.setColor(Color.RED);
            g.fillRect(x, y, drawSize, drawSize); 
        }
    }
}