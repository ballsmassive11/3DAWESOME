package world;

import javax.vecmath.Vector3d;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Camera / player viewpoint.
 *
 * Handles horizontal movement (WASD) and look rotation (arrow keys).
 * Vertical position is now controlled by PlayerPhysics — this class no
 * longer flies up/down freely. Space sets a jump request that physics
 * consumes once per frame.
 */
public class Camera {
    private Vector3d position;
    private double yaw;   // Rotation around Y axis (radians)
    private double pitch; // Rotation around X axis (radians)

    private double moveSpeed = 5.0; // Units per second
    private double turnSpeed = 2.0; // Radians per second

    private Set<Integer> activeKeys = new HashSet<>();
    private boolean jumpRequested   = false;

    public Camera() {
        this.position = new Vector3d(0, 15, 5);
        this.yaw   = 0;
        this.pitch = 0;
    }

    public void setPosition(double x, double y, double z) {
        this.position.set(x, y, z);
    }

    public Vector3d getPosition() { return position; }
    public double   getYaw()      { return yaw; }
    public double   getPitch()    { return pitch; }

    public void update(double deltaTime) {
        // Rotation (arrow keys)
        if (activeKeys.contains(KeyEvent.VK_LEFT))  yaw   += turnSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_RIGHT)) yaw   -= turnSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_UP))    pitch += turnSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_DOWN))  pitch -= turnSpeed * deltaTime;

        // Clamp pitch (~85 degrees)
        double limit = Math.PI / 2.1;
        if (pitch >  limit) pitch =  limit;
        if (pitch < -limit) pitch = -limit;

        // Horizontal movement (WASD) — Y is left to PlayerPhysics
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
     * the last call. PlayerPhysics calls this once per frame.
     */
    public boolean consumeJumpRequest() {
        boolean r = jumpRequested;
        jumpRequested = false;
        return r;
    }

    public void keyPressed(int keyCode) {
        activeKeys.add(keyCode);
        if (keyCode == KeyEvent.VK_SPACE) jumpRequested = true;
    }

    public void keyReleased(int keyCode) {
        activeKeys.remove(keyCode);
    }
}
