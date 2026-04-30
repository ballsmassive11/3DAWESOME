package gui.canvas;

import gui.commands.CommandHud;
import gui.components.GuiFrame;
import gui.components.GuiTexture;
import gui.core.GuiObject;
import gui.overlay.GuiDebugPanel;
import gui.overlay.LoadingScreen;
import gui.text.BitmapFont;
import gui.text.GuiText;
import gui.vec.Vector2;

import world.World;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLContext;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.J3DGraphics2D;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private final LoadingScreen loadingScreen = new LoadingScreen();

    private final World world;

    /** User-managed GUI elements drawn every frame. Thread-safe. */
    private final List<GuiObject> guiObjects = new CopyOnWriteArrayList<>();

    public GuiCanvas(GraphicsConfiguration config, World world) {
        super(config);
        this.world = world;

        debugPanel.setVisible(false);
        guiObjects.add(debugPanel);
        guiObjects.add(commandHud);
        guiObjects.add(loadingScreen);

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { dispatchMouseEvent(e); }
            @Override
            public void mouseReleased(MouseEvent e) { dispatchMouseEvent(e); }
            @Override
            public void mouseClicked(MouseEvent e) { dispatchMouseEvent(e); }
            @Override
            public void mouseMoved(MouseEvent e) { dispatchMouseEvent(e); }
            @Override
            public void mouseDragged(MouseEvent e) { dispatchMouseEvent(e); }

            private void dispatchMouseEvent(MouseEvent e) {
                // System overlays get first dibs on mouse events (top to bottom)
                if (loadingScreen.isVisible()) {
                    if (loadingScreen.handleMouseEvent(e, getWidth(), getHeight())) return;
                }
                if (commandHud.isActive() && commandHud.isVisible()) {
                    if (commandHud.handleMouseEvent(e, getWidth(), getHeight())) return;
                }
                if (debugPanel.isVisible()) {
                    if (debugPanel.handleMouseEvent(e, getWidth(), getHeight())) return;
                }

                // Dispatch to other objects in reverse order (top to bottom)
                for (int i = guiObjects.size() - 1; i >= 0; i--) {
                    GuiObject obj = guiObjects.get(i);
                    if (obj == commandHud || obj == debugPanel) continue;
                    
                    if (obj.handleMouseEvent(e, getWidth(), getHeight())) {
                        break;
                    }
                }
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    public void updateStats(double fps, double x, double y, double z, double yaw, double pitch, int objects, int polygons, int seed, boolean flying, String biome) {
        debugPanel.updateStats(fps, x, y, z, yaw, pitch, objects, polygons, seed, flying, biome);
    }

    public void toggleDebugPanel() {
        debugPanel.setVisible(!debugPanel.isVisible());
    }

    public void showLoadingScreen() {
        loadingScreen.setProgress(0f, "Initializing...");
        loadingScreen.setVisible(true);
    }

    public void hideLoadingScreen() {
        loadingScreen.setVisible(false);
    }

    public void setLoadingProgress(float progress, String status) {
        loadingScreen.setProgress(progress, status);
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

    public void addTexture(GuiTexture texture) { addObject(texture); }

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
            // Draw regular objects first, skip command hud and debug panel to draw them last
            if (obj == commandHud || obj == debugPanel) continue;
            
            obj.draw(g2d, getWidth(), getHeight());
            // Most GUI objects use standard Graphics2D, but GuiText uses custom GL shaders.
            // To be safe, we reset state after each object.
            resetGlShaderState();
        }

        // Draw system overlays last to ensure they are on top
        if (loadingScreen.isVisible()) {
            loadingScreen.draw(g2d, getWidth(), getHeight());
            resetGlShaderState();
        }
        if (debugPanel.isVisible()) {
            debugPanel.draw(g2d, getWidth(), getHeight());
            resetGlShaderState();
        }
        if (commandHud.isVisible() && commandHud.isActive()) {
            commandHud.draw(g2d, getWidth(), getHeight());
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
