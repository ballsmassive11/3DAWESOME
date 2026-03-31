package world;

import javax.vecmath.Vector3d;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Camera class that represents a "flying" viewpoint.
 * Handles movement and rotation based on input.
 */
public class Camera {
    private Vector3d position;
    private double yaw; // Rotation around Y axis (in radians)
    private double pitch; // Rotation around X axis (in radians)

    private double moveSpeed = 5.0; // Units per second
    private double turnSpeed = 2.0; // Radians per second

    private Set<Integer> activeKeys = new HashSet<>();

    public Camera() {
        this.position = new Vector3d(0, 15, 5);
        this.yaw = 0;
        this.pitch = 0;
    }

    public void setPosition(double x, double y, double z) {
        this.position.set(x, y, z);
    }

    public Vector3d getPosition() {
        return position;
    }

    public double getYaw() {
        return yaw;
    }

    public double getPitch() {
        return pitch;
    }

    public void update(double deltaTime) {
        double dx = 0, dy = 0, dz = 0;
        double dyaw = 0, dpitch = 0;

        // Rotation controls (Arrow keys)
        if (activeKeys.contains(KeyEvent.VK_LEFT)) dyaw += turnSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_RIGHT)) dyaw -= turnSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_UP)) dpitch += turnSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_DOWN)) dpitch -= turnSpeed * deltaTime;

        yaw += dyaw;
        pitch += dpitch;

        // Clamp pitch to avoid flipping over (approx. 85 degrees)
        double limit = Math.PI / 2.1;
        if (pitch > limit) pitch = limit;
        if (pitch < -limit) pitch = -limit;

        // Movement controls (WASD)
        double moveX = 0, moveZ = 0;
        if (activeKeys.contains(KeyEvent.VK_W)) moveZ -= moveSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_S)) moveZ += moveSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_A)) moveX -= moveSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_D)) moveX += moveSpeed * deltaTime;

        // Vertical movement (Space/Shift)
        if (activeKeys.contains(KeyEvent.VK_SPACE)) dy += moveSpeed * deltaTime;
        if (activeKeys.contains(KeyEvent.VK_SHIFT)) dy -= moveSpeed * deltaTime;

        // Calculate movement based on yaw
        dz += moveZ * Math.cos(yaw) - moveX * Math.sin(yaw);
        dx += moveZ * Math.sin(yaw) + moveX * Math.cos(yaw);

        position.x += dx;
        position.y += dy;
        position.z += dz;
    }

    public void keyPressed(int keyCode) {
        activeKeys.add(keyCode);
    }

    public void keyReleased(int keyCode) {
        activeKeys.remove(keyCode);
    }
}
