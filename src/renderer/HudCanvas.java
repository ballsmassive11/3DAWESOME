package renderer;

import javax.media.j3d.Canvas3D;
import javax.media.j3d.J3DGraphics2D;
import java.awt.*;

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

    public HudCanvas(GraphicsConfiguration config) {
        super(config);
    }

    public void updateStats(double fps, double x, double y, double z, double yaw, double pitch, int objects, int polygons) {
        this.fps = fps;
        this.camX = x;
        this.camY = y;
        this.camZ = z;
        this.yawDeg = Math.toDegrees(yaw);
        this.pitchDeg = Math.toDegrees(pitch);
        this.objectCount = objects;
        this.polygonCount = polygons;
    }

    @Override
    public void postRender() {
        J3DGraphics2D g2d = getGraphics2D();
        drawHud(g2d);
        g2d.flush(false);
    }

    private void drawHud(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));

        String[] lines = {
            String.format("FPS:    %.1f", fps),
            String.format("X:      %.2f", camX),
            String.format("Y:      %.2f", camY),
            String.format("Z:      %.2f", camZ),
            String.format("Yaw:    %.1f\u00b0", yawDeg),
            String.format("Pitch:  %.1f\u00b0", pitchDeg),
            String.format("Objs:   %d", objectCount),
            String.format("Tris:   %,d", polygonCount),
        };

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

        g2.setColor(Color.WHITE);
        int textX = bgX + pad;
        int textY = bgY + fm.getAscent() + pad / 2;
        for (String line : lines) {
            g2.drawString(line, textX, textY);
            textY += lineHeight;
        }
    }
}
