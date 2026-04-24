package gui.components;

import gui.core.GuiLabel;
import gui.vec.Vector2;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * A GUI element that renders a PNG or JPG image onto the screen.
 *
 * <p>Position and size both use {@link Vector2}, where each axis resolves as
 * {@code offset + scale * screenDimension}.  The anchor point is the top-left
 * corner of the image unless {@code centered} is true.
 *
 * <pre>
 *   GuiTexture crosshair = new GuiTexture("/gui/crosshair.png");
 *   crosshair.setCentered(true);
 *   crosshair.setPosition(Vector2.ofScale(0.5f, 0.5f)); // middle of screen
 *   crosshair.setSize(Vector2.ofOffset(64, 64));         // fixed 64×64 px
 *   crosshair.setAlpha(0.9f);
 * </pre>
 *
 * <p>Call {@link #draw(Graphics2D, int, int)} from inside
 * {@link GuiCanvas#postRender()} (or any other {@link Graphics2D} context)
 * to render the element.
 */
public class GuiTexture extends GuiLabel {

    private BufferedImage image;
    /** Size vector: each axis combines a fixed pixel offset and a screen-relative scale. */
    private Vector2 size = new Vector2(0, 0.1f, 0, 0.1f);
    /** Opacity: 0 = fully transparent, 1 = fully opaque. */
    private float alpha = 1f;
    /** When true, the position is treated as the center rather than the top-left corner. */
    private boolean centered = false;

    // ------------------------------------------------------------------
    // Construction / loading
    // ------------------------------------------------------------------

    /**
     * Creates a GuiTexture from a file system path or a classpath resource path.
     * Classpath resources must start with {@code /} (e.g. {@code /gui/icon.png}).
     *
     * @param path path to a PNG or JPG image
     * @throws RuntimeException if the image cannot be loaded
     */
    public GuiTexture(String path) {
        load(path);
    }

    /**
     * Creates a GuiTexture and sets its position and size immediately.
     *
     * @param path     path to a PNG or JPG image
     * @param position position vector (see {@link Vector2})
     * @param size     size vector (see {@link Vector2})
     */
    public GuiTexture(String path, Vector2 position, Vector2 size) {
        load(path);
        this.position = position;
        this.size = size;
    }

    private void load(String path) {
        try {
            BufferedImage loaded = null;

            // Try classpath first (paths starting with '/')
            if (path.startsWith("/")) {
                InputStream is = GuiTexture.class.getResourceAsStream(path);
                if (is != null) {
                    loaded = ImageIO.read(is);
                    is.close();
                }
            }

            // Fall back to file system (try as-is, then src-relative)
            if (loaded == null) {
                File file = new File(path);
                if (!file.exists() && path.startsWith("/")) {
                    file = new File(path.substring(1));
                }
                if (file.exists()) {
                    loaded = ImageIO.read(file);
                }
            }

            if (loaded == null) {
                throw new IOException("Image not found: " + path);
            }

            this.image = loaded;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load GuiTexture: " + path, e);
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /**
     * Draws this texture onto the given {@link Graphics2D} context.
     * Should be called from within {@link GuiCanvas#postRender()}.
     *
     * @param g2           the graphics context to draw into
     * @param canvasWidth  current canvas width in pixels
     * @param canvasHeight current canvas height in pixels
     */
    @Override
    public void draw(Graphics2D g2, int canvasWidth, int canvasHeight) {
        if (!visible || image == null) return;

        int px = position.resolveX(canvasWidth);
        int py = position.resolveY(canvasHeight);
        int pw = size.resolveX(canvasWidth);
        int ph = size.resolveY(canvasHeight);

        if (centered) {
            px -= pw / 2;
            py -= ph / 2;
        }

        Composite prevComposite = g2.getComposite();
        if (alpha < 1f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }

        g2.drawImage(image, px, py, pw, ph, null);

        if (alpha < 1f) {
            g2.setComposite(prevComposite);
        }
    }

    // ------------------------------------------------------------------
    // Getters / setters
    // ------------------------------------------------------------------

    /** Returns the raw loaded image. */
    public BufferedImage getImage() { return image; }

    /** Size vector controlling width and height via offset + scale per axis. */
    public Vector2 getSize() { return size; }
    public void setSize(Vector2 size) { this.size = size; }

    /** Opacity: 0 = fully transparent, 1 = fully opaque. */
    public float getAlpha() { return alpha; }
    public void setAlpha(float alpha) { this.alpha = Math.max(0f, Math.min(1f, alpha)); }

    /**
     * When {@code true}, the position is treated as the center of the image rather than
     * its top-left corner.  Useful for icons that should be positioned by their center.
     */
    public boolean isCentered() { return centered; }
    public void setCentered(boolean centered) { this.centered = centered; }
}
