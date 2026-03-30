package renderer;

import objects.MeshObject;
import world.World;

import javax.media.j3d.Canvas3D;
import javax.media.j3d.J3DGraphics2D;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
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

    private final World world;
    private final List<String[]> spawnableObjects = new ArrayList<>(); // [name, objPath, usesMtl]
    private volatile int hoveredButton = -1;
    private Rectangle[] buttonRects = new Rectangle[0];

    public HudCanvas(GraphicsConfiguration config, World world) {
        super(config);
        this.world = world;
        scanResources();

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hoveredButton = hitTest(e.getX(), e.getY());
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                hoveredButton = hitTest(e.getX(), e.getY());
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = hitTest(e.getX(), e.getY());
                if (idx >= 0) spawnObject(idx);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredButton = -1;
            }
        });
    }

    private void scanResources() {
        File resourcesDir = new File("src/resources");
        if (!resourcesDir.isDirectory()) return;
        File[] dirs = resourcesDir.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File dir : dirs) {
            File[] objs = dir.listFiles(f -> f.getName().endsWith(".obj"));
            if (objs == null || objs.length == 0) continue;
            File obj = objs[0];
            File[] mtls = dir.listFiles(f -> f.getName().endsWith(".mtl"));
            boolean hasMtl = mtls != null && mtls.length > 0;
            spawnableObjects.add(new String[]{dir.getName(), obj.getPath(), hasMtl ? "true" : "false"});
        }
    }

    private int hitTest(int mx, int my) {
        Rectangle[] rects = buttonRects;
        for (int i = 0; i < rects.length; i++) {
            if (rects[i] != null && rects[i].contains(mx, my)) return i;
        }
        return -1;
    }

    private void spawnObject(int index) {
        if (index < 0 || index >= spawnableObjects.size()) return;
        String[] info = spawnableObjects.get(index);
        boolean useMtl = "true".equals(info[2]);
        MeshObject obj = new MeshObject(info[1], useMtl);
        // Spawn 8 units in front of the camera
        double yaw = Math.toRadians(yawDeg);
        float spawnX = (float) (camX - Math.sin(yaw) * 8);
        float spawnY = (float) camY;
        float spawnZ = (float) (camZ - Math.cos(yaw) * 8);
        obj.setPosition(spawnX, spawnY, spawnZ);
        obj.setScale(2.0f);
        world.addObject(obj);
    }

    public void updateStats(double fps, double x, double y, double z, double yaw, double pitch, int objects, int polygons, int seed) {
        this.fps = fps;
        this.camX = x;
        this.camY = y;
        this.camZ = z;
        this.yawDeg = Math.toDegrees(yaw);
        this.pitchDeg = Math.toDegrees(pitch);
        this.objectCount = objects;
        this.polygonCount = polygons;
        this.seed = seed;
    }

    @Override
    public void postRender() {
        J3DGraphics2D g2d = getGraphics2D();
        drawHud(g2d);
        drawSpawner(g2d);
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
            String.format("Seed:   %d", seed),
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

    private void drawSpawner(Graphics2D g2) {
        if (spawnableObjects.isEmpty()) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();

        int pad = 8;
        int btnPad = 6;
        int lineHeight = fm.getHeight();
        int btnHeight = lineHeight + btnPad * 2;
        int titleHeight = lineHeight + pad;
        int n = spawnableObjects.size();

        // Compute panel width from longest name + "Spawn " prefix
        int maxNameW = fm.stringWidth("SPAWN OBJECTS");
        for (String[] info : spawnableObjects) {
            maxNameW = Math.max(maxNameW, fm.stringWidth(info[0]));
        }
        int panelW = maxNameW + pad * 2 + btnPad * 2;
        int panelH = titleHeight + pad / 2 + n * (btnHeight + 4) + pad;
        int panelX = pad;
        int panelY = pad;

        // Background
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRoundRect(panelX, panelY, panelW, panelH, 10, 10);

        // Title
        g2.setColor(new Color(200, 200, 255, 220));
        int titleX = panelX + (panelW - fm.stringWidth("SPAWN OBJECTS")) / 2;
        int titleY = panelY + fm.getAscent() + pad / 2;
        g2.drawString("SPAWN OBJECTS", titleX, titleY);

        // Separator line
        int sepY = panelY + titleHeight + 2;
        g2.setColor(new Color(255, 255, 255, 60));
        g2.drawLine(panelX + pad, sepY, panelX + panelW - pad, sepY);

        // Buttons
        int hovered = hoveredButton;
        Rectangle[] rects = new Rectangle[n];
        int btnX = panelX + pad;
        int btnW = panelW - pad * 2;
        int btnY = sepY + pad / 2 + 2;

        for (int i = 0; i < n; i++) {
            rects[i] = new Rectangle(btnX, btnY, btnW, btnHeight);

            if (i == hovered) {
                g2.setColor(new Color(100, 140, 255, 180));
                g2.fillRoundRect(btnX, btnY, btnW, btnHeight, 6, 6);
                g2.setColor(new Color(180, 200, 255, 220));
                g2.drawRoundRect(btnX, btnY, btnW, btnHeight, 6, 6);
            } else {
                g2.setColor(new Color(40, 40, 60, 160));
                g2.fillRoundRect(btnX, btnY, btnW, btnHeight, 6, 6);
                g2.setColor(new Color(120, 120, 160, 120));
                g2.drawRoundRect(btnX, btnY, btnW, btnHeight, 6, 6);
            }

            String label = spawnableObjects.get(i)[0];
            g2.setColor(i == hovered ? Color.WHITE : new Color(200, 200, 220));
            int lx = btnX + (btnW - fm.stringWidth(label)) / 2;
            int ly = btnY + btnPad + fm.getAscent();
            g2.drawString(label, lx, ly);

            btnY += btnHeight + 4;
        }

        buttonRects = rects;
    }
}
