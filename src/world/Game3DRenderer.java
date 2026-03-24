package world;

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
            }
        });

        // Create the universe
        universe = new SimpleUniverse(canvas);

        // Setup the viewing platform
        viewingPlatform = universe.getViewingPlatform();
        viewTransformGroup = viewingPlatform.getViewPlatformTransform();

        // Set default camera position
        setCamera(new Vector3d(0.0, 0.0, world.getFocalDist()), 
                  new Point3d(0.0, 0.0, 0.0));

        // Setup the scene
        setupScene();
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
        Transform3D transform = new Transform3D();
        transform.lookAt(new Point3d(cameraPos), lookAt, new Vector3d(0, 1, 0));
        transform.invert();
        viewTransformGroup.setTransform(transform);
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

    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (universe != null) {
            universe.cleanup();
        }
    }
}
