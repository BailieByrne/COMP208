import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;

import javax.swing.ImageIcon;

public class Player {
    public int x = 100; 
    public int y = 100; 
    public int drawSize = 64; // Kept your 40% upscale!

    // 1. Arrays to hold the 4 frames for each direction
    private Image[] upFrames = new Image[4];
    private Image[] downFrames = new Image[4];
    private Image[] leftFrames = new Image[4];
    private Image[] rightFrames = new Image[4];

    // 2. Animation & Direction Trackers
    public int lastX = x;
    public  int lastY = y;
    private int animCounter = 0;
    
    // NEW: We replaced the boolean with an integer to track frames 0, 1, 2, 3
    private int currentFrameIndex = 0; 
    
    private String currentDirection = "DOWN"; 
    private int framesSinceLastMove = 100; 

    public Player() {
        // Load all 16 PNGs here! 
        // Replace these paths with the actual names of your 4-frame PNGs
        String path = "Fin_Stuff/Cycle_two_testing/assets/";

        for (int i = 0; i < 4; i++) {
            // Assuming your files are named like "up_0.png", "up_1.png", etc.
            upFrames[i] = new ImageIcon(path + "up_" + i + ".png").getImage();
            downFrames[i] = new ImageIcon(path + "down_" + i + ".png").getImage();
            leftFrames[i] = new ImageIcon(path + "left_" + i + ".png").getImage();
            rightFrames[i] = new ImageIcon(path + "right_" + i + ".png").getImage();
        }
    }

    public boolean canMove(int nextX, int nextY, int[][] mapGrid) {
        // Keep your collision code here exactly as it was!
        return true; 
    }

    public void draw(Graphics g) {
        // 3. Did we physically move THIS exact frame?
        if (x != lastX || y != lastY) {
            framesSinceLastMove = 0; 
            
            if (x > lastX) currentDirection = "RIGHT";
            else if (x < lastX) currentDirection = "LEFT";
            else if (y > lastY) currentDirection = "DOWN";
            else if (y < lastY) currentDirection = "UP";
        } else {
            framesSinceLastMove++; 
        }

        // 4. The Grace Period & Frame Math
        if (framesSinceLastMove < 5) {
            animCounter++;
            
            // Swap frames every 10 ticks (Adjust to speed up/slow down walk speed)
            if (animCounter > 10) {
                // This math loops the index: 0 -> 1 -> 2 -> 3 -> 0 -> 1...
                currentFrameIndex = (currentFrameIndex + 1) % 4; 
                animCounter = 0;          
            }
        } else {
            // If standing still, force them back to Frame 0 (usually the idle pose)
            currentFrameIndex = 0;
            animCounter = 0;
        }

        lastX = x;
        lastY = y;

        // 5. Pick the correct Array based on direction
        Image[] activeArray = downFrames; // Default
        switch (currentDirection) {
            case "UP": activeArray = upFrames; break;
            case "DOWN": activeArray = downFrames; break;
            case "LEFT": activeArray = leftFrames; break;
            case "RIGHT": activeArray = rightFrames; break;
        }

        // 6. Grab the specific frame from that array
        Image currentFrame = activeArray[currentFrameIndex];

        // 7. Draw it!
        if (currentFrame != null && currentFrame.getWidth(null) > 0) {
            int drawX = x - ((drawSize - 16) / 2);
            int drawY = y - (drawSize - 16); 
            
            g.drawImage(currentFrame, drawX, drawY, drawSize, drawSize, null);
        } else {
            g.setColor(Color.RED);
            g.fillRect(x, y, drawSize, drawSize);
        }
    }
}