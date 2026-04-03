package world;

import objects.BaseObject;
import physics.AABB;
import physics.PlayerPhysics;
import physics.TerrainHeightProvider;
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
    private PlayerPhysics playerPhysics;
    private boolean hitboxesVisible = false;
    private int seed = 0;

    public World() {
        this.objects = new ArrayList<>();
        this.sceneBranchGroup = new BranchGroup();
        this.sceneBranchGroup.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
        this.sceneBranchGroup.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
        this.backgroundColor = new Color3f(0.8f, 0.8f, 0.9f);
        this.camera = new Camera();
        this.lighting = new Lighting();
        this.playerPhysics = new PlayerPhysics();
    }

    public Lighting getLighting() { return lighting; }

    public void addObject(BaseObject object) {
        objects.add(object);
        sceneBranchGroup.addChild(object.getBranchGroup());
        // Apply the current hitbox visibility to the newly added object
        object.setHitboxVisible(hitboxesVisible);
    }

    public void removeObject(BaseObject object) {
        object.detachFromScene();
        objects.remove(object);
    }

    public void clearObjects() {
        for (BaseObject obj : objects) obj.detachFromScene();
        objects.clear();
        waterHandlerLegacy = null;
        playerPhysics.setTerrainProvider(null);
    }

    public void setTerrainProvider(TerrainHeightProvider provider) {
        playerPhysics.setTerrainProvider(provider);
    }

    /** Toggle yellow wireframe hitboxes on all current and future objects. */
    public void setHitboxVisible(boolean visible) {
        hitboxesVisible = visible;
        for (BaseObject obj : objects) obj.setHitboxVisible(visible);
    }

    public boolean isHitboxVisible() { return hitboxesVisible; }

    public void setWaterHandler(WaterHandlerLegacy wh) { this.waterHandlerLegacy = wh; }
    public int  getSeed()              { return seed; }
    public void setSeed(int seed)      { this.seed = seed; }
    public PlayerPhysics getPlayerPhysics() { return playerPhysics; }

    public void update(double deltaTime) {
        camera.update(deltaTime);

        // Collect world-space AABBs of all collidable objects
        List<AABB> collidables = new ArrayList<>();
        for (BaseObject obj : objects) {
            AABB a = obj.getWorldAABB();
            if (a != null) collidables.add(a);
        }

        playerPhysics.update(deltaTime, camera.getPosition(),
                             camera.consumeJumpRequest(), collidables);

        for (BaseObject obj : new ArrayList<>(objects)) obj.update(deltaTime);
        if (waterHandlerLegacy != null) waterHandlerLegacy.update(deltaTime);
    }

    public Camera getCamera() { return camera; }

    public BranchGroup getSceneBranchGroup() {
        if (sceneBranchGroup.numChildren() == objects.size()) {
            lighting.addToScene(sceneBranchGroup);
        }
        return sceneBranchGroup;
    }

    public Color3f getBackgroundColor() { return backgroundColor; }

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
