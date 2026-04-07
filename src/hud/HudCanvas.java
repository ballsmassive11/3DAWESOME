package hud;

import world.World;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLContext;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.J3DGraphics2D;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Canvas3D subclass that draws the HUD overlay directly via postRender(),
 * bypassing the heavyweight/lightweight Swing mixing issue.
 */
public class HudCanvas extends Canvas3D {
    private volatile double fps = 0;
    private volatile double camX = 0, camY = 0, camZ = 0;
    private volatile double yawDeg = 0, pitchDeg = 0;
    private volatile int objectCount = 0;
    private volatile int polygonCount = 0;
    private volatile int seed = 0;
    private volatile boolean flying = false;

    private final CommandHud commandHud = new CommandHud();

    public HudCanvas(GraphicsConfiguration config, World world) {
        super(config);
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

    public CommandHud getCommandHud() { return commandHud; }

    @Override
    public void postRender() {
        // The terrain/water ShaderAppearances leave texture units and the GLSL program
        // bound, which causes J3DGraphics2D to render 2D shapes with active shaders
        // (HUD invisible, or a screen-space lighting cone from the water shader).
        // Reset texture units AND unbind the shader program before drawing the overlay.
        try {
            GL2 gl = GLContext.getCurrent().getGL().getGL2();
            gl.glUseProgram(0);  // unbind any active GLSL shader
            for (int i = 3; i >= 0; i--) {
                gl.glActiveTexture(GL2.GL_TEXTURE0 + i);
                gl.glBindTexture(GL2.GL_TEXTURE_2D, 0);
                gl.glDisable(GL2.GL_TEXTURE_2D);
            }
            gl.glActiveTexture(GL2.GL_TEXTURE0);
        } catch (Exception ignored) {}

        J3DGraphics2D g2d = getGraphics2D();
        drawHud(g2d);
        commandHud.draw(g2d, getWidth(), getHeight());
        g2d.flush(false);
    }

    private void drawHud(Graphics2D g2) {
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
