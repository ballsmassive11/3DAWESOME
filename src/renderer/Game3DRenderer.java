package renderer;

import hud.CommandHud;
import hud.HudCanvas;
import objects.BaseObject;
import terrain.MapGenerator;
import world.*;

import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.universe.SimpleUniverse;
import com.sun.j3d.utils.universe.ViewingPlatform;
import java.util.List;
import java.awt.*;
import java.awt.event.*;

/**
 * Renderer class for displaying 3D scenes using Java3D
 */
public class Game3DRenderer {
    private HudCanvas canvas;
    private SimpleUniverse universe;
    private World world;
    private ViewingPlatform viewingPlatform;
    private TransformGroup viewTransformGroup;
    private double fov = Math.PI / 3.0; // 60 degrees default
    private double renderDistance = 10.0;

    /**
     * Create a renderer for the given world
     * @param world The world to render
     */
    public Game3DRenderer(World world) {
        this.world = world;
        initializeRenderer();
    }

    /**
     * Initialize the Java3D rendering pipeline
     */
    private void initializeRenderer() {
        // Create a canvas for 3D rendering
        GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();
        canvas = new HudCanvas(config, world);
        canvas.setSize(800, 600);
        canvas.setFocusable(true);
        canvas.getCommandHud().addListener(event -> handleCommand(event.getText()));
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                CommandHud cmdHud = canvas.getCommandHud();

                // Let the command bar intercept the toggle key first
                if (cmdHud.handleToggle(e.getKeyCode())) return;

                // While the command bar is open, route all input there
                if (cmdHud.isActive()) {
                    cmdHud.keyPressed(e.getKeyCode());
                    return;
                }

                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
                world.getCamera().keyPressed(e.getKeyCode());
            }

