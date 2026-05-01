package renderer;

import world.Camera;
import world.World;

import javax.media.j3d.Behavior;
import javax.media.j3d.BoundingSphere;
import javax.media.j3d.WakeupOnElapsedFrames;
import javax.vecmath.Point3d;
import java.util.Enumeration;

/**
 * Java3D Behavior that fires every frame and drives World.update(deltaTime).
 */
@SuppressWarnings("rawtypes")
public class WorldUpdateBehavior extends Behavior {
    private final World world;
    private final WakeupOnElapsedFrames wakeup = new WakeupOnElapsedFrames(0);
    private long lastTime;
    private Game3DRenderer renderer;
    private double totalTime = 0.0;
    
    private double fpsAccumulator = 0;
    private int fpsFrameCount = 0;
    private double lastFpsUpdateTime = 0;
    private double currentFps = 0;

    public WorldUpdateBehavior(World world) {
        this(world, null);
    }

    public WorldUpdateBehavior(World world, Game3DRenderer renderer) {
        this.world = world;
        this.renderer = renderer;
        setSchedulingBounds(new BoundingSphere(new Point3d(0, 0, 0), Double.MAX_VALUE));
    }

    @Override
    public void initialize() {
        lastTime = System.currentTimeMillis();
        wakeupOn(wakeup);
    }

    @Override
    public void processStimulus(Enumeration criteria) {
        long now = System.currentTimeMillis();
        double deltaTime = (now - lastTime) / 1000.0;
        
        // Cap deltaTime to avoid huge jumps
        if (deltaTime > 0.1) deltaTime = 0.1;
        if (deltaTime < 0) deltaTime = 0;
        
        lastTime = now;
        totalTime += deltaTime;
        world.update(deltaTime);

        if (renderer != null) {
            renderer.syncCamera(deltaTime);
            
            fpsAccumulator += deltaTime;
            fpsFrameCount++;
            
            if (totalTime - lastFpsUpdateTime >= 0.1) {
                currentFps = fpsFrameCount / (totalTime - lastFpsUpdateTime);
                fpsAccumulator = 0;
                fpsFrameCount = 0;
                lastFpsUpdateTime = totalTime;
            }

            Camera cam = world.getCamera();
            renderer.updateHud(currentFps,
                    cam.getPosition().x, cam.getPosition().y, cam.getPosition().z,
                    cam.getYaw(), cam.getPitch(),
                    world.getObjects().size(),
                    world.getTotalPolygonCount(),
                    world.getSeed(),
                    world.getPlayer().getPhysics().isFlying());
        }
        
        wakeupOn(wakeup);
    }
}
