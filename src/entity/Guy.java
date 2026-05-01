package entity;

import objects.MeshObject;
import physics.AABB;
import terrain.MapGenerator;

import javax.vecmath.Vector3d;
import java.util.List;
import java.util.Random;

/**
 * A simple NPC that wanders randomly around the world.
 *
 * Guys are spawned in terrain chunks.  When their chunk is unloaded their
 * model is removed from the scene and updates are skipped, but their position
 * is preserved so they resume from the same spot when the chunk reloads.
 *
 * Updates are also skipped when the player is beyond {@code ACTIVE_RADIUS}
 * world units, capping the number of NPCs that need per-frame physics work.
 */
public class Guy extends Entity {

    private static final String MODEL_PATH       = "resources/models/Guy/guy.obj";
    private static final float  WALK_SPEED       = 2.5f;
    private static final double ACTIVE_RADIUS_SQ = 80.0 * 80.0;

    // Wander state
    private double wanderAngle;
    private double wanderTimer;
    private final Random rng;

    // Chunk lifecycle flags
    private boolean active;   // true when this guy's chunk is loaded in the scene
    private boolean inWorld;  // true once world.addEntity() has been called for this guy

    // Shared reference to player position for distance culling
    private Vector3d playerPos;

    public Guy(long seed, double x, double z, float groundY) {
        super();
        this.rng = new Random(seed);
        position.x = x;
        position.z = z;
        position.y = groundY + EntityPhysics.EYE_HEIGHT;
        wanderAngle = rng.nextDouble() * Math.PI * 2;
        wanderTimer = rng.nextDouble() * 3.0 + 1.0;

        setAutoRotate(true);

        // MeshObject caches parsed geometry, so after the first Guy is constructed,
        // subsequent guys reuse cached vertex data cheaply.
        MeshObject model = new MeshObject(MODEL_PATH, true);
        model.setCollidable(false);
        setModel(model);
    }

    // -----------------------------------------------------------------------
    // Chunk lifecycle
    // -----------------------------------------------------------------------

    /** Called by ChunkManager when this guy's chunk is loaded or unloaded. */
    public void setActive(boolean active) { this.active = active; }
    public boolean isActive()             { return active; }

    /** True once {@code world.addEntity(this)} has been called (first ever load). */
    public boolean isInWorld()   { return inWorld; }
    public void    markInWorld() { inWorld = true; }

    /** Shared reference to the player's live position vector (used for distance culling). */
    public void setPlayerPosition(Vector3d pos) { this.playerPos = pos; }

    // -----------------------------------------------------------------------
    // Entity update
    // -----------------------------------------------------------------------

    @Override
    public void update(double deltaTime, List<AABB> worldAABBs) {
        if (!active) return;

        // Skip update when player is far away
        if (playerPos != null) {
            double dx = position.x - playerPos.x;
            double dz = position.z - playerPos.z;
            if (dx * dx + dz * dz > ACTIVE_RADIUS_SQ) return;
        }

        // Wander: pick a new random direction periodically
        wanderTimer -= deltaTime;
        if (wanderTimer <= 0) {
            wanderAngle = rng.nextDouble() * Math.PI * 2;
            wanderTimer = rng.nextDouble() * 4.0 + 1.0;
        }

        // Horizontal movement: Look ahead to avoid water
        double nextX = position.x + Math.sin(wanderAngle) * WALK_SPEED * deltaTime;
        double nextZ = position.z + Math.cos(wanderAngle) * WALK_SPEED * deltaTime;

        boolean waterAhead = false;
        if (physics.getTerrainProvider() != null) {
            float h = physics.getTerrainProvider().getHeightAt((float) nextX, (float) nextZ);
            if (h < 0.1f || (physics.getTerrainProvider() instanceof MapGenerator && ((MapGenerator)physics.getTerrainProvider()).getRiverValAt((float) nextX, (float) nextZ) < 0.15f)) { // Water level is 0.0, so stay slightly above it
                waterAhead = true;
            }
        }

        if (waterAhead) {
            // Pick a new direction if we hit water
            wanderAngle = rng.nextDouble() * Math.PI * 2;
            wanderTimer = rng.nextDouble() * 4.0 + 1.0;

            // If we are currently IN water, try to move toward land
            if (physics.getTerrainProvider() != null) {
                float currentH = physics.getTerrainProvider().getHeightAt((float) position.x, (float) position.z);
                if (currentH < 0.1f || physics.getTerrainProvider() instanceof MapGenerator && ((MapGenerator)physics.getTerrainProvider()).getRiverValAt((float) position.x, (float) position.z) < 0.15f) {
                    // Sample a few directions to find land
                    for (int i = 0; i < 8; i++) {
                        double testAngle = i * (Math.PI * 2 / 8);
                        double tx = position.x + Math.sin(testAngle) * WALK_SPEED * deltaTime * 5;
                        double tz = position.z + Math.cos(testAngle) * WALK_SPEED * deltaTime * 5;
                        
                        float testH = physics.getTerrainProvider().getHeightAt((float) tx, (float) tz);
                        
                        if (testH > currentH && !(physics.getTerrainProvider() instanceof MapGenerator && ((MapGenerator)physics.getTerrainProvider()).getRiverValAt((float) tx, (float) tz) < 0.15f)) {
                            wanderAngle = testAngle;
                            // Move a bit this frame to start escaping
                            position.x += Math.sin(wanderAngle) * WALK_SPEED * deltaTime;
                            position.z += Math.cos(wanderAngle) * WALK_SPEED * deltaTime;
                            break;
                        }
                    }
                }
            }
        } else {
            position.x = nextX;
            position.z = nextZ;
        }
        applyAutoRotation(deltaTime);

        // Gravity + terrain clamping + AABB collision
        physics.update(deltaTime, position, false, worldAABBs, 0.0f);

        syncModelTransform();
    }
}
