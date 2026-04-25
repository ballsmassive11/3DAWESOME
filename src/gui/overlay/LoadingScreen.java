package gui.overlay;

import gui.canvas.GuiCanvas;
import gui.components.GuiFrame;
import gui.core.GuiObject;
import gui.text.GuiText;
import gui.vec.Vector2;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * A simple loading screen overlay with a solid background and text.
 */
public class LoadingScreen extends GuiObject {

    private final GuiFrame background;
    private final GuiText loadingText;

    public LoadingScreen() {
        // Full screen dark background
        background = new GuiFrame(Vector2.ofScale(0.5f, 0.5f), Vector2.ofScale(1.0f, 1.0f), new Color(15, 15, 15));
        background.setCentered(true);

        // Loading text in the center
        loadingText = new GuiText(GuiCanvas.ARIAL, "LOADING WORLD...", Vector2.ofScale(0.5f, 0.5f));
        loadingText.setPixelHeight(48f);
        loadingText.setColor(Color.WHITE);
        loadingText.setCentered(true);
        
        visible = false;
    }

    @Override
    public void draw(Graphics2D g2d, int screenWidth, int screenHeight) {
        if (!visible) return;

        background.draw(g2d, screenWidth, screenHeight);
        
        // Ensure Graphics2D is flushed for GL text if needed
        if (g2d instanceof javax.media.j3d.J3DGraphics2D) {
            ((javax.media.j3d.J3DGraphics2D) g2d).flush(false);
        }
        
        loadingText.draw(g2d, screenWidth, screenHeight);
    }

    @Override
    public boolean handleMouseEvent(MouseEvent e, int screenWidth, int screenHeight) {
        // Consume all mouse events while loading to prevent clicking things behind
        return visible;
    }
}
