package scripting;

import gui.canvas.GuiCanvas;
import gui.components.GuiFrame;
import gui.components.GuiTexture;
import gui.text.GuiText;
import gui.vec.Vector2;
import world.World;

import java.awt.Color;

/**
 * Surface exposed to Python mod scripts as the global `game`.
 * Keep methods small and stable — scripts will depend on these names.
 */
public class ModApi {
    private final World world;
    private final GuiCanvas gui;

    public ModApi(World world, GuiCanvas gui) {
        this.world = world;
        this.gui = gui;
    }

    // ------------------------------------------------------------------
    // World
    // ------------------------------------------------------------------

    public void setPlayerModel(String path) {
        world.setPlayerModel(path);
    }

    public void setMoveSpeed(double speed) {
        world.getCamera().setMoveSpeed(speed);
    }

    // ------------------------------------------------------------------
    // GUI — Text
    // ------------------------------------------------------------------

    /** Adds a text label at normalized screen position (0.0–1.0). Returns the live object. */
    public GuiText addText(String text, double x, double y) {
        GuiText t = new GuiText(GuiCanvas.ARIAL, text, Vector2.ofScale((float) x, (float) y));
        gui.addText(t);
        return t;
    }

    public void removeText(GuiText t) {
        gui.removeText(t);
    }

    // ------------------------------------------------------------------
    // GUI — Textures
    // ------------------------------------------------------------------

    /**
     * Adds an image at normalized screen position (0.0–1.0) with pixel size w×h.
     * path may be a file system path or a classpath resource starting with '/'.
     * Returns the live object.
     */
    public GuiTexture addTexture(String path, double x, double y, double w, double h) {
        GuiTexture tex = new GuiTexture(path);
        tex.setPosition(Vector2.ofScale((float) x, (float) y));
        tex.setSize(Vector2.ofOffset((float) w, (float) h));
        gui.addTexture(tex);
        return tex;
    }

    public void removeTexture(GuiTexture tex) {
        gui.removeObject(tex);
    }

    // ------------------------------------------------------------------
    // GUI — Frames
    // ------------------------------------------------------------------

    /**
     * Adds a filled rectangle at normalized screen position with pixel size w×h.
     * Color components are 0–255. Returns the live object.
     */
    public GuiFrame addFrame(double x, double y, double w, double h, int r, int g, int b, int a) {
        GuiFrame f = new GuiFrame(
                Vector2.ofScale((float) x, (float) y),
                Vector2.ofOffset((float) w, (float) h),
                new Color(r, g, b, a)
        );
        gui.addFrame(f);
        return f;
    }

    /** Adds an opaque white frame. */
    public GuiFrame addFrame(double x, double y, double w, double h) {
        return addFrame(x, y, w, h, 255, 255, 255, 255);
    }

    public void removeFrame(GuiFrame f) {
        gui.removeFrame(f);
    }
}
