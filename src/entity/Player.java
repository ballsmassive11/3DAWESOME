package entity;

import physics.AABB;
import world.Camera;

import javax.media.j3d.BoundingSphere;
import javax.media.j3d.PointLight;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Point3f;
import java.util.List;

/**
 * The user-controlled entity.
 *
 * Player extends Entity and drives movement through a {@link Camera} that
 * captures keyboard input.  The Camera owns yaw, pitch, and the WASD/jump
 * state; Player reads those each frame and feeds them into EntityPhysics.
 *
 * The Camera shares the entity's {@code position} vector directly, so
 * {@code camera.getPosition()} always returns the live eye position —
 * all existing code that reads camera position keeps working unchanged.
 */
public class Player extends Entity {

    // Spring-damper constants.  ζ = DAMPING / (2√STIFFNESS) ≈ 1.004 → overdamped (no oscillation).
    private static final double ROTATION_STIFFNESS = 90.0;
    private static final double ROTATION_DAMPING   = 11.0;

    private final Camera camera;
    private final PointLight pointLight;
    private boolean shiftLockEnabled = false;
    private double  angularVelocity  = 0.0;
    private double  targetYaw        = 0.0;

    public Player() {
        super(); // initialises position to (0, 15, 5), creates EntityPhysics
        // Pass the shared position to Camera so both always see the same vector
        this.camera = new Camera(position);

        // Initialise point light attached to player
        this.pointLight = new PointLight();
        pointLight.setColor(new Color3f(1.0f, 0.95f, 0.8f)); // Warm white
        pointLight.setPosition(new Point3f((float)position.x, (float)position.y, (float)position.z));
        pointLight.setAttenuation(new Point3f(1.0f, 0.05f, 0.005f));
        pointLight.setCapability(PointLight.ALLOW_POSITION_WRITE);
        pointLight.setCapability(PointLight.ALLOW_STATE_WRITE);
        pointLight.setCapability(PointLight.ALLOW_COLOR_WRITE);
        pointLight.setInfluencingBounds(new BoundingSphere(new Point3d(0, 0, 0), Double.MAX_VALUE));
    }

    @Override
    public void update(double deltaTime, List<AABB> worldAABBs) {
        // 1. Update camera rotation from arrow keys
        camera.update(deltaTime);

        // 2. Determine whether the character should rotate this frame.
        //    Shift-lock (when enabled + held) always rotates toward camera yaw.
        //    Otherwise rotate only while actively moving; standing still freezes the model.
        double movYaw = camera.getMovementFacingYaw();
        boolean shouldRotate;
        if (shiftLockEnabled && camera.isShiftHeld()) {
            targetYaw   = camera.getYaw();
            shouldRotate = true;
        } else if (!Double.isNaN(movYaw)) {
            targetYaw   = movYaw;
            shouldRotate = true;
        } else {
            shouldRotate = false;
        }

        // 3. Spring-damper: smoothly drive yaw toward targetYaw while moving.
        //    When not moving, zero angular velocity so the model holds its last facing.
        if (shouldRotate) {
            double diff = shortestAngleDiff(targetYaw, yaw);
            angularVelocity += (diff * ROTATION_STIFFNESS - angularVelocity * ROTATION_DAMPING) * deltaTime;
            yaw             += angularVelocity * deltaTime;
        } else {
            angularVelocity = 0.0;
        }

        // 4. Apply WASD horizontal movement to the shared position
        camera.applyMovement(deltaTime);

        // 4. Physics: gravity, jumping, terrain clamping, AABB collision
        physics.update(deltaTime, position,
                       camera.consumeJumpRequest(),
                       worldAABBs,
                       camera.getVerticalInput());

        // 5. Move the visible body model to match
        syncModelTransform();

        // 6. Update point light position
        pointLight.setPosition(new Point3f((float)position.x, (float)position.y, (float)position.z));
    }

    /** The camera used for view/input — its position IS the entity position. */
    public Camera getCamera() {
        return camera;
    }

    public PointLight getPointLight() {
        return pointLight;
    }

    public boolean isShiftLockEnabled() { return shiftLockEnabled; }
    public void setShiftLockEnabled(boolean enabled) { shiftLockEnabled = enabled; }

    /** Shortest signed angle from {@code current} to {@code target}, in [-π, π]. */
    private static double shortestAngleDiff(double target, double current) {
        double diff = (target - current) % (2 * Math.PI);
        if (diff >  Math.PI) diff -= 2 * Math.PI;
        if (diff < -Math.PI) diff += 2 * Math.PI;
        return diff;
    }
}
