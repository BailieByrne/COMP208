import java.awt.Color;
import java.awt.Graphics;

public class Player {
    // Starting on a clear road segment
    public int x = 160; 
    public int y = 224;
    public int width = 16;
    public int height = 16;

    public void draw(Graphics g) {
        g.setColor(Color.RED); 
        g.fillRect(x, y, width, height);
    }

public boolean canMove(int nextX, int nextY, int[][] grid) {
    if (grid == null) return true;

    // We check the 'hitbox' of the player's feet (left edge and right edge)
    int leftGridX = (nextX + 2) / 16; 
    int rightGridX = (nextX + 14) / 16;
    int gy = (nextY + 15) / 16;

    if (gy < 0 || gy >= grid.length || leftGridX < 0 || rightGridX >= grid[0].length) {
        return false;
    }

    // Check the row you are in AND the row above for both foot corners
    boolean collision = 
        isSolid(grid[gy][leftGridX]) || isSolid(grid[gy][rightGridX]) ||
        (gy > 0 && (isSolid(grid[gy-1][leftGridX]) || isSolid(grid[gy-1][rightGridX])));

    return !collision;
}

// Keep your solid IDs in one place for easy updating
private boolean isSolid(int id) {
    // These IDs cover the skyscraper base/middle and trucks
    return (id == 103 || id == 104 || id == 71 || id == 72 || (id >= 449 && id <= 451));
}
}