package gui.overlay;

import gui.core.GuiObject;
import java.awt.*;

/**
 * A semi-transparent blueish overlay that covers the entire screen when the camera is underwater.
 */
public class UnderwaterOverlay extends GuiObject {

    private Color overlayColor = new Color(0, 100, 200, 100);

    public UnderwaterOverlay() {
        this.visible = false;
    }

    @Override
    public void draw(Graphics2D g2d, int screenWidth, int screenHeight) {
        if (!visible) return;

        Color oldColor = g2d.getColor();
        g2d.setColor(overlayColor);
        g2d.fillRect(0, 0, screenWidth, screenHeight);
        g2d.setColor(oldColor);
    }
}
