package gui;

import gui.commands.CommandHud;
import gui.text.BitmapFont;
import gui.text.GuiText;
import gui.vec.Vector2;

import world.World;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLContext;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.J3DGraphics2D;
import java.awt.*;
import java.util.ArrayList;
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

    private volatile double fps = 0;
    private volatile double camX = 0, camY = 0, camZ = 0;
    private volatile double yawDeg = 0, pitchDeg = 0;
    private volatile int objectCount = 0;
    private volatile int polygonCount = 0;
    private volatile int seed = 0;
    private volatile boolean flying = false;
    private volatile boolean debugVisible = true;

    private final CommandHud commandHud = new CommandHud();
    private GuiTexture crosshair;
    private GuiTexture joey;

    private final World world;

    /** User-managed text elements drawn every frame. Thread-safe. */
    private final List<GuiText> texts = new CopyOnWriteArrayList<>();

    /** User-managed frame elements drawn every frame. Thread-safe. */
    private final List<GuiFrame> frames = new CopyOnWriteArrayList<>();

    public GuiCanvas(GraphicsConfiguration config, World world) {
        super(config);
        this.world = world;
        crosshair = new GuiTexture("/gui/SreTransparentCrop.png");
        crosshair.setCentered(true);
        crosshair.setPosition(new Vector2(100f,0.1f,200f, 0.1f));
        crosshair.setSize(Vector2.ofOffset(250f, 300f));

        joey = new GuiTexture("/gui/joey.png");
        joey.setCentered(true);
        joey.setPosition(new Vector2(100f,0.1f,200f, 0.5f));
        joey.setSize(Vector2.ofOffset(250f, 250));
    }

    public void updateStats(double fps, double x, double y, double z, double yaw, double pitch, int objects, int polygons, int seed, boolean flying) {
        this.fps = fps;
        this.camX = x;
        this.camY = y;
        this.camZ = z;
        this.yawDeg = Math.toDegrees(yaw);
        this.pitchDeg = Math.toDegrees(pitch);
        this.objectCount = objects;
        this.polygonCount = polygons;
        this.seed = seed;
        this.flying = flying;
    }

    public void toggleDebugPanel() {
        debugVisible = !debugVisible;
    }

    public CommandHud getCommandHud() { return commandHud; }

    // ------------------------------------------------------------------ //
    // Text management                                                     //
    // ------------------------------------------------------------------ //

    /** Adds a {@link GuiText} element to be drawn every frame. */
    public void addText(GuiText text)    { texts.add(text); }

    /** Removes a previously added {@link GuiText} element. */
    public void removeText(GuiText text) { texts.remove(text); }

    /** Removes all {@link GuiText} elements. */
    public void clearTexts()             { texts.clear(); }

    // ------------------------------------------------------------------ //
    // Frame management                                                    //
    // ------------------------------------------------------------------ //

    /** Adds a {@link GuiFrame} element to be drawn every frame. */
    public void addFrame(GuiFrame frame)    { frames.add(frame); }

    /** Removes a previously added {@link GuiFrame} element. */
    public void removeFrame(GuiFrame frame) { frames.remove(frame); }

    /** Removes all {@link GuiFrame} elements. */
    public void clearFrames()             { frames.clear(); }

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

        crosshair.draw(g2d, getWidth(), getHeight());
        joey.draw(g2d, getWidth(), getHeight());

        for (GuiFrame f : frames) {
            f.draw(g2d, getWidth(), getHeight());
            resetGlShaderState();
        }

        for (GuiText t : texts) {
            t.draw(g2d, getWidth(), getHeight());
            resetGlShaderState(); // Ensure state is clean after each text element
        }

        if (debugVisible) drawDebugPanel(g2d);
        commandHud.draw(g2d, getWidth(), getHeight());
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

    private void drawDebugPanel(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));

        List<String> lineList = new ArrayList<>();
        lineList.add(String.format("FPS:    %.1f", fps));
        lineList.add(String.format("X:      %.2f", camX));
        lineList.add(String.format("Y:      %.2f", camY));
        lineList.add(String.format("Z:      %.2f", camZ));
        lineList.add(String.format("Yaw:    %.1f\u00b0", yawDeg));
        lineList.add(String.format("Pitch:  %.1f\u00b0", pitchDeg));
        lineList.add(String.format("Objs:   %d", objectCount));
        lineList.add(String.format("Tris:   %,d", polygonCount));
        lineList.add(String.format("Seed:   %d", seed));
        if (flying) lineList.add("** FLYING **");
        String[] lines = lineList.toArray(new String[0]);

        FontMetrics fm = g2.getFontMetrics();
        int lineHeight = fm.getHeight();
        int pad = 8;
        int maxWidth = 0;
        for (String line : lines) maxWidth = Math.max(maxWidth, fm.stringWidth(line));

        int bgW = maxWidth + pad * 2;
        int bgH = lines.length * lineHeight + pad;
        int bgX = getWidth() - bgW - pad;
        int bgY = pad;

        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRoundRect(bgX, bgY, bgW, bgH, 10, 10);

        int textX = bgX + pad;
        int textY = bgY + fm.getAscent() + pad / 2;
        for (String line : lines) {
            g2.setColor(line.startsWith("**") ? new Color(120, 220, 255) : Color.WHITE);
            g2.drawString(line, textX, textY);
            textY += lineHeight;
        }
    }
}
