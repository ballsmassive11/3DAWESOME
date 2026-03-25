package world;

import objects.BaseObject;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.util.ArrayList;
import java.util.List;

public class World {
    private List<BaseObject> objects;
    private BranchGroup sceneBranchGroup;
    private Color3f backgroundColor;
    private AmbientLight ambientLight;
    private DirectionalLight directionalLight;
    private Camera camera;

    public World() {
        this.objects = new ArrayList<>();
        this.sceneBranchGroup = new BranchGroup();
        this.sceneBranchGroup.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
        this.sceneBranchGroup.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
        this.backgroundColor = new Color3f(0.2f, 0.2f, 0.3f);
        this.camera = new Camera();

        setupLighting();
    }

    /**
     * Setup default lighting for the scene
     */
    private void setupLighting() {
        // Ambient light
        ambientLight = new AmbientLight(new Color3f(0.3f, 0.3f, 0.3f));
        ambientLight.setInfluencingBounds(new BoundingSphere(new Point3d(0, 0, 0), 100.0));

        // Directional light
        directionalLight = new DirectionalLight(
            new Color3f(1.0f, 1.0f, 1.0f),
            new Vector3f(-1.0f, -1.0f, -1.0f)
        );
        directionalLight.setInfluencingBounds(new BoundingSphere(new Point3d(0, 0, 0), 100.0));
    }

    /**
     * Add an object to the world
     */
    public void addObject(BaseObject object) {
        objects.add(object);
        sceneBranchGroup.addChild(object.getBranchGroup());
    }

    /**
     * Remove an object from the world
     */
    public void removeObject(BaseObject object) {
        objects.remove(object);
    }

    /**
     * Advance all objects and camera by deltaTime seconds. Called each frame by WorldUpdateBehavior.
     */
    public void update(double deltaTime) {
        camera.update(deltaTime);
        for (BaseObject obj : objects) {
            obj.update(deltaTime);
        }
    }

    public Camera getCamera() {
        return camera;
    }

    /**
     * Get the scene graph for rendering
     */
    public BranchGroup getSceneBranchGroup() {
        // Add lights if not already added
        if (sceneBranchGroup.numChildren() == objects.size()) {
            sceneBranchGroup.addChild(ambientLight);
            sceneBranchGroup.addChild(directionalLight);
        }
        return sceneBranchGroup;
    }

    public Color3f getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color3f backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public List<BaseObject> getObjects() {
        return new ArrayList<>(objects);
    }
}
