package world;

import objects.BaseObject;
import terrain.WaterHandlerLegacy;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.util.ArrayList;
import java.util.List;

public class World {
    private List<BaseObject> objects;
    private BranchGroup sceneBranchGroup;
    private Color3f backgroundColor;
    private Lighting lighting;
    private Camera camera;
    private WaterHandlerLegacy waterHandlerLegacy;
    private int seed = 0;

    public World() {
        this.objects = new ArrayList<>();
        this.sceneBranchGroup = new BranchGroup();
        this.sceneBranchGroup.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
        this.sceneBranchGroup.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
        this.backgroundColor = new Color3f(0.8f, 0.8f, 0.9f);
        this.camera = new Camera();
        this.lighting = new Lighting();
    }

    public Lighting getLighting() {return lighting;}

    /**
     * Add an object to the world
     */
    public void addObject(BaseObject object) {
        objects.add(object);
        sceneBranchGroup.addChild(object.getBranchGroup());
    }

    /**
     * Remove an object from the world and detach it from the scene graph.
     */
    public void removeObject(BaseObject object) {
        object.detachFromScene();
        objects.remove(object);
    }

    /**
     * Remove all objects from the world and clear the water handler.
     */
    public void clearObjects() {
        for (BaseObject obj : objects) {
            obj.detachFromScene();
        }
        objects.clear();
        waterHandlerLegacy = null;
    }

    /**
     * Advance all objects and camera by deltaTime seconds. Called each frame by WorldUpdateBehavior.
     */
    public void setWaterHandler(WaterHandlerLegacy wh) { this.waterHandlerLegacy = wh; }
    public int getSeed() { return seed; }
    public void setSeed(int seed) { this.seed = seed; }

    public void update(double deltaTime) {
        camera.update(deltaTime);
        for (BaseObject obj : new ArrayList<>(objects)) {
            obj.update(deltaTime);
        }
        if (waterHandlerLegacy != null) waterHandlerLegacy.update(deltaTime);
    }

    public Camera getCamera() {return camera;}

    /**
     * Get the scene graph for rendering
     */
    public BranchGroup getSceneBranchGroup() {
        // Add lights if not already added
        if (sceneBranchGroup.numChildren() == objects.size()) {
            lighting.addToScene(sceneBranchGroup);
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

    public int getTotalPolygonCount() {
        int total = 0;
        for (BaseObject obj : objects) total += obj.getPolygonCount();
        return total;
    }
}
