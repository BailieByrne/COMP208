import java.awt.Graphics;
import java.awt.Image;
import javax.swing.ImageIcon;
import java.awt.Color;

public class Player {
    public int x = 100; 
    public int y = 100; 

    // 1. The 8 Image Assets (4 directions, 2 frames each)
    private Image upStand, upStep;
    private Image downStand, downStep;
    private Image leftStand, leftStep;
    private Image rightStand, rightStep;

    // 2. Animation & Direction Trackers
    private int lastX = x;
    private int lastY = y;
    private int animCounter = 0;
    private boolean isStepping = false;
    private String currentDirection = "DOWN"; // Face the camera by default

    public Player() {
        // Load all 8 PNGs here! 
        // Just make sure these filenames exactly match the ones in your assets folder.
        upStand = new ImageIcon("Fin_Stuff/Cycle_two_testing/assets/up_stand.png").getImage();
        upStep = new ImageIcon("Fin_Stuff/Cycle_two_testing/assets/up_step.png").getImage();
        
        downStand = new ImageIcon("Fin_Stuff/Cycle_two_testing/assets/down_stand.png").getImage();
        downStep = new ImageIcon("Fin_Stuff/Cycle_two_testing/assets/down_step.png").getImage();
        
        leftStand = new ImageIcon("Fin_Stuff/Cycle_two_testing/assets/left_stand.png").getImage();
        leftStep = new ImageIcon("Fin_Stuff/Cycle_two_testing/assets/left_step.png").getImage();
        
        rightStand = new ImageIcon("Fin_Stuff/Cycle_two_testing/assets/right_stand.png").getImage();
        rightStep = new ImageIcon("Fin_Stuff/Cycle_two_testing/assets/right_step.png").getImage();
    }

    public boolean canMove(int nextX, int nextY, int[][] mapGrid) {
        // Keep your collision code here exactly as you had it!
        return true; 
    }

    public void draw(Graphics g) {
        // 3. Check if we moved since the last frame
        boolean isMoving = (x != lastX || y != lastY);

        if (isMoving) {
            // 4. Figure out WHICH WAY we moved by comparing current X/Y to old X/Y
            if (x > lastX) currentDirection = "RIGHT";
            else if (x < lastX) currentDirection = "LEFT";
            else if (y > lastY) currentDirection = "DOWN";
            else if (y < lastY) currentDirection = "UP";

            animCounter++;
            
            // The Speed Limit: Swap frames every 10 ticks
            if (animCounter > 10) {
                isStepping = !isStepping; // Flip between true/false
                animCounter = 0;          // Reset the clock
            }
        } else {
            // If standing still, force the standing frame and reset the clock
            isStepping = false;
            animCounter = 0;
        }

        // 5. Save our current position to check against next time
        lastX = x;
        lastY = y;

        // 6. Pick the exact frame based on our Direction AND Stepping state
        Image currentFrame = null;
        
        switch (currentDirection) {
            case "UP":
                currentFrame = isStepping ? upStep : upStand;
                break;
            case "DOWN":
                currentFrame = isStepping ? downStep : downStand;
                break;
            case "LEFT":
                currentFrame = isStepping ? leftStep : leftStand;
                break;
            case "RIGHT":
                currentFrame = isStepping ? rightStep : rightStand;
                break;
        }

        // 7. Draw the chosen frame!
        if (currentFrame != null && currentFrame.getWidth(null) > 0) {
            g.drawImage(currentFrame, x, y, 16, 16, null);
        } else {
            // Fallback red square just in case a file path is typed wrong
            g.setColor(Color.RED);
            g.fillRect(x, y, 16, 16);
        }
    }
}