            @Override
            public void keyTyped(KeyEvent e) {
                CommandHud cmdHud = canvas.getCommandHud();
                if (cmdHud.isActive()) {
                    cmdHud.keyTyped(e.getKeyChar());
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                // Don't release camera keys while command bar is open so no
                // movement bleeds through when the bar closes.
                if (!canvas.getCommandHud().isActive()) {
                    world.getCamera().keyReleased(e.getKeyCode());
                }
            }
        });

        // Create the universe
        universe = new SimpleUniverse(canvas);
        universe.getViewer().getView().setFieldOfView(fov);
        universe.getViewer().getView().setBackClipDistance(renderDistance);   // render distance
        universe.getViewer().getView().setFrontClipDistance(0.1);

        // Setup the viewing platform
        viewingPlatform = universe.getViewingPlatform();
        viewTransformGroup = viewingPlatform.getViewPlatformTransform();

        // Set default camera position
        syncCamera();

        // Setup the scene
        setupScene();
    }

    /**
     * Sync the Java3D view transform with the Camera object's position and orientation
     */
    public void syncCamera() {
        Camera cam = world.getCamera();
        Transform3D transform = new Transform3D();
        Transform3D rotation = new Transform3D();
        
        // Apply pitch (around X) then yaw (around Y)
        transform.rotX(cam.getPitch());
        rotation.rotY(cam.getYaw());
        transform.mul(rotation, transform);
        
        // Apply position
        transform.setTranslation(cam.getPosition());
        
        // Java3D ViewPlatform transform is inverse of camera transform
        // But usually we set it directly as the "eye" transform.
        // If we use lookAt, we invert it. If we use rot/trans, it IS the eye's transform.
        viewTransformGroup.setTransform(transform);
    }

    /**
     * Setup the scene with background and objects
     */
    private void setupScene() {
        // Create background
        Background background = new Background(world.getBackgroundColor());
        background.setApplicationBounds(new BoundingSphere(new Point3d(0, 0, 0), 100.0));

        BranchGroup sceneBG = world.getSceneBranchGroup();
        sceneBG.addChild(background);
        
        // Add world update behavior and pass renderer for camera sync
        WorldUpdateBehavior behavior = new WorldUpdateBehavior(world, this);
        sceneBG.addChild(behavior);

        // Compile for optimization
        sceneBG.compile();

        // Add to universe
        universe.addBranchGraph(sceneBG);
    }

    /**
     * Set the camera position and look-at point
     * @param cameraPos Position of the camera
     * @param lookAt Point the camera looks at
     */
    public void setCamera(Vector3d cameraPos, Point3d lookAt) {
        world.getCamera().setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
        // We don't have an easy way to set lookAt in Camera yet, but we can at least set position
        syncCamera();
    }

    public void updateHud(double fps, double x, double y, double z, double yaw, double pitch, int objects, int polygons, int seed) {
        canvas.updateStats(fps, x, y, z, yaw, pitch, objects, polygons, seed);
    }

    public void setRenderDistance(double distance) {
        renderDistance = distance;
        universe.getViewer().getView().setBackClipDistance(renderDistance);
    }

    /**
     * Get the Canvas3D for embedding in a frame or panel
     */
    public Canvas3D getCanvas() {
        return canvas;
    }

    /**
     * Get the universe for advanced manipulation
     */
    public SimpleUniverse getUniverse() {
        return universe;
    }

    public double getFov() {
        return fov;
    }

    public void setFov(double fovRadians) {
        this.fov = fovRadians;
        universe.getViewer().getView().setFieldOfView(fovRadians);
    }

    /** Handles text submitted via the CommandHud. */
    private void handleCommand(String text) {
        CommandHud hud = canvas.getCommandHud();
        String[] parts = text.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();

        if (cmd.equals("fov") && parts.length == 2) {
            try {
                double degrees = Double.parseDouble(parts[1]);
                degrees = Math.max(10.0, Math.min(170.0, degrees));
                setFov(Math.toRadians(degrees));
                hud.logOutput("FOV set to " + (int) degrees + "°");
            } catch (NumberFormatException ignored) {
                hud.logOutput("Invalid value: " + parts[1]);
            }
        } else if (cmd.equals("rdist") && parts.length == 2) {
            try {
                double distance = Double.parseDouble(parts[1]);
                distance = Math.max(0.1, distance);
                setRenderDistance(distance);
                hud.logOutput("Render distance set to " + distance);
            } catch (NumberFormatException ignored) {
                hud.logOutput("Invalid value: " + parts[1]);
            }

        } else if (cmd.equals("fun")) {
            hud.logOutput("say less");

            List<BaseObject> objects = world.getObjects();
            for (BaseObject obj : objects) {
                Vector3d Vec = new Vector3d((float)(Math.random()-0.5)*20, (float)(Math.random()-0.5)*20, (float)(Math.random()-0.5)*20);
                obj.setAngularVelocity(Vec);

            }

        } else if (cmd.equals("genmap")) {
            String params = parts.length == 2 ? parts[1] : "";
            generateMap(hud, params);

        } else if (cmd.equals("delmap")) {
            deleteMap(hud);
        }else if (cmd.equals("cmds") || cmd.equals("help")) {
            hud.logOutput("fov <degrees>           - Set field of view (10-170)");
            hud.logOutput("rdist <distance>        - Set render distance");
            hud.logOutput("genmap [key=val ...]    - Regenerate terrain");
            hud.logOutput("  params: seed size height threshold blockwidth");
            hud.logOutput("delmap                  - Delete the current terrain");
            hud.logOutput("fun                     - would recommend turning render distance down");
            hud.logOutput("cmds / help             - Show this message");
        } else {
            hud.logOutput("Unknown command: " + cmd + "  (type 'cmds / help' for list)");
        }
    }

    /**
     * Parses key=value params and regenerates the terrain on a background thread.
     * Supported params: seed, size, height, threshold, blockwidth
     * Example: genmap seed=42 size=120 height=24 threshold=0.1 blockwidth=0.6
     */
    private void generateMap(CommandHud hud, String params) {
        // Parse key=value pairs
        int seed         = (int) System.currentTimeMillis();
        int size         = 160;
        float height     = 16.0f;
        float threshold  = 0.05f;
        float blockWidth = 0.8f;

        for (String token : params.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            String[] kv = token.split("=", 2);
            if (kv.length != 2) { hud.logOutput("Skipping bad param: " + token); continue; }
            String key = kv[0].toLowerCase();
            String val = kv[1];
            try {
                switch (key) {
                    case "seed":       seed       = Integer.parseInt(val);   break;
                    case "size":       size       = Integer.parseInt(val);   break;
                    case "height":     height     = Float.parseFloat(val);   break;
                    case "threshold":  threshold  = Float.parseFloat(val);   break;
                    case "blockwidth": blockWidth = Float.parseFloat(val);   break;
                    default: hud.logOutput("Unknown param: " + key); break;
                }
            } catch (NumberFormatException e) {
                hud.logOutput("Invalid value for " + key + ": " + val);
                return;
            }
        }

        final int fSeed = seed;
        final int fSize = size;
        final float fHeight = height, fThreshold = threshold, fBlockWidth = blockWidth;

        hud.logOutput("Generating map (seed=" + fSeed + " size=" + fSize
                + " height=" + fHeight + " threshold=" + fThreshold + ") ...");

        Thread t = new Thread(() -> {
            canvas.stopRenderer();
            try {
                world.clearObjects();

                MapGenerator gen = new MapGenerator();
                gen.setSeed(fSeed);
                gen.setGridSize(fSize);
                gen.setHeightScale(fHeight);
                gen.setThreshold(fThreshold);
                gen.setBlockWidth(fBlockWidth);
                gen.generate(world);

                world.setSeed(fSeed);
            } finally {
                canvas.startRenderer();
            }
            hud.logOutput("Map ready.");
        }, "genmap-thread");
        t.setDaemon(true);
        t.start();
    }

    private void deleteMap(CommandHud hud) {
        world.clearObjects();
        hud.logOutput("Map deleted.");
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (universe != null) {
            universe.cleanup();
        }
    }
}
