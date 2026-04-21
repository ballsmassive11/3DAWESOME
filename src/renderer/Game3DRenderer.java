package renderer;

import gui.CommandHud;
import gui.GuiCanvas;
import renderer.skybox.Skybox;
import objects.BaseObject;
import world.*;

import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.universe.SimpleUniverse;
import com.sun.j3d.utils.universe.ViewingPlatform;
import physics.AABB;
import physics.TerrainHeightProvider;
import java.util.HashSet;
import java.util.Set;
import java.awt.*;
import java.awt.event.*;

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
    private boolean skyboxIsDay = true;

    // Guard against calling setColor() on lights while the render structure is being rebuilt
    // (e.g. during startup or after clearObjects/addObject). The RenderStructureUpdateThread
    // will NPE in processLightChanged if it finds a render atom whose lightBin is not yet set.
    // lightResumeTimeMs is set to Long.MAX_VALUE until setupScene() calls addBranchGraph, at
    // which point it is reset to now+5s so the full warmup applies from when the scene goes live.
    private volatile boolean lightUpdateEnabled = true;
    private volatile long lightResumeTimeMs = Long.MAX_VALUE; // reset in setupScene() after addBranchGraph

    // Skybox cross-fade state
    private enum TransState { NONE, FADING_IN, FADING_OUT }
    private TransState transState = TransState.NONE;
    private boolean pendingSwapToDay;
    private float veilAlpha = 0f;
    // Each half (fade-in or fade-out) takes this many seconds
    private static final float TRANS_HALF_SECS = 7f;

    // 3rd-person orbit camera
    private double camOrbitRadius = 5.0;
    private static final double CAM_ZOOM_SPEED = 5.0;
    private static final double CAM_MIN_RADIUS = 1.0;
    private static final double CAM_MAX_RADIUS = 20.0;
    private final Set<Integer> zoomKeys = new HashSet<>();

    public Game3DRenderer(World world) {
        this.world = world;
        initializeRenderer();
    }

    private void initializeRenderer() {
        GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();
        canvas = new GuiCanvas(config, world);
        canvas.setSize(800, 600);
        canvas.setFocusable(true);

        CommandHandler cmdHandler = new CommandHandler(world, this);
        canvas.getCommandHud().addListener(event -> cmdHandler.handle(event.getText()));

        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                CommandHud cmdHud = canvas.getCommandHud();
                if (cmdHud.handleToggle(e.getKeyCode())) return;
                if (cmdHud.isActive()) {
                    cmdHud.keyPressed(e.getKeyCode());
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0);
                if (e.getKeyCode() == KeyEvent.VK_F3) { canvas.toggleDebugPanel(); return; }
                int kc = e.getKeyCode();
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

        universe = new SimpleUniverse(canvas);
        View view = universe.getViewer().getView();
        view.setFieldOfView(fov);
        view.setBackClipDistance(renderDistance);
        view.setFrontClipDistance(0.05);
        view.setTransparencySortingPolicy(View.TRANSPARENCY_SORT_GEOMETRY);

        viewingPlatform = universe.getViewingPlatform();
        viewTransformGroup = viewingPlatform.getViewPlatformTransform();

        syncCamera();
        setupScene();
    }

    private void setupScene() {
        Color3f dayFogColor   = new Color3f(0.9f,  0.9f,  0.9f);
        Color3f nightFogColor = new Color3f(0.03f, 0.03f, 0.10f);

        skybox = new Skybox("/skyboxes/cloudy_sky", "png", dayFogColor);
        skybox.preloadNightSky("/skyboxes/night2", "png", nightFogColor);

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

    /** Call after scene mutations are complete. Waits 4s before resuming light updates. */
    public void notifySceneReady()    { lightResumeTimeMs = System.currentTimeMillis() + 4000; lightUpdateEnabled = true; }

    public void updateDayNight(double deltaTime) {
        if (dayNightCycle == null) return;
        if (!lightUpdateEnabled) return;
        if (System.currentTimeMillis() < lightResumeTimeMs) return;
        dayNightCycle.update(deltaTime);

        world.getLighting().setAmbientColor(dayNightCycle.getAmbientColor());
        world.getLighting().setDirectionalColor(dayNightCycle.getSunColor());
        Color3f fogColor = dayNightCycle.getFogColor();
        fog.setColor(fogColor);
        // Keep the skybox fog gradient in sync so it transitions smoothly rather than snapping
        if (skybox != null) skybox.setFogOverlayColor(fogColor);

        // Skybox cross-fade state machine
        boolean nowDay = dayNightCycle.isDay();
        float step = (float) (deltaTime / TRANS_HALF_SECS);

        switch (transState) {
            case NONE:
                if (nowDay != skyboxIsDay) {
                    transState = TransState.FADING_IN;
                    pendingSwapToDay = nowDay;
                }
                break;
            case FADING_IN:
                veilAlpha = Math.min(1f, veilAlpha + step);
                if (veilAlpha >= 1f) {
                    // Veil fully covers sky — safe to swap underneath
                    if (skybox != null) {
                        skybox.setDaytime(pendingSwapToDay);
                        skyboxIsDay = pendingSwapToDay;
                    }
                    transState = TransState.FADING_OUT;
                }
                break;
            case FADING_OUT:
                veilAlpha = Math.max(0f, veilAlpha - step);
                if (veilAlpha <= 0f) transState = TransState.NONE;
                break;
        }

        if (skybox != null) skybox.setVeilAlpha(veilAlpha);
    }

    public void syncCamera(double deltaTime) {
        updateDayNight(deltaTime);
        syncSkybox();
        if (skybox != null) skybox.update(deltaTime);

        if (zoomKeys.contains(KeyEvent.VK_I))
            camOrbitRadius = Math.max(CAM_MIN_RADIUS, camOrbitRadius - CAM_ZOOM_SPEED * deltaTime);
        if (zoomKeys.contains(KeyEvent.VK_O))
            camOrbitRadius = Math.min(CAM_MAX_RADIUS, camOrbitRadius + CAM_ZOOM_SPEED * deltaTime);

        Camera cam = world.getCamera();
        double yaw   = cam.getYaw();
        double pitch = cam.getPitch();
        Vector3d lookAt = cam.getPosition();

        double sinY = Math.sin(yaw), cosY = Math.cos(yaw);
        double sinP = Math.sin(pitch), cosP = Math.cos(pitch);

        double idealR = camOrbitRadius;
        double idealX = lookAt.x + sinY * cosP * idealR;
        double idealY = lookAt.y - sinP * idealR;
        double idealZ = lookAt.z + cosY * cosP * idealR;

        double r = idealR * occlusionT(lookAt, idealX, idealY, idealZ);
        double camX = lookAt.x + sinY * cosP * r;
        double camY = lookAt.y - sinP * r;
        double camZ = lookAt.z + cosY * cosP * r;

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
        canvas.updateStats(fps, x, y, z, yaw, pitch, objects, polygons, seed, flying);
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
