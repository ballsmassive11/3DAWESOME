package physics;

import javax.vecmath.Vector3d;
import java.util.List;

/**
 * Vertical physics + AABB collision for the player.
 *
 * Gravity pulls the player down each frame. The player is clamped to terrain
 * (via TerrainHeightProvider) and resolved against all object AABBs.
 *
 * Slope limiting: while grounded, horizontal moves that would climb steeper
 * than MAX_SLOPE are cancelled.
 *
 * Object collision: uses the minimum-separation-axis approach.
 *   - Horizontal MSA → wall push-out
 *   - Vertical MSA   → land on top or ceiling bump
 *   - Step-up        → object top within STEP_HEIGHT of player feet → auto-step
 *
 * The player shape is a true pillbox (capsule): a vertical cylinder of radius
 * PLAYER_RADIUS capped with hemispheres, total height EYE_HEIGHT.
 */
public class PlayerPhysics {

    public static final float EYE_HEIGHT   = 1.7f;
    public static final float PLAYER_RADIUS = 0.35f;
    public static final float JUMP_SPEED   = 9.0f;
    public static final float FLY_SPEED    = 8.0f;

    public static final Pillbox PLAYER_SHAPE = new Pillbox(PLAYER_RADIUS, EYE_HEIGHT);

    private static final float GRAVITY     = -22.0f;
    private static final float MAX_SLOPE   = (float) Math.tan(Math.toRadians(40.0));
    private static final float STEP_HEIGHT = 0.7f;

    private float   velocityY = 0f;
    private boolean onGround  = false;
    private boolean flying    = false;

    private float prevX = Float.NaN;
    private float prevZ = Float.NaN;

    private TerrainHeightProvider terrainProvider;

    public void setTerrainProvider(TerrainHeightProvider provider) {
        this.terrainProvider = provider;
        velocityY = 0f;
        onGround  = false;
        prevX     = Float.NaN;
        prevZ     = Float.NaN;
    }

    public TerrainHeightProvider getTerrainProvider() {
        return terrainProvider;
    }

    /**
     * Advance physics by deltaTime. May modify all three components of position.
     *
     * @param deltaTime     Seconds since last frame.
     * @param position      Player eye position (modified in place).
     * @param jumpRequested True if the player pressed jump this frame.
     * @param objectAABBs   World-space AABBs of all collidable objects this frame.
     * @param verticalInput +1 = ascend, -1 = descend, 0 = neutral (used when flying).
     */
    public void update(double deltaTime, Vector3d position, boolean jumpRequested,
                       List<AABB> objectAABBs, float verticalInput) {
        float curX = (float) position.x;
        float curZ = (float) position.z;

        if (flying) {
            // --- Fly mode: no gravity, no terrain clamping ---
            position.y += FLY_SPEED * verticalInput * deltaTime;
            velocityY   = 0f;
            onGround    = false;
            prevX = curX;
            prevZ = curZ;
            resolveObjectCollisions(position, objectAABBs);
            return;
        }

        // --- Slope check (grounded only) ---
        if (onGround && !Float.isNaN(prevX)) {
            float dh = (float) Math.sqrt((curX - prevX) * (curX - prevX)
                                       + (curZ - prevZ) * (curZ - prevZ));
            if (dh > 0.001f) {
                float rise = queryGroundY(curX, curZ) - queryGroundY(prevX, prevZ);
                if (rise / dh > MAX_SLOPE) {
                    position.x = prevX;
                    position.z = prevZ;
                    curX = prevX;
                    curZ = prevZ;
                }
            }
        }
        prevX = curX;
        prevZ = curZ;

        // --- Vertical physics ---
        float terrainY  = queryGroundY(curX, curZ);
        float eyeFloor  = terrainY + EYE_HEIGHT;   // lowest Y allowed by terrain

        if (jumpRequested && onGround) {
            velocityY = JUMP_SPEED;
            onGround  = false;
        }

        velocityY      += (float) (GRAVITY * deltaTime);
        position.y     += velocityY * deltaTime;

        // Terrain ground clamp
        if (position.y <= eyeFloor) {
            position.y = eyeFloor;
            velocityY  = 0f;
            onGround   = true;
        } else {
            onGround = false;
        }

        // --- Object AABB collision ---
        resolveObjectCollisions(position, objectAABBs);
    }

