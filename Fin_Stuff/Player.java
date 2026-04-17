import java.awt.Graphics;
import java.awt.Image;
import javax.swing.ImageIcon;
import java.awt.Color;

public class Player {
    public int x = 100; 
    public int y = 100; 
    
    // The visual size! 16 * 1.4 (40% bigger) = ~22
    public int drawSize = 22; 

    // 1. The 8 Image Assets
    private Image upStand, upStep;
    private Image downStand, downStep;
    private Image leftStand, leftStep;
    private Image rightStand, rightStep;

    // 2. Animation & Direction Trackers
    private int lastX = x;
    private int lastY = y;
    private int animCounter = 0;
    private boolean isStepping = false;
    private String currentDirection = "DOWN"; 
    
    // NEW: The "Grace Period" tracker to fix the stuttering animation
    private int framesSinceLastMove = 100; 

    public Player() {
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
        // Keep your collision code here!
        return true; 
    }

    public void draw(Graphics g) {
        // 3. Did we physically move THIS exact frame?
        if (x != lastX || y != lastY) {
            framesSinceLastMove = 0; // Reset our idle timer!
            
            // Figure out WHICH WAY we moved
            if (x > lastX) currentDirection = "RIGHT";
            else if (x < lastX) currentDirection = "LEFT";
            else if (y > lastY) currentDirection = "DOWN";
            else if (y < lastY) currentDirection = "UP";
        } else {
            framesSinceLastMove++; // Tick up the idle timer
        }

        // 4. The Grace Period: Keep animating if we moved very recently
        if (framesSinceLastMove < 5) {
            animCounter++;
            
            // The Speed Limit: Swap frames every 10 ticks (Increase this to slow down the walk speed!)
            if (animCounter > 10) {
                isStepping = !isStepping; 
                animCounter = 0;          
            }
        } else {
            // We have DEFINITELY stopped moving, freeze the animation
            isStepping = false;
            animCounter = 0;
        }

        // Save our position for the next frame's math
        lastX = x;
        lastY = y;

        // Pick the exact frame based on Direction and Stepping state
        Image currentFrame = null;
        switch (currentDirection) {
            case "UP": currentFrame = isStepping ? upStep : upStand; break;
            case "DOWN": currentFrame = isStepping ? downStep : downStand; break;
            case "LEFT": currentFrame = isStepping ? leftStep : leftStand; break;
            case "RIGHT": currentFrame = isStepping ? rightStep : rightStand; break;
        }

        // 5. Draw it! Notice we use 'drawSize' now so it is 40% bigger
        if (currentFrame != null && currentFrame.getWidth(null) > 0) {
            
            // Optional pro-tip: we offset the drawing slightly so the character's feet 
            // stay in the same place even though the image is bigger!
            int drawX = x - ((drawSize - 16) / 2);
            int drawY = y - (drawSize - 16); 
            
            g.drawImage(currentFrame, drawX, drawY, drawSize, drawSize, null);
        } else {
            g.setColor(Color.RED);
            g.fillRect(x, y, drawSize, drawSize);
        }
    }
}