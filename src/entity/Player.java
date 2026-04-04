package entity;

import physics.AABB;
import world.Camera;

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

    private final Camera camera;

    public Player() {
        super(); // initialises position to (0, 15, 5), creates EntityPhysics
        // Pass the shared position to Camera so both always see the same vector
        this.camera = new Camera(position);
    }

    @Override
    public void update(double deltaTime, List<AABB> worldAABBs) {
        // 1. Update camera rotation from arrow keys
        camera.update(deltaTime);

        // 2. Keep entity yaw in sync with where the camera is facing
        yaw = camera.getYaw();

        // 3. Apply WASD horizontal movement to the shared position
        camera.applyMovement(deltaTime);

        // 4. Physics: gravity, jumping, terrain clamping, AABB collision
        physics.update(deltaTime, position,
                       camera.consumeJumpRequest(),
                       worldAABBs,
                       camera.getVerticalInput());

        // 5. Move the visible body model to match
        syncModelTransform();
    }

    /** The camera used for view/input — its position IS the entity position. */
    public Camera getCamera() {
        return camera;
    }
}
