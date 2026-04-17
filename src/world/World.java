package world;

import entity.Entity;
import entity.Player;
import objects.BaseObject;
import objects.MeshObject;
import particles.Particle;
import particles.ParticleRenderer;
import physics.AABB;
import physics.TerrainHeightProvider;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class World {
    private final List<BaseObject>  objects    = new ArrayList<>();
    private final List<Entity>      entities   = new ArrayList<>();
    private final List<BranchGroup> lightNodes = new ArrayList<>();
    private final ParticleRenderer  particleRenderer = new ParticleRenderer();
    private final Random            rng        = new Random();
    private double particleAccum = 0.0;
    private static final double PARTICLES_PER_SEC = 60.0;

    private final BranchGroup sceneBranchGroup;
    private boolean lightsAdded = false;
    private Color3f backgroundColor;
    private final Lighting lighting;
    private final Player player;
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
        for (BranchGroup lg : lightNodes) lg.detach();
        lightNodes.clear();
        player.setTerrainProvider(null);
        for (Entity e : entities) e.setTerrainProvider(null);
        // Keep the player model in the scene and tracked in the object list
        if (playerModel != null) objects.add(playerModel);
    }

    /**
     * Adds a PointLight (or any Light node) to the scene.
     * The light is wrapped in a detachable BranchGroup so it is removed on clearObjects().
     */
    public void addPointLight(PointLight light) {
        BranchGroup lg = new BranchGroup();
        lg.setCapability(BranchGroup.ALLOW_DETACH);
        lg.addChild(light);
        lightNodes.add(lg);
        sceneBranchGroup.addChild(lg);
    }

    /** Adds a raw BranchGroup to the scene, tracked so it is removed on clearObjects(). */
    public void addNode(BranchGroup node) {
        node.setCapability(BranchGroup.ALLOW_DETACH);
        lightNodes.add(node);
        sceneBranchGroup.addChild(node);
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
        MeshObject model = new MeshObject(modelPath, true);
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

        // Particles
        emitPlayerParticles(deltaTime);
        particleRenderer.update(deltaTime, player.getCamera().getYaw(), player.getCamera().getPitch());
    }

    private static final float EYE_HEIGHT = 1.7f;

    private void emitPlayerParticles(double dt) {
        particleAccum += PARTICLES_PER_SEC * dt;
        int toEmit = (int) particleAccum;
        particleAccum -= toEmit;

        javax.vecmath.Vector3d eye = player.getCamera().getPosition();
        for (int i = 0; i < toEmit; i++) {
            javax.vecmath.Vector3d pos = new javax.vecmath.Vector3d(
                    eye.x + (rng.nextDouble() - 0.5) * 0.3,
                    eye.y - EYE_HEIGHT + 0.1,
                    eye.z + (rng.nextDouble() - 0.5) * 0.3);
            javax.vecmath.Vector3d vel = new javax.vecmath.Vector3d(
                    (rng.nextDouble() - 0.5) * 1.2,
                    5.5 + rng.nextDouble() * 1.5,
                    (rng.nextDouble() - 0.5) * 1.2);
            Color4f start = new Color4f(1.0f, 1.0f, 1.0f, 1.0f);
            Color4f end   = new Color4f(1.0f, 1.0f, 1.0f, 0.0f);
            float size    = 0.4f + rng.nextFloat() * 0.2f;
            particleRenderer.emit(new Particle(pos, vel, start, end, size, 0f,
                    0.8f + rng.nextFloat() * 0.6f, 1.0f, 120f * (rng.nextFloat() - 0.5f), 0));
        }
    }

    // ------------------------------------------------------------------
    // Scene graph
    // ------------------------------------------------------------------

    public BranchGroup getSceneBranchGroup() {
        if (!lightsAdded) {
            lighting.addToScene(sceneBranchGroup);
            sceneBranchGroup.addChild(particleRenderer.getBranchGroup());
            lightsAdded = true;
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
