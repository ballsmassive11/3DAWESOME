package physics;

import javax.vecmath.Vector3d;
import java.util.List;

/**
 * Vertical physics + AABB collision for the player.
 *
 * Slope behaviour:
 *   - If the player tries to walk UP a slope steeper than MAX_SLOPE, the
 *     horizontal move is cancelled (old behaviour, prevents the jump-exploit).
 *   - If the player is STANDING on a slope steeper than MAX_SLOPE, they enter
 *     a SLIDING state: they are pushed downhill at a speed proportional to the
 *     gradient, and onGround is false — so they cannot jump off the slope.
 *
 * Object collision: minimum-separation-axis push-out with step-up support.
 */
public class PlayerPhysics {

    public static final float EYE_HEIGHT    = 1.7f;
    public static final float PLAYER_RADIUS = 0.35f;
    public static final float JUMP_SPEED    = 9.0f;

    private static final float GRAVITY              = -22.0f;
    private static final float MAX_SLOPE            = (float) Math.tan(Math.toRadians(40.0));
    private static final float STEP_HEIGHT          = 0.7f;

    /** Step size used when sampling the terrain gradient for sliding. */
    private static final float GRADIENT_DELTA       = 0.5f;
    /** Slide speed (units/s) per unit of gradient magnitude (tan of slope angle). */
    private static final float SLIDE_SPEED_FACTOR   = 10.0f;
    /** Hard cap on slide speed so very steep terrain doesn't fling the player. */
    private static final float MAX_SLIDE_SPEED      = 20.0f;

    private float   velocityY = 0f;
    private boolean onGround  = false;
    private boolean sliding   = false;

    private float prevX = Float.NaN;
    private float prevZ = Float.NaN;

    private TerrainHeightProvider terrainProvider;

    public void setTerrainProvider(TerrainHeightProvider provider) {
        this.terrainProvider = provider;
        velocityY = 0f;
        onGround  = false;
        sliding   = false;
        prevX     = Float.NaN;
        prevZ     = Float.NaN;
    }

    /**
     * Advance physics by deltaTime. May modify all three components of position.
     *
     * @param deltaTime     Seconds since last frame.
     * @param position      Player eye position (modified in place).
     * @param jumpRequested True if the player pressed jump this frame.
     * @param objectAABBs   World-space AABBs of all collidable objects this frame.
     */
    public void update(double deltaTime, Vector3d position, boolean jumpRequested,
                       List<AABB> objectAABBs) {
        float curX = (float) position.x;
        float curZ = (float) position.z;

        // --- Uphill movement cancellation (prevents walking/jumping up steep slopes) ---
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

        // --- Sample terrain gradient at current position ---
        float h0  = queryGroundY(curX, curZ);
        float hpx = queryGroundY(curX + GRADIENT_DELTA, curZ);
        float hpz = queryGroundY(curX, curZ + GRADIENT_DELTA);
        float gradX = (hpx - h0) / GRADIENT_DELTA;   // dY/dX
        float gradZ = (hpz - h0) / GRADIENT_DELTA;   // dY/dZ
        float gradMag = (float) Math.sqrt(gradX * gradX + gradZ * gradZ);

        boolean onSteepTerrain = gradMag > MAX_SLOPE;

        // --- Vertical physics ---
        float eyeFloor = h0 + EYE_HEIGHT;

        // Jump only when truly grounded (not sliding)
        if (jumpRequested && onGround) {
            velocityY = JUMP_SPEED;
            onGround  = false;
            sliding   = false;
        }

        velocityY  += (float) (GRAVITY * deltaTime);
        position.y += velocityY * deltaTime;

        // Terrain ground clamp
        if (position.y <= eyeFloor) {
            position.y = eyeFloor;
            velocityY  = 0f;

            if (onSteepTerrain) {
                // Sliding state: player is on the slope surface but cannot jump
                sliding  = true;
                onGround = false;

                // Push player downhill (opposite of gradient direction)
                float speed = Math.min(gradMag * SLIDE_SPEED_FACTOR, MAX_SLIDE_SPEED);
                position.x -= (gradX / gradMag) * speed * deltaTime;
                position.z -= (gradZ / gradMag) * speed * deltaTime;

                // Update prevX/prevZ so the uphill-cancellation check next frame
                // doesn't fight the slide direction.
                prevX = (float) position.x;
                prevZ = (float) position.z;
            } else {
                // Flat enough: normal grounded state
                sliding  = false;
                onGround = true;
            }
        } else {
            onGround = false;
            sliding  = false;
        }

        // --- Object AABB collision ---
        resolveObjectCollisions(position, objectAABBs);
    }

    // ------------------------------------------------------------------

    private void resolveObjectCollisions(Vector3d position, List<AABB> objects) {
        float px = (float) position.x;
        float pz = (float) position.z;

        for (AABB obj : objects) {
            float eyeY  = (float) position.y;
            float feetY = eyeY - EYE_HEIGHT;

            if (px + PLAYER_RADIUS <= obj.minX || px - PLAYER_RADIUS >= obj.maxX) continue;
            if (pz + PLAYER_RADIUS <= obj.minZ || pz - PLAYER_RADIUS >= obj.maxZ) continue;
            if (feetY >= obj.maxY || eyeY <= obj.minY) continue;

            // Step-up
            float objTop = obj.maxY;
            if (objTop > feetY && objTop - feetY <= STEP_HEIGHT) {
                position.y = objTop + EYE_HEIGHT;
                if (velocityY < 0f) { velocityY = 0f; onGround = true; sliding = false; }
                continue;
            }

            float overlapX = Math.min(px + PLAYER_RADIUS, obj.maxX)
                           - Math.max(px - PLAYER_RADIUS, obj.minX);
            float overlapY = Math.min(eyeY, obj.maxY) - Math.max(feetY, obj.minY);
            float overlapZ = Math.min(pz + PLAYER_RADIUS, obj.maxZ)
                           - Math.max(pz - PLAYER_RADIUS, obj.minZ);

            if (overlapY < overlapX && overlapY < overlapZ) {
                if (feetY > obj.centerY()) {
                    position.y = obj.maxY + EYE_HEIGHT;
                    if (velocityY < 0f) { velocityY = 0f; onGround = true; sliding = false; }
                } else {
                    if (velocityY > 0f) velocityY = 0f;
                }
            } else if (overlapX <= overlapZ) {
                float dir = px < obj.centerX() ? -1f : 1f;
                position.x += dir * overlapX;
                px = (float) position.x;
            } else {
                float dir = pz < obj.centerZ() ? -1f : 1f;
                position.z += dir * overlapZ;
                pz = (float) position.z;
            }
        }
    }

    private float queryGroundY(float x, float z) {
        return terrainProvider != null ? terrainProvider.getHeightAt(x, z) : 0f;
    }

    public boolean isOnGround() { return onGround; }
    public boolean isSliding()  { return sliding;  }
    public float   getVelocityY() { return velocityY; }
}
