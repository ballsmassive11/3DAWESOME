package renderer;

import gui.canvas.GuiCanvas;
import gui.commands.CommandHud;
import renderer.skybox.Skybox;
import objects.BaseObject;
import world.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.universe.SimpleUniverse;
import com.sun.j3d.utils.universe.ViewingPlatform;
import gui.overlay.PauseMenu;
import gui.overlay.UnderwaterOverlay;
import physics.AABB;
import physics.TerrainHeightProvider;
import terrain.MapGenerator;
import water.WaterTile;
import java.util.HashSet;
import java.util.Set;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Responsible for the Java3D rendering pipeline: camera, scene setup, fog, and skybox.
 * Command handling is delegated to {@link CommandHandler}.
 */
public class Game3DRenderer {
    private GuiCanvas canvas;
    private SimpleUniverse universe;
    private World world;
    private ViewingPlatform viewingPlatform;
    private TransformGroup viewTransformGroup;
    private double fov = Math.PI / 3.0;
    private double renderDistance = 70.0;
    // Fraction of render distance over which fog fades in (0=instant at clip, 1=starts at origin).
    private double fogMargin = 0.5;
    private LinearFog fog;
    private Skybox skybox;
    private DayNightCycle dayNightCycle;
    private UnderwaterOverlay underwaterOverlay;
    private boolean skyboxIsDay = true; // No longer strictly needed but kept for other logic if any

    // Guard against calling setColor() on lights while the render structure is being rebuilt
    // (e.g. during startup or after clearObjects/addObject). The RenderStructureUpdateThread
    // will NPE in processLightChanged if it finds a render atom whose lightBin is not yet set.
    // lightResumeTimeMs is set to Long.MAX_VALUE until setupScene() calls addBranchGraph, at
    // which point it is reset to now+5s so the full warmup applies from when the scene goes live.
    private volatile boolean lightUpdateEnabled = true;
    private volatile long lightResumeTimeMs = Long.MAX_VALUE; // reset in setupScene() after addBranchGraph

    // Skybox cross-fade state
    private float skyMix = 0f; // 0 = day, 1 = night
    // Each cross-fade takes this many seconds
    private static final float TRANS_SECS = 3f;

    // 3rd-person orbit camera
    private double camOrbitRadius = 5.0;
    private static final double CAM_ZOOM_SPEED = 5.0;
    private static final double CAM_MIN_RADIUS = 1.0;
    private static final double CAM_MAX_RADIUS = 20.0;
    private final Set<Integer> zoomKeys = new HashSet<>();

    private boolean menuActive = false;
    private PauseMenu pauseMenu;
    private boolean pauseActive = false;
    private boolean hasDoneInitialLightUpdate = false;

    private View view;

    private boolean rightMouseDown = false;
    private Point lastMousePos = new Point();
    private Robot robot;
    private Cursor transparentCursor;

    private int lastShiftLockX = -1;
    private int lastShiftLockY = -1;

    public Game3DRenderer(World world) {
        this.world = world;
        initializeRenderer();
    }

    private void initializeRenderer() {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }

        BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        transparentCursor = Toolkit.getDefaultToolkit().createCustomCursor(cursorImg, new Point(0, 0), "blank cursor");

        GraphicsConfigTemplate3D template = new GraphicsConfigTemplate3D();
        template.setSceneAntialiasing(GraphicsConfigTemplate3D.PREFERRED);
        GraphicsConfiguration config = GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .getBestConfiguration(template);
        if (config == null) config = SimpleUniverse.getPreferredConfiguration();
        canvas = new GuiCanvas(config, world);
        canvas.setSize(800, 600);
        canvas.setFocusable(true);

