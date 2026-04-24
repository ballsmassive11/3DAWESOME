package gui.core;

import gui.vec.Vector2;
import java.awt.Graphics2D;

/**
 * Base class for all GUI elements.
 */
public abstract class GuiObject {
    protected Vector2 position = new Vector2(0, 0f, 0, 0f);
    protected boolean visible = true;

    public abstract void draw(Graphics2D g2d, int screenWidth, int screenHeight);

    public Vector2 getPosition() { return position; }
    public void setPosition(Vector2 position) { this.position = position; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
