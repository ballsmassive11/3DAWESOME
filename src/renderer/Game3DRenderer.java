package renderer;

import world.*;

import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.universe.SimpleUniverse;
import com.sun.j3d.utils.universe.ViewingPlatform;

import java.awt.*;
import java.awt.event.*;

/**
 * Renderer class for displaying 3D scenes using Java3D
 */
public class Game3DRenderer {
    private Canvas3D canvas;
    private SimpleUniverse universe;
    private World world;
    private ViewingPlatform viewingPlatform;
    private TransformGroup viewTransformGroup;
    private double fov = Math.PI / 3.0; // 60 degrees default

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
        canvas = new Canvas3D(config);
        canvas.setSize(800, 600);
        canvas.setFocusable(true);
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
                world.getCamera().keyPressed(e.getKeyCode());
            }

            @Override
            public void keyReleased(KeyEvent e) {
                world.getCamera().keyReleased(e.getKeyCode());
            }
        });

        // Create the universe
        universe = new SimpleUniverse(canvas);
        universe.getViewer().getView().setFieldOfView(fov);

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

    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (universe != null) {
            universe.cleanup();
        }
    }
}
