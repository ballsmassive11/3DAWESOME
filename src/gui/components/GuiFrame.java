package gui.components;

import gui.core.GuiObject;
import gui.vec.Vector2;
import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * A simple GUI box that can be filled, transparent, positioned, resized, and rotated.
 */
public class GuiFrame extends GuiObject {

    private Vector2 size = new Vector2(100, 0f, 100, 0f);
    private Color color = Color.WHITE;
    private float alpha = 1.0f;
    private double rotationDegrees = 0;
    private boolean centered = false;
    private boolean filled = true;

    private Color borderColor = Color.BLACK;
    private float borderWidth = 0.0f;
    private float borderAlpha = 1.0f;

    public GuiFrame() {}

    public GuiFrame(Vector2 position, Vector2 size, Color color) {
        this.position = position;
        this.size = size;
        this.color = color;
    }

    /**
     * Draws the frame onto the given Graphics2D context.
     */
    @Override
    public void draw(Graphics2D g2, int canvasWidth, int canvasHeight) {
        if (!visible) return;

        int px = position.resolveX(canvasWidth);
        int py = position.resolveY(canvasHeight);
        int pw = size.resolveX(canvasWidth);
        int ph = size.resolveY(canvasHeight);

        // Save original transform and composite
        AffineTransform oldTransform = g2.getTransform();
        Composite oldComposite = g2.getComposite();
        Stroke oldStroke = g2.getStroke();

        // Apply alpha
        if (alpha < 1.0f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }

        // Apply rotation
        if (rotationDegrees != 0) {
            double rx = centered ? px : px + pw / 2.0;
            double ry = centered ? py : py + ph / 2.0;
            g2.rotate(Math.toRadians(rotationDegrees), rx, ry);
        }

        // Adjust for centering if needed
        int drawX = centered ? px - pw / 2 : px;
        int drawY = centered ? py - ph / 2 : py;

        if (filled) {
            g2.setColor(color);
            g2.fillRect(drawX, drawY, pw, ph);
        }

        // Draw border
        if (borderWidth > 0) {
            g2.setColor(new Color(
                    borderColor.getRed(),
                    borderColor.getGreen(),
                    borderColor.getBlue(),
                    (int) (borderAlpha * 255)
            ));
            g2.setStroke(new BasicStroke(borderWidth));
            g2.drawRect(drawX, drawY, pw, ph);
        }

        // Restore state
        g2.setStroke(oldStroke);
        g2.setComposite(oldComposite);
        g2.setTransform(oldTransform);
    }

    // Getters and Setters

    public Vector2 getSize() { return size; }
    public void setSize(Vector2 size) { this.size = size; }

    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }

    public float getAlpha() { return alpha; }
    public void setAlpha(float alpha) {
        this.alpha = Math.max(0f, Math.min(1f, alpha));
        // Update color alpha for backward compatibility with Graphics2D if needed, 
        // though our draw method uses AlphaComposite which is better.
        if (color != null) {
            this.color = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(this.alpha * 255));
        }
    }

    public double getRotation() { return rotationDegrees; }
    public void setRotation(double degrees) { this.rotationDegrees = degrees; }

    public boolean isCentered() { return centered; }
    public void setCentered(boolean centered) { this.centered = centered; }

    public boolean isFilled() { return filled; }
    public void setFilled(boolean filled) { this.filled = filled; }

    public Color getBorderColor() { return borderColor; }
    public void setBorderColor(Color borderColor) { this.borderColor = borderColor; }

    public float getBorderWidth() { return borderWidth; }
    public void setBorderWidth(float borderWidth) { this.borderWidth = borderWidth; }

    public float getBorderAlpha() { return borderAlpha; }
    public void setBorderAlpha(float borderAlpha) {
        this.borderAlpha = Math.max(0f, Math.min(1f, borderAlpha));
    }
}
