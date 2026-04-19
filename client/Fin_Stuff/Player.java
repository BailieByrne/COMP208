import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;

import javax.swing.ImageIcon;
//Neccesary imports for rendering sprite-based player character

/**
 * Player class - handles the player character sprite, animation, and rendering
 * Supports 4-directional movement with frame-based animation
 * Position tracked relative to last update for smooth animation detection
 */
public class Player {
    public int x = 100; // player x position
    public int y = 100; // player y position
    public int drawSize = 64; // sprite draw size
    
    private Image[] upFrames = new Image[4]; // up animation frames
    private Image[] downFrames = new Image[4]; // down animation frames
    private Image[] leftFrames = new Image[4]; // left animation frames
    private Image[] rightFrames = new Image[4]; // right animation frames

    public int lastX = x; // previous frame x position
    public  int lastY = y; // previous frame y position
    private int animCounter = 0; // counter for animation timing
    
    private int currentFrameIndex = 0; // which frame in the 4-frame animation
    
    private String currentDirection = "DOWN"; // which direction player is facing
    private int framesSinceLastMove = 100; // frames since movement stopped (for idle animation) 

    /**
     * Constructor - load all 16 player sprite frames (4 directions × 4 frames)
     * Files assumed named like "up_0.png", "up_1.png", etc in assets folder
     */
    public Player() {
        String path = "client/Fin_Stuff/Cycle_two_testing/assets/"; // sprite folder

        // load 4 animation frames for each direction
        for (int i = 0; i < 4; i++) {
            upFrames[i] = new ImageIcon(path + "up_" + i + ".png").getImage();
            downFrames[i] = new ImageIcon(path + "down_" + i + ".png").getImage();
            leftFrames[i] = new ImageIcon(path + "left_" + i + ".png").getImage();
            rightFrames[i] = new ImageIcon(path + "right_" + i + ".png").getImage();
        }
    }

    /**
     * canMove - checks if player can move to next position (collision detection)
     * Currently always returns true but placeholder for future collision logic
     */
    public boolean canMove(int nextX, int nextY, int[][] mapGrid) {
        return true; // no collision checks yet
    }

    /**
     * draw - renders player sprite with 4-frame animation
     * Animates when moving, idle when stationary (5+ frames b4 idle)
     * Automatically selects correct direction sprite based on movement
     */
    public void draw(Graphics g) {
        // detect if player moved
        if (x != lastX || y != lastY) {
            framesSinceLastMove = 0; // reset idle counter
            
            // update direction based on movement delta
            if (x > lastX) currentDirection = "RIGHT";
            else if (x < lastX) currentDirection = "LEFT";
            else if (y > lastY) currentDirection = "DOWN";
            else if (y < lastY) currentDirection = "UP";
        } else {
            framesSinceLastMove++; // increment idle counter
        }

        // animate only if player is moving (within 5 frames of move)
        if (framesSinceLastMove < 5) {
            animCounter++;
            
            // advance to next frame every 10 ticks
            if (animCounter > 10) {
                currentFrameIndex = (currentFrameIndex + 1) % 4; // cycle 0→1→2→3→0
                animCounter = 0;          
            }
        } else {
            // idle - show first frame
            currentFrameIndex = 0;
            animCounter = 0;
        }

        lastX = x;
        lastY = y;

        // select appropriate direction frame array
        Image[] activeArray = downFrames; // default
        switch (currentDirection) {
            case "UP": activeArray = upFrames; break;
            case "DOWN": activeArray = downFrames; break;
            case "LEFT": activeArray = leftFrames; break;
            case "RIGHT": activeArray = rightFrames; break;
        }

        Image currentFrame = activeArray[currentFrameIndex];

        // render sprite with bounds checking
        if (currentFrame != null && currentFrame.getWidth(null) > 0) {
            int drawX = x - ((drawSize - 16) / 2); // center horizontally
            int drawY = y - (drawSize - 16); // bottom-align to feet
            
            g.drawImage(currentFrame, drawX, drawY, drawSize, drawSize, null);
        } else {
            // fallback red square if sprite missing
            g.setColor(Color.RED);
            g.fillRect(x, y, drawSize, drawSize);
        }
    }
}
