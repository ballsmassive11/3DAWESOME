package entity;

import objects.MeshObject;
import physics.AABB;
import physics.TerrainHeightProvider;

import javax.vecmath.Vector3d;
import java.util.List;

/**
 * Abstract base class for anything that exists physically in the world and
 * can move — players, NPCs, creatures, etc.
 *
 * Each entity has:
 *   - a position (eye level) and a yaw (horizontal facing direction)
 *   - an EntityPhysics component that handles gravity, jumping, and collisions
 *   - an optional MeshObject as its visible body
 *
 * Subclasses implement {@link #update} to drive their own movement logic
 * (keyboard input for Player, AI for NPCs, etc.) and then call the physics
 * component to apply gravity and resolve collisions.
 */
public abstract class Entity {

    protected Vector3d position;
    protected double   yaw;
    protected EntityPhysics physics;

    private MeshObject model;

    public Entity() {
        this.position = new Vector3d(0, 15, 5);
        this.yaw      = 0;
        this.physics  = new EntityPhysics();
    }

    /**
     * Advance this entity by one frame.
     *
     * Implementations should:
     *   1. Determine movement intent (input / AI)
     *   2. Apply horizontal movement to {@code position}
     *   3. Call {@code physics.update(...)} to handle gravity and collision
     *   4. Call {@code syncModelTransform()} to move the visible body
     *
     * @param deltaTime  Seconds since last frame.
     * @param worldAABBs World-space AABBs of all collidable objects this frame.
     */
    public abstract void update(double deltaTime, List<AABB> worldAABBs);

    /**
     * Moves the entity's mesh model to match its current position and yaw.
     * No-op if no model is attached.
     */
    protected void syncModelTransform() {
        if (model == null) return;
        // Model sits at feet; position is eye level
        model.setPosition(position.x, position.y - EntityPhysics.EYE_HEIGHT, position.z);
        // +PI because models face +Z at rest but camera/movement faces -Z at yaw=0
        model.setRotationEuler(0, yaw + Math.PI, 0);
    }

    // --- Model ---

    public void setModel(MeshObject model) {
        this.model = model;
    }

    public MeshObject getModel() {
        return model;
    }

    // --- Physics / terrain ---

    public EntityPhysics getPhysics() {
        return physics;
    }

    public void setTerrainProvider(TerrainHeightProvider provider) {
        physics.setTerrainProvider(provider);
    }

    // --- Accessors ---

    public Vector3d getPosition() { return position; }
    public double   getYaw()      { return yaw; }
}
