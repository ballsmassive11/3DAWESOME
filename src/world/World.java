package world;

import entity.Entity;
import entity.Player;
import objects.BaseObject;
import objects.MeshObject;
import particles.Particle;
import particles.ParticleEmitter;
import particles.ParticleRenderer;
import physics.AABB;
import physics.TerrainHeightProvider;
import water.WaterTile;

import javax.media.j3d.*;
import javax.vecmath.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class World {
    private final List<BaseObject>      objects    = new CopyOnWriteArrayList<>();
    private final List<Entity>          entities   = new CopyOnWriteArrayList<>();
    private final List<BranchGroup>     lightNodes = new CopyOnWriteArrayList<>();
    private final List<ParticleEmitter> emitters   = new CopyOnWriteArrayList<>();
    private final ParticleRenderer      particleRenderer = new ParticleRenderer();
    private final Random            rng        = new Random();
    private double particleAccum = 0.0;
    private static final double PARTICLES_PER_SEC = 10.0;

    private final BranchGroup sceneBranchGroup;
    private final OrderedGroup rootOrderedGroup;
    private boolean lightsAdded = false;
    private Color3f backgroundColor;
    private final Lighting lighting;
    private final Player player;
    private boolean hitboxesVisible = false;
    private int seed = 0;
    private boolean physicsEnabled = true;

    public World() {
        this.sceneBranchGroup = new BranchGroup();
        this.sceneBranchGroup.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
        this.sceneBranchGroup.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);

        this.rootOrderedGroup = new OrderedGroup();
        this.rootOrderedGroup.setCapability(Group.ALLOW_CHILDREN_EXTEND);
        this.rootOrderedGroup.setCapability(Group.ALLOW_CHILDREN_WRITE);
        this.sceneBranchGroup.addChild(rootOrderedGroup);

        this.backgroundColor = new Color3f(0.8f, 0.8f, 0.9f);
        this.player  = new Player();
        this.lighting = new Lighting();
    }

    // ------------------------------------------------------------------
    // Objects (static scene geometry)
    // ------------------------------------------------------------------

    public void addObject(BaseObject object) {
        objects.add(object);
        if (object instanceof WaterTile) {
            rootOrderedGroup.insertChild(object.getBranchGroup(), 0);
        } else {
            rootOrderedGroup.addChild(object.getBranchGroup());
        }
        object.setHitboxVisible(hitboxesVisible);
    }

    public void removeObject(BaseObject object) {
        object.detachFromScene();
        objects.remove(object);
    }

    public void clearObjects() {
        MeshObject playerModel = player.getModel();
        // Use a temporary list to avoid issues if needed, but CopyOnWriteArrayList is safe here
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
        rootOrderedGroup.addChild(lg);
    }

    /** Adds a raw BranchGroup to the scene, tracked so it is removed on clearObjects(). */
    public void addNode(BranchGroup node) {
        node.setCapability(BranchGroup.ALLOW_DETACH);
        lightNodes.add(node);
        rootOrderedGroup.addChild(node);
    }

    // ------------------------------------------------------------------
    // Particle emitters
    // ------------------------------------------------------------------

    /** Registers a {@link ParticleEmitter} to be updated every frame. */
    public void addEmitter(ParticleEmitter emitter) {
        emitters.add(emitter);
    }

    /** Removes a previously registered emitter. */
    public void removeEmitter(ParticleEmitter emitter) {
        emitters.remove(emitter);
    }

    public List<ParticleEmitter> getEmitters() {
        return emitters;
    }

    public List<BaseObject> getObjects() {
        return objects;
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
        return entities;
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

    public void setPhysicsEnabled(boolean enabled) {
        this.physicsEnabled = enabled;
    }

    public boolean isPhysicsEnabled() {
        return physicsEnabled;
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    public void update(double deltaTime) {
        // Update all static objects (animations like rotation)
        for (BaseObject obj : objects) {
            obj.update(deltaTime);
        }

        if (!physicsEnabled) return;

        // Collect collidable AABBs from static objects
        List<AABB> collidables = new ArrayList<>();
        for (BaseObject obj : objects) {
            AABB a = obj.getWorldAABB();
            if (a != null) collidables.add(a);
        }

        // Update player (input → movement → physics → model sync)
        player.update(deltaTime, collidables);

        // Update all other entities
        for (Entity e : entities) {
            e.update(deltaTime, collidables);
        }

        // Particle emitters
        for (ParticleEmitter emitter : emitters) {
            emitter.update(deltaTime, particleRenderer);
        }

        // Particles
        Vector3d camPos = player.getCamera().getPosition();
        particleRenderer.update(deltaTime, player.getCamera().getYaw(), player.getCamera().getPitch(),
                camPos.x, camPos.y, camPos.z);
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
                    (rng.nextDouble() - 0.5) * 2.2,
                    5.5 + rng.nextDouble() * 1.5,
                    (rng.nextDouble() - 0.5) * 2.2);
            Color4f start = new Color4f(1.0f, 1.0f, 1.0f, 1.0f);
            Color4f end   = new Color4f(1.0f, 1.0f, 1.0f, 0.0f);
            float size    = 1.4f + rng.nextFloat() * 0.2f;

            String particleName = "resources/particles/joeyParticle.png";
            if (rng.nextFloat() < 0.5f) particleName = "resources/particles/happyhappyhappy.png";

            particleRenderer.emit(new Particle(pos, vel, start, end, size, 0f,
                    0.8f + rng.nextFloat() * 0.6f, 1.0f, 120f * (rng.nextFloat() - 0.5f),
                    0, particleName));
        }
    }

    // ------------------------------------------------------------------
    // Scene graph
    // ------------------------------------------------------------------

    public BranchGroup getSceneBranchGroup() {
        if (!lightsAdded) {
            lighting.addToScene(sceneBranchGroup);
            // Add particles to rootOrderedGroup so they render after world objects
            rootOrderedGroup.addChild(particleRenderer.getBranchGroup());
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