    // ------------------------------------------------------------------

    private void resolveObjectCollisions(Vector3d position, List<AABB> objects) {
        float cx = (float) position.x;
        float cz = (float) position.z;

        for (AABB obj : objects) {
            float eyeY    = (float) position.y;
            float feetY   = eyeY - EYE_HEIGHT;
            float spineBot = PLAYER_SHAPE.spineBot(feetY);
            float spineTop = PLAYER_SHAPE.spineTop(feetY);

            // --- Circle XZ test: closest point on AABB to the capsule axis ---
            float closestX = clamp(cx, obj.minX, obj.maxX);
            float closestZ = clamp(cz, obj.minZ, obj.maxZ);
            float dx = cx - closestX;
            float dz = cz - closestZ;
            float dxzSq = dx * dx + dz * dz;
            if (dxzSq >= PLAYER_RADIUS * PLAYER_RADIUS) continue; // XZ circle miss

            // Full capsule Y extent quick-reject
            if (feetY >= obj.maxY || eyeY <= obj.minY) continue;

            // Step-up: object top is within step height of player feet
            float objTop = obj.maxY;
            if (objTop > feetY && objTop - feetY <= STEP_HEIGHT) {
                position.y = objTop + EYE_HEIGHT;
                if (velocityY < 0f) { velocityY = 0f; onGround = true; }
                continue;
            }

            // --- Closest points between capsule spine and AABB in Y ---
            // After the step-up check, objTop > feetY + STEP_HEIGHT.
            float dy;
            if (obj.minY >= spineTop) {
                // AABB is entirely above the spine → top hemisphere contact
                dy = spineTop - obj.minY; // negative: spine is below AABB
            } else if (obj.maxY <= spineBot) {
                // AABB is entirely below the spine → bottom hemisphere contact
                // (only reachable if STEP_HEIGHT < PLAYER_RADIUS, kept for correctness)
                dy = spineBot - obj.maxY; // positive: spine is above AABB
            } else {
                // Spine and AABB Y ranges overlap → cylindrical body contact
                dy = 0f;
            }

            // Full distance from spine point to AABB closest point
            float distSq = dxzSq + dy * dy;
            if (distSq >= PLAYER_RADIUS * PLAYER_RADIUS) continue;

            float dist = (float) Math.sqrt(distSq);
            if (dist < 1e-5f) { position.x += PLAYER_RADIUS; cx = (float) position.x; continue; }

            float penetration = PLAYER_RADIUS - dist;

            if (dy == 0f) {
                // Cylindrical contact: push the player out horizontally only.
                // This lets the player slide smoothly around AABB corners.
                float dxzLen = (float) Math.sqrt(dxzSq);
                if (dxzLen < 1e-5f) {
                    position.x += penetration;
                } else {
                    position.x += dx / dxzLen * penetration;
                    position.z += dz / dxzLen * penetration;
                }
            } else {
                // Hemisphere contact: push in 3D along the spine→AABB vector.
                float invDist = 1f / dist;
                position.x += dx * invDist * penetration;
                position.z += dz * invDist * penetration;
                float pushY = dy * invDist * penetration;
                position.y += pushY;
                if (dy > 0f) {
                    // Bottom hemisphere hit object above → land on top
                    if (velocityY < 0f) { velocityY = 0f; onGround = true; }
                } else {
                    // Top hemisphere hit object below → ceiling bump
                    if (velocityY > 0f) velocityY = 0f;
                }
            }

            cx = (float) position.x;
            cz = (float) position.z;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private float queryGroundY(float x, float z) {
        return terrainProvider != null ? terrainProvider.getHeightAt(x, z) : 0f;
    }

    public boolean isOnGround()   { return onGround; }
    public float   getVelocityY() { return velocityY; }
    public boolean isFlying()     { return flying; }

    public void setFlying(boolean flying) {
        this.flying  = flying;
        this.velocityY = 0f;
        this.onGround  = false;
    }
}
