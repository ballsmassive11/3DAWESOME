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

    // --- Auto-rotation (spring-damper toward targetYaw) ---
    // ζ = rotationDamping / (2√rotationStiffness) ≈ 1.0 → critically/over-damped (no oscillation).
    private double  rotationStiffness = 90.0;
    private double  rotationDamping   = 11.0;
    protected double targetYaw        = 0.0;
    private double  angularVelocity   = 0.0;
    /** When true, {@link #applyAutoRotation} derives targetYaw from horizontal movement. */
    private boolean autoRotate        = false;
    private double  prevX, prevZ; // last-frame position for movement-direction tracking

    private MeshObject model;

    public Entity() {
        this.position = new Vector3d(0, 15, 5);
        this.yaw      = 0;
        this.physics  = new EntityPhysics();
        this.prevX    = position.x;
        this.prevZ    = position.z;
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
        // Model sits at feet; position is eye level.
        // ObjectFile.RESIZE centres the model at its origin (spans [-0.5,0.5] * scale),
        // so lift by half the Y scale to align the model's bottom with the feet.
        double feetY = position.y - EntityPhysics.EYE_HEIGHT;
        model.setPosition(position.x, feetY + model.getScale().y * 0.5, position.z);
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

    // --- Auto-rotation ---

    /**
     * Advances {@code yaw} toward {@code targetYaw} using a spring-damper.
     * Call each frame while the entity should be rotating.
     */
    protected void stepRotation(double deltaTime) {
        double diff = shortestAngleDiff(targetYaw, yaw);
        angularVelocity += (diff * rotationStiffness - angularVelocity * rotationDamping) * deltaTime;
        yaw             += angularVelocity * deltaTime;
    }

    /**
     * Zeroes angular velocity so the model holds its current facing when not moving.
     */
    protected void stopRotation() {
        angularVelocity = 0.0;
    }

    /**
     * If {@code autoRotate} is enabled, derives {@code targetYaw} from the horizontal
     * movement since the last call and steps the spring-damper rotation.
     * If the entity didn't move this frame, rotation is frozen.
     * Call once per frame after applying horizontal movement.
     */
    protected void applyAutoRotation(double deltaTime) {
        double dx = position.x - prevX;
        double dz = position.z - prevZ;
        prevX = position.x;
        prevZ = position.z;
        if (!autoRotate) return;
        if (dx * dx + dz * dz < 1e-8) {
            stopRotation();
        } else {
            targetYaw = Math.atan2(-dx, -dz);
            stepRotation(deltaTime);
        }
    }

    public void    setAutoRotate(boolean autoRotate) { this.autoRotate = autoRotate; }
    public boolean isAutoRotate()                    { return autoRotate; }

    /** Shortest signed angle from {@code current} to {@code target}, in [-π, π]. */
    protected static double shortestAngleDiff(double target, double current) {
        double diff = (target - current) % (2 * Math.PI);
        if (diff >  Math.PI) diff -= 2 * Math.PI;
        if (diff < -Math.PI) diff += 2 * Math.PI;
        return diff;
    }

    public void setRotationStiffness(double stiffness) { this.rotationStiffness = stiffness; }
    public void setRotationDamping(double damping)     { this.rotationDamping   = damping;   }

    // --- Accessors ---

    public Vector3d getPosition() { return position; }
    public double   getYaw()      { return yaw; }
}
