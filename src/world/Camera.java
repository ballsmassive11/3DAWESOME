package world;

import javax.vecmath.Vector3d;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Player view and input state.
 *
 * Camera is responsible for two things only:
 *   1. View orientation — yaw (horizontal) and pitch (vertical) updated from arrow keys.
 *   2. Input state — which keys are held, jump requests.
 *
 * It does NOT own the player's position.  Instead it holds a reference to the
 * owning entity's position vector (set via the constructor) so that
 * {@link #getPosition()} and {@link #applyMovement} always operate on the
 * live entity position.  All existing callers of {@code camera.getPosition()}
 * continue to work without change.
 */
public class Camera {

    // Shared reference: this IS the owning entity's position vector
    private final Vector3d position;

    private double yaw;   // rotation around Y axis (radians)
    private double pitch; // rotation around X axis (radians)

    private double moveSpeed = 5.0; // units per second
    private double turnSpeed = 2.0; // radians per second

    private final Set<Integer> activeKeys = new HashSet<>();
    private boolean jumpRequested = false;
    private boolean rotationDisabled = false;

    /**
     * Constructs a Camera that shares the given position vector.
     * The vector is modified in-place by {@link #applyMovement}.
     */
    public Camera(Vector3d sharedPosition) {
        this.position = sharedPosition;
        this.yaw      = 0;
        this.pitch    = 0;
    }

    public void setPosition(double x, double y, double z) {
        this.position.set(x, y, z);
    }

    public Vector3d getPosition() { return position; }
    public double   getYaw()      { return yaw; }
    public double   getPitch()    { return pitch; }

    /**
     * Updates view rotation from arrow keys.  Call once per frame before
     * {@link #applyMovement}.
     */
    public void update(double deltaTime) {
        if (!rotationDisabled) {
            if (activeKeys.contains(KeyEvent.VK_LEFT))  yaw   += turnSpeed * deltaTime;
            if (activeKeys.contains(KeyEvent.VK_RIGHT)) yaw   -= turnSpeed * deltaTime;
            if (activeKeys.contains(KeyEvent.VK_UP))    pitch += turnSpeed * deltaTime;
            if (activeKeys.contains(KeyEvent.VK_DOWN))  pitch -= turnSpeed * deltaTime;
        }

        double limit = Math.PI / 2.1;
        if (pitch >  limit) pitch =  limit;
        if (pitch < -limit) pitch = -limit;
    }

    /**
     * Applies WASD horizontal movement to the shared position vector.
     * Vertical movement (gravity / jumping) is handled by EntityPhysics.
     */
    public void applyMovement(double deltaTime) {
        double moveX = 0, moveZ = 0;
        if (activeKeys.contains(KeyEvent.VK_W)) moveZ -= moveSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_S)) moveZ += moveSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_A)) moveX -= moveSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_D)) moveX += moveSpeed * deltaTime;

        position.x += moveZ * Math.sin(yaw) + moveX * Math.cos(yaw);
        position.z += moveZ * Math.cos(yaw) - moveX * Math.sin(yaw);
    }

    /**
     * Returns true (and clears the flag) if the player pressed Space since
     * the last call. EntityPhysics calls this once per frame.
     */
    public boolean consumeJumpRequest() {
        boolean r = jumpRequested;
        jumpRequested = false;
        return r;
    }

    /**
     * Returns the current vertical input for fly mode.
     * +1 = ascend (Space held), -1 = descend (Shift held), 0 = neither.
     */
    public float getVerticalInput() {
        boolean up   = activeKeys.contains(KeyEvent.VK_SPACE);
        boolean down = activeKeys.contains(KeyEvent.VK_SHIFT);
        if (up && !down) return  1.0f;
        if (down && !up) return -1.0f;
        return 0.0f;
    }

    public void keyPressed(int keyCode) {
        activeKeys.add(keyCode);
        if (keyCode == KeyEvent.VK_SPACE) jumpRequested = true;
    }

    public void keyReleased(int keyCode) {
        activeKeys.remove(keyCode);
    }

    public void setRotationDisabled(boolean disabled) {
        this.rotationDisabled = disabled;
    }

    /** Returns true while the Shift key is held. */
    public boolean isShiftHeld() {
        return activeKeys.contains(KeyEvent.VK_SHIFT);
    }

    /**
     * Returns the world-space yaw the player model should face based on the
     * current WASD input and camera orientation, or {@code Double.NaN} if no
     * movement keys are held.
     */
    public double getMovementFacingYaw() {
        double moveX = 0, moveZ = 0;
        if (activeKeys.contains(KeyEvent.VK_W)) moveZ -= 1;
        if (activeKeys.contains(KeyEvent.VK_S)) moveZ += 1;
        if (activeKeys.contains(KeyEvent.VK_A)) moveX -= 1;
        if (activeKeys.contains(KeyEvent.VK_D)) moveX += 1;
        if (moveX == 0 && moveZ == 0) return Double.NaN;
        double dx = moveZ * Math.sin(yaw) + moveX * Math.cos(yaw);
        double dz = moveZ * Math.cos(yaw) - moveX * Math.sin(yaw);
        return Math.atan2(-dx, -dz);
    }
}
