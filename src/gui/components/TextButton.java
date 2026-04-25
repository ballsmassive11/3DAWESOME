package gui.components;

import gui.canvas.GuiCanvas;
import gui.core.GuiButton;
import gui.text.GuiText;
import gui.vec.Vector2;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A button that displays text on a rectangular background.
 */
public class TextButton extends GuiButton {

    private final GuiFrame background;
    private final GuiText label;
    private final List<Consumer<TextButton>> clickListeners = new ArrayList<>();

    private Color normalColor = new Color(50, 50, 50, 200);
    private Color hoverColor = new Color(80, 80, 80, 200);
    private Color pressedColor = new Color(30, 30, 30, 200);

    private boolean isHovered = false;
    private boolean isPressed = false;

    public TextButton(String text, Vector2 position, Vector2 size) {
        this.position = position;
        this.background = new GuiFrame(position, size, normalColor);
        this.background.setCentered(true);

        this.label = new GuiText(GuiCanvas.ARIAL, text, position);
        this.label.setPixelHeight(24f);
        this.label.setColor(Color.WHITE);
        this.label.setCentered(true);
    }

    @Override
    public void draw(Graphics2D g2d, int screenWidth, int screenHeight) {
        if (!visible) return;

        // Update background color based on state
        if (isPressed) {
            background.setColor(pressedColor);
        } else if (isHovered) {
            background.setColor(hoverColor);
        } else {
            background.setColor(normalColor);
        }

        background.draw(g2d, screenWidth, screenHeight);
        
        // Ensure Graphics2D is flushed before GL drawing to avoid layering issues
        if (g2d instanceof javax.media.j3d.J3DGraphics2D) {
            ((javax.media.j3d.J3DGraphics2D) g2d).flush(false);
        }
        
        label.draw(g2d, screenWidth, screenHeight);
    }

    @Override
    public boolean handleMouseEvent(MouseEvent e, int screenWidth, int screenHeight) {
        if (!visible) return false;

        int mx = e.getX();
        int my = e.getY();

        int bx = position.resolveX(screenWidth);
        int by = position.resolveY(screenHeight);
        int bw = background.getSize().resolveX(screenWidth);
        int bh = background.getSize().resolveY(screenHeight);

        // Calculate bounds (assuming centered)
        int x0 = bx - bw / 2;
        int y0 = by - bh / 2;
        int x1 = x0 + bw;
        int y1 = y0 + bh;

        boolean inside = mx >= x0 && mx <= x1 && my >= y0 && my <= y1;

        if (e.getID() == MouseEvent.MOUSE_MOVED || e.getID() == MouseEvent.MOUSE_DRAGGED) {
            isHovered = inside;
            return false; // Don't consume move events usually
        }

        if (inside) {
            if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                isPressed = true;
                return true;
            } else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
                if (isPressed) {
                    notifyClicked();
                }
                isPressed = false;
                return true;
            } else if (e.getID() == MouseEvent.MOUSE_CLICKED) {
                return true;
            }
        } else {
            if (e.getID() == MouseEvent.MOUSE_RELEASED) {
                isPressed = false;
            }
        }

        return false;
    }

    public void addClickListener(Consumer<TextButton> listener) {
        clickListeners.add(listener);
    }

    private void notifyClicked() {
        for (Consumer<TextButton> listener : clickListeners) {
            listener.accept(this);
        }
    }

    // Setters for customization
    public void setNormalColor(Color color) { this.normalColor = color; }
    public void setHoverColor(Color color) { this.hoverColor = color; }
    public void setPressedColor(Color color) { this.pressedColor = color; }
    public void setTextColor(Color color) { this.label.setColor(color); }
    public void setTextHeight(float height) { this.label.setPixelHeight(height); }

    @Override
    public void setPosition(Vector2 position) {
        super.setPosition(position);
        background.setPosition(position);
        label.setPosition(position);
    }
}
