package world;

import javax.media.j3d.*;
import javax.vecmath.Point3d;
import java.util.Enumeration;

/**
 * Java3D Behavior that fires every frame and drives World.update(deltaTime).
 */
public class WorldUpdateBehavior extends Behavior {
    private final World world;
    private final WakeupOnElapsedFrames wakeup = new WakeupOnElapsedFrames(0);
    private long lastTime;

    public WorldUpdateBehavior(World world) {
        this.world = world;
        setSchedulingBounds(new BoundingSphere(new Point3d(0, 0, 0), Double.MAX_VALUE));
    }

    @Override
    public void initialize() {
        lastTime = System.currentTimeMillis();
        wakeupOn(wakeup);
    }

    @Override
    public void processStimulus(Enumeration<WakeupCriterion> criteria) {
        long now = System.currentTimeMillis();
        double deltaTime = (now - lastTime) / 1000.0;
        lastTime = now;
        world.update(deltaTime);
        wakeupOn(wakeup);
    }
}
