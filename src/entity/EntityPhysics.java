package entity;

import physics.AABB;
import physics.Pillbox;
import physics.TerrainHeightProvider;

import javax.vecmath.Vector3d;
import java.util.List;

/**
 * Physics simulation for a walking entity.
 *
 * Handles gravity, jumping, slope limiting, step-up, and capsule vs AABB
 * collision for any entity that moves through the world.  One instance lives
 * per entity; each holds its own velocity and grounded state.
 *
 * The entity shape is a pillbox (vertical capsule): a cylinder of radius
 * ENTITY_RADIUS capped with hemispheres, total height EYE_HEIGHT.
 */
public class EntityPhysics {

    public static final float EYE_HEIGHT    = 1.7f;
    public static final float ENTITY_RADIUS = 0.35f;
    private float jumpSpeed = 9.0f;
    private float flySpeed  = 8.0f;

    public static final Pillbox ENTITY_SHAPE = new Pillbox(ENTITY_RADIUS, EYE_HEIGHT);

    private static final float GRAVITY     = -22.0f;
    private static final float MAX_SLOPE   = (float) Math.tan(Math.toRadians(60.0));
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
     * Advance physics by deltaTime. Modifies all three components of position.
     *
     * @param deltaTime     Seconds since last frame.
     * @param position      Entity eye position (modified in place).
     * @param jumpRequested True if the entity wants to jump this frame.
     * @param objectAABBs   World-space AABBs of all collidable objects.
     * @param verticalInput +1 = ascend, -1 = descend, 0 = neutral (fly mode only).
     */
    public void update(double deltaTime, Vector3d position, boolean jumpRequested,
                       List<AABB> objectAABBs, float verticalInput) {
        float curX = (float) position.x;
        float curZ = (float) position.z;

        if (flying) {
            position.y += flySpeed * verticalInput * deltaTime;
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
        float terrainY = queryGroundY(curX, curZ);
        float eyeFloor = terrainY + EYE_HEIGHT;

        if (jumpRequested && onGround) {
            velocityY = jumpSpeed;
            onGround  = false;
        }

        velocityY  += (float) (GRAVITY * deltaTime);
        position.y += velocityY * deltaTime;

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
            float spineBot = ENTITY_SHAPE.spineBot(feetY);
            float spineTop = ENTITY_SHAPE.spineTop(feetY);

            float closestX = clamp(cx, obj.minX, obj.maxX);
            float closestZ = clamp(cz, obj.minZ, obj.maxZ);
            float dx = cx - closestX;
            float dz = cz - closestZ;
            float dxzSq = dx * dx + dz * dz;
            if (dxzSq >= ENTITY_RADIUS * ENTITY_RADIUS) continue;

            if (feetY >= obj.maxY || eyeY <= obj.minY) continue;

            float objTop = obj.maxY;
            if (objTop > feetY && objTop - feetY <= STEP_HEIGHT) {
                position.y = objTop + EYE_HEIGHT;
                if (velocityY < 0f) { velocityY = 0f; onGround = true; }
                continue;
            }

            float dy;
            if (obj.minY >= spineTop) {
                dy = spineTop - obj.minY;
            } else if (obj.maxY <= spineBot) {
                dy = spineBot - obj.maxY;
            } else {
                dy = 0f;
            }

            float distSq = dxzSq + dy * dy;
            if (distSq >= ENTITY_RADIUS * ENTITY_RADIUS) continue;

            float dist = (float) Math.sqrt(distSq);
            if (dist < 1e-5f) { position.x += ENTITY_RADIUS; cx = (float) position.x; continue; }

            float penetration = ENTITY_RADIUS - dist;

            if (dy == 0f) {
                float dxzLen = (float) Math.sqrt(dxzSq);
                if (dxzLen < 1e-5f) {
                    position.x += penetration;
                } else {
                    position.x += dx / dxzLen * penetration;
                    position.z += dz / dxzLen * penetration;
                }
            } else {
                float invDist = 1f / dist;
                position.x += dx * invDist * penetration;
                position.z += dz * invDist * penetration;
                float pushY = dy * invDist * penetration;
                position.y += pushY;
                if (dy > 0f) {
                    if (velocityY < 0f) { velocityY = 0f; onGround = true; }
                } else {
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
        this.flying    = flying;
        this.velocityY = 0f;
        this.onGround  = false;
    }

    public float getJumpSpeed() { return jumpSpeed; }
    public void setJumpSpeed(float jumpSpeed) { this.jumpSpeed = jumpSpeed; }

    public float getFlySpeed() { return flySpeed; }
    public void setFlySpeed(float flySpeed) { this.flySpeed = flySpeed; }
}
