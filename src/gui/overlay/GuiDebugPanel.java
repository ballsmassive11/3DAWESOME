package gui.overlay;

import gui.core.GuiObject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A GUI element that renders camera and performance stats.
 */
public class GuiDebugPanel extends GuiObject {
    private double fps = 0;
    private double camX = 0, camY = 0, camZ = 0;
    private double yawDeg = 0, pitchDeg = 0;
    private int objectCount = 0;
    private int polygonCount = 0;
    private int seed = 0;
    private int entityCount = 0;
    private String biome = "Unknown";
    private boolean flying = false;

    public GuiDebugPanel() {
        this.visible = true;
    }

    public void updateStats(double fps, double x, double y, double z, double yaw, double pitch, int objects, int polygons, int seed, int entities, String biome, boolean flying) {
        this.fps = fps;
        this.camX = x;
        this.camY = y;
        this.camZ = z;
        this.yawDeg = Math.toDegrees(yaw);
        this.pitchDeg = Math.toDegrees(pitch);
        this.objectCount = objects;
        this.polygonCount = polygons;
        this.seed = seed;
        this.entityCount = entities;
        this.biome = biome;
        this.flying = flying;
    }

    @Override
    public void draw(Graphics2D g2, int screenWidth, int screenHeight) {
        if (!visible) return;

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
        lineList.add(String.format("Ents:   %d", entityCount));
        lineList.add(String.format("Tris:   %,d", polygonCount));
        lineList.add(String.format("Seed:   %d", seed));
        lineList.add(String.format("Biome:  %s", biome));
        if (flying) lineList.add("** FLYING **");
        String[] lines = lineList.toArray(new String[0]);

        FontMetrics fm = g2.getFontMetrics();
        int lineHeight = fm.getHeight();
        int pad = 8;
        int maxWidth = 0;
        for (String line : lines) maxWidth = Math.max(maxWidth, fm.stringWidth(line));

        int bgW = maxWidth + pad * 2;
        int bgH = lines.length * lineHeight + pad;
        int bgX = screenWidth - bgW - pad;
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
