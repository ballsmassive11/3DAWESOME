package particles;

import javax.media.j3d.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages and renders multiple particle batches, one per unique atlas texture.
 *
 * <h3>Usage</h3>
 * <pre>
 *   ParticleRenderer pr = new ParticleRenderer();
 *   world.addNode(pr.getBranchGroup());          // once during setup
 *
 *   // optional: register a multi-sprite atlas before emitting into it
 *   pr.registerAtlas("resources/particles/spritesheet.png", 4); // 4×4 grid
 *
 *   // each frame:
 *   pr.update(dt, cam.getYaw(), cam.getPitch());
 *
 *   // spawn particles (atlasPath routes to the right batch automatically):
 *   pr.emit(new Particle(pos, vel, "resources/particles/fire.png", 0.4f, 1.5f));
 *   pr.emit(new Particle(pos, vel, null, 0.2f, 1.0f));  // colored, no texture
 * </pre>
 *
 * <h3>How batches work</h3>
 * Each unique {@link Particle#atlasPath} gets its own {@link ParticleBatch}, which owns
 * a {@link QuadArray} and a {@link ShaderAppearance}.  Batches are created lazily on
 * first {@link #emit}.  All batch {@link BranchGroup}s are children of a shared root
 * group that is added to the scene once.
 *
 * <h3>Atlas grids</h3>
 * By default every atlas is treated as a 1×1 grid (the whole image = sprite 0).
 * Call {@link #registerAtlas(String, int)} before the first emission for that path to
 * specify a different grid size (e.g. 4 for a 4×4 spritesheet).
 */
public class ParticleRenderer {

    /** Key used for the null-atlas (colored, no texture) batch. */
    private static final String NULL_KEY = "__null__";

    // atlasPath → batch (null atlasPath stored under NULL_KEY)
    private final Map<String, ParticleBatch>  batches      = new HashMap<>();
    // atlasPath → grid size registered before first emission
    private final Map<String, Integer>        gridRegistry = new HashMap<>();

    private final BranchGroup rootBG;

    // -------------------------------------------------------------------------

    public ParticleRenderer() {
        rootBG = new BranchGroup();
        rootBG.setCapability(BranchGroup.ALLOW_DETACH);
        rootBG.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
        rootBG.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the root BranchGroup to add to the scene exactly once.
     * Pass to {@code world.addNode(pr.getBranchGroup())} during setup.
     */
    public BranchGroup getBranchGroup() { return rootBG; }

    /**
     * Registers a custom atlas grid size for a given path.
     * Must be called before the first {@link #emit} that uses this atlasPath.
     *
     * @param atlasPath File path to the atlas PNG.
     * @param gridSize  Number of sprites per row and column (e.g. 4 for a 4×4 atlas).
     */
    public void registerAtlas(String atlasPath, int gridSize) {
        gridRegistry.put(atlasPath, gridSize);
    }

    /**
     * Queues a particle for emission into the appropriate batch.
     * Safe to call from any thread.
     */
    public void emit(Particle p) {
        getOrCreateBatch(p.atlasPath).emit(p);
    }

    /**
     * Advances all batches, removes expired particles, and rebuilds geometry.
     * Call once per frame.
     *
     * @param dt          seconds since last frame
     * @param cameraYaw   camera yaw in radians
     * @param cameraPitch camera pitch in radians
     */
    public void update(double dt, double cameraYaw, double cameraPitch,
                       double camX, double camY, double camZ) {
        // Camera basis vectors for screen-aligned billboards:
        //   right = ( cos(yaw),            0,          -sin(yaw)           )
        //   up    = ( sin(yaw)*sin(pitch),  cos(pitch),  cos(yaw)*sin(pitch) )
        double cy = Math.cos(cameraYaw),   sy = Math.sin(cameraYaw);
        double sp = Math.sin(cameraPitch), cp = Math.cos(cameraPitch);

        float rightX = (float)  cy,          rightY = 0f,        rightZ = (float) -sy;
        float upX    = (float) (sy * sp),     upY    = (float) cp, upZ   = (float) (cy * sp);

        for (ParticleBatch batch : batches.values())
            batch.update(dt, rightX, rightY, rightZ, upX, upY, upZ, camX, camY, camZ);
    }

    /** Total live particle count across all batches. */
    public int getParticleCount() {
        int total = 0;
        for (ParticleBatch b : batches.values()) total += b.getParticleCount();
        return total;
    }

    /** Removes all live and pending particles from every batch. */
    public void clear() {
        for (ParticleBatch b : batches.values()) b.clear();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private ParticleBatch getOrCreateBatch(String atlasPath) {
        String key = atlasPath != null ? atlasPath : NULL_KEY;
        ParticleBatch batch = batches.get(key);
        if (batch == null) {
            int gridSize = gridRegistry.getOrDefault(atlasPath, 1);
            batch = new ParticleBatch(atlasPath, gridSize);
            batches.put(key, batch);
            rootBG.addChild(batch.getBranchGroup());
        }
        return batch;
    }
}
