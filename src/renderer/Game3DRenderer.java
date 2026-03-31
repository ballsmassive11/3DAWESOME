package renderer;

import objects.BaseObject;
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
            List<BaseObject> objects = world.getObjects();
            for (BaseObject obj : objects) {
                obj.setAngularVelocity((float)(Math.random())-0.5*20, (float)(Math.random())-0.5*20, (float)(Math.random())-0.5*20);
            }
            hud.logOutput("say less");

        } else if (cmd.equals("cmds") || cmd.equals("help")) {
            hud.logOutput("fov <degrees>    - Set field of view (10-170)");
            hud.logOutput("rdist <distance> - Set render distance");
            hud.logOutput("fun              - would recommend turning render distance down");
            hud.logOutput("cmds / help      - Show this message");
        } else {
            hud.logOutput("Unknown command: " + cmd + "  (type 'cmds / help' for list)");
        }
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