        CommandHandler cmdHandler = new CommandHandler(world, this);
        canvas.getCommandHud().addListener(event -> cmdHandler.handle(event.getText()));

        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (menuActive) return;
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    boolean shift = (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0;
                    if (shift) { System.exit(0); return; }
                    togglePause();
                    return;
                }
                if (pauseActive) return;
                CommandHud cmdHud = canvas.getCommandHud();
                if (cmdHud.handleToggle(e.getKeyCode())) return;
                if (cmdHud.isActive()) {
                    cmdHud.keyPressed(e.getKeyCode());
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_F3) { canvas.toggleDebugPanel(); return; }
                int kc = e.getKeyCode();
                if (kc == KeyEvent.VK_SHIFT) {
                    if (!world.getPlayer().getPhysics().isFlying()) {
                        boolean now = !world.getPlayer().isShiftLockEnabled();
                        world.getPlayer().setShiftLockEnabled(now);
                        updateShiftLockCursor();
                    }
                }
                if (kc == KeyEvent.VK_I || kc == KeyEvent.VK_O) {
                    zoomKeys.add(kc);
                    return;
                }
                world.getCamera().keyPressed(kc);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                CommandHud cmdHud = canvas.getCommandHud();
                if (cmdHud.isActive()) cmdHud.keyTyped(e.getKeyChar());
            }

            @Override
            public void keyReleased(KeyEvent e) {
                zoomKeys.remove(e.getKeyCode());
                if (!canvas.getCommandHud().isActive()) world.getCamera().keyReleased(e.getKeyCode());
            }
        });

        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    rightMouseDown = true;
                    lastMousePos = e.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    rightMouseDown = false;
                }
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseMove(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouseMove(e);
            }

            private void handleMouseMove(MouseEvent e) {
                if (canvas.getCommandHud().isActive()) return;

                if (world.getPlayer().isShiftLockEnabled()) {
                    centerMouse(e);
                } else if (rightMouseDown) {
                    double dx = e.getX() - lastMousePos.x;
                    double dy = e.getY() - lastMousePos.y;

                    if (dx != 0 || dy != 0) {
                        world.getCamera().mouseRotate(dx, dy);
                        // Lock mouse in place during right-drag
                        Point screenPos = canvas.getLocationOnScreen();
                        screenPos.translate(lastMousePos.x, lastMousePos.y);
                        robot.mouseMove(screenPos.x, screenPos.y);
                    }
                }
            }

            private void centerMouse(MouseEvent e) {
                if (e.getX() == lastShiftLockX && e.getY() == lastShiftLockY) return;

                Point center = new Point(canvas.getWidth() / 2, canvas.getHeight() / 2);
                double dx = e.getX() - center.x;
                double dy = e.getY() - center.y;

                if (dx != 0 || dy != 0) {
                    world.getCamera().mouseRotate(dx, dy);
                    lastShiftLockX = center.x;
                    lastShiftLockY = center.y;
                    Point screenCenter = canvas.getLocationOnScreen();
                    screenCenter.translate(center.x, center.y);
                    robot.mouseMove(screenCenter.x, screenCenter.y);
                }
            }
        });

        universe = new SimpleUniverse(canvas);
        view = universe.getViewer().getView();
        view.setFieldOfView(fov);
        view.setBackClipDistance(renderDistance);
        view.setFrontClipDistance(0.05);
        view.setTransparencySortingPolicy(View.TRANSPARENCY_SORT_GEOMETRY);
        view.setSceneAntialiasingEnable(GameSettings.antialiasing);

        viewingPlatform = universe.getViewingPlatform();
        viewTransformGroup = viewingPlatform.getViewPlatformTransform();

        underwaterOverlay = new UnderwaterOverlay();
        canvas.addObject(underwaterOverlay);

        syncCamera();
        setupScene();
    }

    private void setupScene() {
        Color3f dayFogColor   = new Color3f(0.9f,  0.9f,  0.9f);
        Color3f nightFogColor = new Color3f(0.03f, 0.03f, 0.10f);

        skybox = new Skybox("/sky/cubemaps/sky2", "png", dayFogColor);
        skybox.preloadNightSky("/sky/cubemaps/night2", "png", nightFogColor);
        skybox.addSunMoon("/sky/sun/sun.png", "/sky/sun/moon.png");

        BranchGroup sceneBG = world.getSceneBranchGroup();
        sceneBG.addChild(skybox.getBackground());

        fog = new LinearFog(dayFogColor, renderDistance * (1 - fogMargin)*2, renderDistance*2);
        fog.setCapability(LinearFog.ALLOW_COLOR_WRITE);
        fog.setCapability(LinearFog.ALLOW_DISTANCE_WRITE);
        fog.setInfluencingBounds(new BoundingSphere(new Point3d(0, 0, 0), Double.MAX_VALUE));
        sceneBG.addChild(fog);

        dayNightCycle = new DayNightCycle();

        sceneBG.addChild(new WorldUpdateBehavior(world, this));
        universe.addBranchGraph(sceneBG);

        // Start the warmup timer now that the scene is live. lightResumeTimeMs was held at
        // Long.MAX_VALUE until this point so no light updates could slip through during
        // canvas/GL initialization (which can take ~1s and would have eaten the old 3s budget).
        lightResumeTimeMs = System.currentTimeMillis() + 5000;
    }

    // -------------------------------------------------------------------------
    // Per-frame update
    // -------------------------------------------------------------------------

    /** Call before large scene mutations (clearObjects, bulk addObject). */
    public void notifySceneChanging() { lightUpdateEnabled = false; }

    /** Call after scene mutations are complete. Waits 0.5s before resuming light updates. */
    public void notifySceneReady()    { lightResumeTimeMs = System.currentTimeMillis() + 500; lightUpdateEnabled = true; }

    public void updateDayNight(double deltaTime) {
        if (dayNightCycle == null) return;
        if (!lightUpdateEnabled) return;

        // Force an initial update if we haven't updated yet, regardless of timer
        boolean timerExpired = System.currentTimeMillis() >= lightResumeTimeMs;
        if (!timerExpired && hasDoneInitialLightUpdate) return;

        dayNightCycle.update(deltaTime);
        hasDoneInitialLightUpdate = true;

        world.getLighting().setAmbientColor(dayNightCycle.getAmbientColor());
        world.getLighting().setDirectionalColor(dayNightCycle.getSunColor());
        world.getLighting().setDirectionalDirection(dayNightCycle.getSunDirection());
        Color3f fogColor = dayNightCycle.getFogColor();
        fog.setColor(fogColor);
        // Keep the skybox fog gradient in sync so it transitions smoothly rather than snapping
        if (skybox != null) skybox.setFogOverlayColor(fogColor);

        // Water darkening
        WaterTile.setDaylightFactor((float) dayNightCycle.getDaylightFactor());

        // Skybox cross-fade
        boolean nowDay = dayNightCycle.isDay();
        float step = (float) (deltaTime / TRANS_SECS);

        if (nowDay) {
            skyMix = Math.max(0f, skyMix - step);
        } else {
            skyMix = Math.min(1f, skyMix + step);
        }

        if (skybox != null) skybox.setSkyMix(skyMix);
    }

    public void syncCamera(double deltaTime) {
        updateDayNight(deltaTime);
        syncSkybox();
        if (skybox != null) skybox.update(deltaTime, dayNightCycle != null ? dayNightCycle.getTimeOfDay() : 0.3);

        if (zoomKeys.contains(KeyEvent.VK_I))
            camOrbitRadius = Math.max(CAM_MIN_RADIUS, camOrbitRadius - CAM_ZOOM_SPEED * deltaTime);
        if (zoomKeys.contains(KeyEvent.VK_O))
            camOrbitRadius = Math.min(CAM_MAX_RADIUS, camOrbitRadius + CAM_ZOOM_SPEED * deltaTime);

        Camera cam = world.getCamera();
        double yaw   = cam.getYaw();
        double pitch = cam.getPitch();
        Vector3d basePos = cam.getPosition();
        Vector3d offset  = cam.getOffset();

        double sinY = Math.sin(yaw), cosY = Math.cos(yaw);
        double sinP = Math.sin(pitch), cosP = Math.cos(pitch);

        // Calculate lookAt position by applying offset in view-relative space
        // offset.x: right, offset.y: up, offset.z: forward
        Vector3d lookAt = new Vector3d(basePos);
        // right vector: (cosY, 0, -sinY)
        lookAt.x += offset.x * cosY;
        lookAt.z -= offset.x * sinY;
        // up vector: (0, 1, 0)
        lookAt.y += offset.y;
        // forward vector: (-sinY, 0, -cosY)
        lookAt.x -= offset.z * sinY;
        lookAt.z -= offset.z * cosY;

        double idealR = camOrbitRadius;
        double idealX = lookAt.x + sinY * cosP * idealR;
        double idealY = lookAt.y - sinP * idealR;
        double idealZ = lookAt.z + cosY * cosP * idealR;

        double r = idealR * occlusionT(lookAt, idealX, idealY, idealZ);
        double camX = lookAt.x + sinY * cosP * r;
        double camY = lookAt.y - sinP * r;
        double camZ = lookAt.z + cosY * cosP * r;

        if (underwaterOverlay != null) {
            underwaterOverlay.setVisible(camY < 0);
        }

        Transform3D transform = new Transform3D();
        Transform3D rotation  = new Transform3D();
        transform.rotX(pitch);
        rotation.rotY(yaw);
        transform.mul(rotation, transform);
        transform.setTranslation(new Vector3d(camX, camY, camZ));
        viewTransformGroup.setTransform(transform);

    }

    public void syncCamera() { syncCamera(0); }

    private void syncSkybox() {
        if (skybox != null) {
            Vector3d pos = world.getCamera().getPosition();
            skybox.getBackground().setApplicationBounds(
                    new BoundingSphere(new Point3d(pos.x, pos.y, pos.z), Double.MAX_VALUE));
        }
    }

    private double occlusionT(Vector3d from, double idealX, double idealY, double idealZ) {
        double dx = idealX - from.x;
        double dy = idealY - from.y;
        double dz = idealZ - from.z;
        double minT = 1.0;

        TerrainHeightProvider terrain = world.getTerrainProvider();
        if (terrain != null) {
            int steps = 12;
            for (int i = 1; i <= steps; i++) {
                double t  = (double) i / steps;
                double px = from.x + dx * t;
                double py = from.y + dy * t;
                double pz = from.z + dz * t;
                if (py < terrain.getHeightAt((float) px, (float) pz)) {
                    minT = Math.min(minT, Math.max(0.05, t - 1.0 / steps));
                    break;
                }
            }
        }

        for (BaseObject obj : world.getObjects()) {
            AABB box = obj.getWorldAABB();
            if (box == null) continue;
            double tEnter = 0.05, tExit = minT;

            if (Math.abs(dx) < 1e-9) {
                if (from.x < box.minX || from.x > box.maxX) continue;
            } else {
                double t1 = (box.minX - from.x) / dx, t2 = (box.maxX - from.x) / dx;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tEnter = Math.max(tEnter, t1); tExit = Math.min(tExit, t2);
                if (tEnter > tExit) continue;
            }

            if (Math.abs(dy) < 1e-9) {
                if (from.y < box.minY || from.y > box.maxY) continue;
            } else {
                double t1 = (box.minY - from.y) / dy, t2 = (box.maxY - from.y) / dy;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tEnter = Math.max(tEnter, t1); tExit = Math.min(tExit, t2);
                if (tEnter > tExit) continue;
            }

            if (Math.abs(dz) < 1e-9) {
                if (from.z < box.minZ || from.z > box.maxZ) continue;
            } else {
                double t1 = (box.minZ - from.z) / dz, t2 = (box.maxZ - from.z) / dz;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tEnter = Math.max(tEnter, t1); tExit = Math.min(tExit, t2);
                if (tEnter > tExit) continue;
            }

            minT = Math.min(minT, Math.max(0.05, tEnter - 0.02));
        }

        return minT;
    }

    // -------------------------------------------------------------------------
    // Renderer / canvas controls (used by CommandHandler)
    // -------------------------------------------------------------------------

    public void stopRenderer()  { canvas.stopRenderer(); }
    public void startRenderer() { canvas.startRenderer(); }
    public CommandHud getCommandHud() { return canvas.getCommandHud(); }

    // -------------------------------------------------------------------------
    // Fog controls
    // -------------------------------------------------------------------------

    public void togglePause() {
        setPauseActive(!pauseActive);
    }

    public void setPauseActive(boolean active) {
        pauseActive = active;
        if (pauseMenu == null) {
            pauseMenu = new PauseMenu();
            pauseMenu.setOnResume(() -> setPauseActive(false));
            canvas.addObject(pauseMenu);
        }
        pauseMenu.setVisible(active);
        if (active) {
            canvas.setCursor(Cursor.getDefaultCursor());
        } else {
            updateShiftLockCursor();
        }
    }

    public void setMenuActive(boolean active) {
        this.menuActive = active;
        if (active) {
            canvas.setCursor(Cursor.getDefaultCursor());
        } else if (world.getPlayer().isShiftLockEnabled()) {
            canvas.setCursor(transparentCursor);
        }
    }

    public boolean isMenuActive() {
        return menuActive;
    }

    public void updateShiftLockCursor() {
        if (menuActive) return;
        if (world.getPlayer().isShiftLockEnabled()) {
            canvas.setCursor(transparentCursor);
        } else {
            canvas.setCursor(Cursor.getDefaultCursor());
        }
    }

    public void setFogOn(boolean on) {
        if (on && fogMargin > 0) {
            fog.setFrontDistance(renderDistance * (1 - fogMargin)*2);
            fog.setBackDistance(renderDistance*2);
            if (skybox != null) skybox.setFogVisible(true);
        } else {
            fog.setFrontDistance(1e10);
            fog.setBackDistance(1e10);
            if (skybox != null) skybox.setFogVisible(false);
        }
    }

    public void setFogColor(Color3f color) {
        fog.setColor(color);
    }

    public void setFogMargin(double margin) {
        this.fogMargin = margin;
        if (margin <= 0) {
            // margin=0 means no fog — disable entirely (same as fog off)
            fog.setFrontDistance(1e10);
            fog.setBackDistance(1e10);
            if (skybox != null) skybox.setFogVisible(false);
        } else {
            fog.setFrontDistance(renderDistance * (1 - margin)*2);
            fog.setBackDistance(renderDistance*2);
            if (skybox != null) skybox.setFogVisible(true);
        }
    }

    public void setFogNear(double nearDist) {
        nearDist = Math.max(0, Math.min(renderDistance, nearDist));
        this.fogMargin = 1.0 - (nearDist / renderDistance);
        setFogMargin(this.fogMargin);
    }

    public double getFogMargin() { return fogMargin; }

    // -------------------------------------------------------------------------
    // Camera / view settings
    // -------------------------------------------------------------------------

    public double getFov() { return fov; }

    public void setFov(double fovRadians) {
        this.fov = fovRadians;
        universe.getViewer().getView().setFieldOfView(fovRadians);
    }

    public double getRenderDistance() { return renderDistance; }

    public void setRenderDistance(double distance) {
        renderDistance = distance;
        universe.getViewer().getView().setBackClipDistance(renderDistance);
        if (fog != null) {
            fog.setFrontDistance(renderDistance * (1 - fogMargin) *2);
            fog.setBackDistance(renderDistance*2);
        }
    }

    public void setAntialiasing(boolean enabled) {
        view.setSceneAntialiasingEnable(enabled);
    }

    public void setCamera(Vector3d cameraPos, Point3d lookAt) {
        world.getCamera().setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
        syncCamera();
    }

    // -------------------------------------------------------------------------
    // HUD
    // -------------------------------------------------------------------------

    public void updateHud(double fps, double x, double y, double z,
                          double yaw, double pitch, int objects, int polygons,
                          int seed, boolean flying) {
        int entities = world.getEntityCount();
        TerrainHeightProvider provider = world.getTerrainProvider();
        String biome = (provider != null) ? provider.getBiomeAt((float)x, (float)z) : "Unknown";
        canvas.updateStats(fps, x, y, z, yaw, pitch, objects, polygons, seed, entities, biome, flying);
    }

    public DayNightCycle getDayNightCycle() { return dayNightCycle; }
    public Canvas3D getCanvas() { return canvas; }
    public GuiCanvas getGuiCanvas() { return canvas; }
    /** Returns the main ViewPlatform TransformGroup (used by the water RTT system). */
    public TransformGroup getViewTransformGroup() { return viewTransformGroup; }
    public SimpleUniverse getUniverse() { return universe; }

    public void cleanup() {
        if (universe != null) universe.cleanup();
    }
}
