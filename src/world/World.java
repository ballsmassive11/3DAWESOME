package world;

import entity.Entity;
import entity.Player;
import objects.BaseObject;
import objects.MeshObject;
import physics.AABB;
import physics.TerrainHeightProvider;
import terrain.WaterHandlerLegacy;

import javax.media.j3d.*;
import javax.vecmath.*;
import java.util.ArrayList;
import java.util.List;

public class World {
    private final List<BaseObject> objects  = new ArrayList<>();
    private final List<Entity>     entities = new ArrayList<>();

    private final BranchGroup sceneBranchGroup;
    private Color3f backgroundColor;
    private final Lighting lighting;
    private final Player player;
    private WaterHandlerLegacy waterHandlerLegacy;
    private boolean hitboxesVisible = false;
    private int seed = 0;

    public World() {
        this.sceneBranchGroup = new BranchGroup();
        this.sceneBranchGroup.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
        this.sceneBranchGroup.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
        this.backgroundColor = new Color3f(0.8f, 0.8f, 0.9f);
        this.player  = new Player();
        this.lighting = new Lighting();
    }

    // ------------------------------------------------------------------
    // Objects (static scene geometry)
    // ------------------------------------------------------------------

    public void addObject(BaseObject object) {
        objects.add(object);
        sceneBranchGroup.addChild(object.getBranchGroup());
        object.setHitboxVisible(hitboxesVisible);
    }

    public void removeObject(BaseObject object) {
        object.detachFromScene();
        objects.remove(object);
    }

    public void clearObjects() {
        MeshObject playerModel = player.getModel();
        for (BaseObject obj : objects) {
            if (obj != playerModel) obj.detachFromScene();
        }
        objects.clear();
        waterHandlerLegacy = null;
        player.setTerrainProvider(null);
        for (Entity e : entities) e.setTerrainProvider(null);
        // Keep the player model in the scene and tracked in the object list
        if (playerModel != null) objects.add(playerModel);
    }

    public List<BaseObject> getObjects() {
        return new ArrayList<>(objects);
    }

    // ------------------------------------------------------------------
    // Entities (moving actors)
    // ------------------------------------------------------------------

    /**
     * Adds an entity to the world.  If the entity has a model, it is also
     * added to the scene as a renderable object.
     */
    public void addEntity(Entity entity) {
        entities.add(entity);
        if (entity.getModel() != null) {
            addObject(entity.getModel());
        }
        entity.setTerrainProvider(player.getPhysics().getTerrainProvider());
    }

    public List<Entity> getEntities() {
        return new ArrayList<>(entities);
    }

    // ------------------------------------------------------------------
    // Player
    // ------------------------------------------------------------------

    public Player  getPlayer() { return player; }
    public Camera  getCamera() { return player.getCamera(); }

    /**
     * Loads an OBJ model and attaches it as the player's visible body.
     * The model is non-collidable and added to the scene graph.
     */
    public void setPlayerModel(String modelPath) {
        MeshObject model = new MeshObject(modelPath);
        model.setCollidable(false);
        player.setModel(model);
        addObject(model);
    }

    // ------------------------------------------------------------------
    // Terrain
    // ------------------------------------------------------------------

    public void setTerrainProvider(TerrainHeightProvider provider) {
        player.setTerrainProvider(provider);
        for (Entity e : entities) e.setTerrainProvider(provider);
    }

    public TerrainHeightProvider getTerrainProvider() {
        return player.getPhysics().getTerrainProvider();
    }

    // ------------------------------------------------------------------
    // Hitboxes
    // ------------------------------------------------------------------

    public void setHitboxVisible(boolean visible) {
        hitboxesVisible = visible;
        for (BaseObject obj : objects) obj.setHitboxVisible(visible);
    }

    public boolean isHitboxVisible() { return hitboxesVisible; }

    // ------------------------------------------------------------------
    // Misc
    // ------------------------------------------------------------------

    public void setWaterHandler(WaterHandlerLegacy wh) { this.waterHandlerLegacy = wh; }
    public int  getSeed()          { return seed; }
    public void setSeed(int seed)  { this.seed = seed; }
    public Lighting getLighting()  { return lighting; }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    public void update(double deltaTime) {
        // Collect collidable AABBs from static objects
        List<AABB> collidables = new ArrayList<>();
        for (BaseObject obj : objects) {
            AABB a = obj.getWorldAABB();
            if (a != null) collidables.add(a);
        }

        // Update player (input → movement → physics → model sync)
        player.update(deltaTime, collidables);

        // Update all other entities
        for (Entity e : new ArrayList<>(entities)) {
            e.update(deltaTime, collidables);
        }

        // Animate static objects
        for (BaseObject obj : new ArrayList<>(objects)) obj.update(deltaTime);

        if (waterHandlerLegacy != null) waterHandlerLegacy.update(deltaTime);
    }

    // ------------------------------------------------------------------
    // Scene graph
    // ------------------------------------------------------------------

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

    public int getTotalPolygonCount() {
        int total = 0;
        for (BaseObject obj : objects) total += obj.getPolygonCount();
        return total;
    }
}
