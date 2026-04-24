package gui.canvas;

import gui.commands.CommandHud;
import gui.components.GuiFrame;
import gui.components.GuiTexture;
import gui.core.GuiObject;
import gui.overlay.GuiDebugPanel;
import gui.text.BitmapFont;
import gui.text.GuiText;
import gui.vec.Vector2;

import world.World;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLContext;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.J3DGraphics2D;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Canvas3D subclass that draws the GUI overlay directly via postRender(),
 * bypassing the heavyweight/lightweight Swing mixing issue.
 */
public class GuiCanvas extends Canvas3D {

    /**
     * Shared Arial SDF bitmap font.  Loaded once on first access.
     * Use this when creating {@link GuiText} elements.
     *
     * <pre>
     *   GuiText label = new GuiText(GuiCanvas.ARIAL, "Score: 0",
     *       Vector2.ofOffset(20, 20));
     *   label.setPixelHeight(24);
     *   guiCanvas.addText(label);
     * </pre>
     */
    public static final BitmapFont ARIAL =
        new BitmapFont("/fonts/arial/arial.fnt");

    private final CommandHud commandHud = new CommandHud();
    private final GuiDebugPanel debugPanel = new GuiDebugPanel();
    private GuiTexture crosshair;
    private GuiTexture joey;

    private final World world;

    /** User-managed GUI elements drawn every frame. Thread-safe. */
    private final List<GuiObject> guiObjects = new CopyOnWriteArrayList<>();

    public GuiCanvas(GraphicsConfiguration config, World world) {
        super(config);
        this.world = world;

        crosshair = new GuiTexture("/gui/SreTransparentCrop.png");
        crosshair.setCentered(true);
        crosshair.setPosition(new Vector2(100f,0.1f,200f, 0.1f));
        crosshair.setSize(Vector2.ofOffset(250f, 300f));
        guiObjects.add(crosshair);

        joey = new GuiTexture("/gui/joey.png");
        joey.setCentered(true);
        joey.setPosition(new Vector2(100f,0.1f,200f, 0.5f));
        joey.setSize(Vector2.ofOffset(250f, 250));
        guiObjects.add(joey);

        guiObjects.add(debugPanel);
        guiObjects.add(commandHud);
    }

    public void updateStats(double fps, double x, double y, double z, double yaw, double pitch, int objects, int polygons, int seed, boolean flying) {
        debugPanel.updateStats(fps, x, y, z, yaw, pitch, objects, polygons, seed, flying);
    }

    public void toggleDebugPanel() {
        debugPanel.setVisible(!debugPanel.isVisible());
    }

    public CommandHud getCommandHud() { return commandHud; }

    // ------------------------------------------------------------------ //
    // GUI Object management                                               //
    // ------------------------------------------------------------------ //

    /** Adds a generic {@link GuiObject} to be drawn every frame. */
    public void addObject(GuiObject obj) {
        if (!guiObjects.contains(obj)) guiObjects.add(obj);
    }

    /** Removes a previously added {@link GuiObject}. */
    public void removeObject(GuiObject obj) {
        guiObjects.remove(obj);
    }

    /** Adds a {@link GuiText} element to be drawn every frame. */
    public void addText(GuiText text)    { addObject(text); }

    /** Removes a previously added {@link GuiText} element. */
    public void removeText(GuiText text) { removeObject(text); }

    /** Removes all {@link GuiText} elements. */
    public void clearTexts() {
        guiObjects.removeIf(obj -> obj instanceof GuiText);
    }

    /** Adds a {@link GuiFrame} element to be drawn every frame. */
    public void addFrame(GuiFrame frame)    { addObject(frame); }

    /** Removes a previously added {@link GuiFrame} element. */
    public void removeFrame(GuiFrame frame) { removeObject(frame); }

    /** Removes all {@link GuiFrame} elements. */
    public void clearFrames() {
        guiObjects.removeIf(obj -> obj instanceof GuiFrame);
    }

    /**
     * Runs before each 3D frame, in the renderer thread with GL context current.
     */
    @Override
    public void preRender() {
        resetGlShaderState();
    }

    @Override
    public void postRender() {
        // Reset texture units AND unbind the shader program before drawing the overlay.
        resetGlShaderState();

        J3DGraphics2D g2d = getGraphics2D();

        for (GuiObject obj : guiObjects) {
            obj.draw(g2d, getWidth(), getHeight());
            // Most GUI objects use standard Graphics2D, but GuiText uses custom GL shaders.
            // To be safe, we reset state after each object.
            resetGlShaderState();
        }

        g2d.flush(false);
    }

    private void resetGlShaderState() {
        try {
            GL2 gl = GLContext.getCurrent().getGL().getGL2();
            gl.glUseProgram(0);
            for (int i = 3; i >= 0; i--) {
                gl.glActiveTexture(GL2.GL_TEXTURE0 + i);
                gl.glBindTexture(GL2.GL_TEXTURE_2D, 0);
                gl.glDisable(GL2.GL_TEXTURE_2D);
            }
            gl.glActiveTexture(GL2.GL_TEXTURE0);
        } catch (Exception ignored) {}
    }
}
