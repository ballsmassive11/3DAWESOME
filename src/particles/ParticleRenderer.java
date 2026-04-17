package particles;

import javax.media.j3d.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Renders a pool of {@link Particle}s as camera-facing billboard quads backed by a
 * texture atlas and a GLSL shader ({@link ParticleShader}).
 *
 * <h3>Usage</h3>
 * <pre>
 *   ParticleRenderer pr = new ParticleRenderer(world);
 *   world.addNode(pr.getBranchGroup());        // once, during scene setup
 *
 *   // each frame (from World.update or WorldUpdateBehavior):
 *   pr.update(dt, cam.getYaw(), cam.getPitch());
 *
 *   // spawn a particle:
 *   pr.emit(new Particle(pos, vel, new Color4f(1,0.5f,0,1), 0.3f, 2.0f));
 *   pr.emit(new Particle(pos, vel, spriteIndex, size, lifetime));
 * </pre>
 *
 * <h3>How it works</h3>
 * All particles share a single {@link QuadArray} pre-allocated for {@value #MAX_PARTICLES}
 * quads (4 vertices each).  Every frame the CPU:
 * <ol>
 *   <li>Steps each particle via {@link Particle#update(double)}.</li>
 *   <li>Computes four billboard corners (rotated around the camera right/up axes).</li>
 *   <li>Looks up the sprite's UV region in the atlas via
 *       {@link ParticleShader#getSpriteUVs(int)}.</li>
 *   <li>Packs coordinates, colors and UVs into flat arrays, then uploads them via
 *       {@code setCoordinates}/{@code setColors}/{@code setTextureCoordinates}.</li>
 * </ol>
 * Unused slots become zero-area, alpha-0 quads and are invisible.
 */
public class ParticleRenderer {

    /** Hard cap on simultaneously live particles. */
    private static final int MAX_PARTICLES = 5_000;

    private final List<Particle> particles = new ArrayList<>(256);

    /** Thread-safe staging list; drained into {@code particles} at the top of each frame. */
    private final List<Particle> pending = new ArrayList<>(64);

    private final BranchGroup branchGroup;
    private final QuadArray   geometry;

    // Flat arrays rebuilt each frame (reused to avoid GC pressure)
    private final float[] coords; // MAX_PARTICLES * 4 verts * 3 floats (XYZ)
    private final float[] colors; // MAX_PARTICLES * 4 verts * 4 floats (RGBA)
    private final float[] uvs;    // MAX_PARTICLES * 4 verts * 2 floats (UV)

    // -------------------------------------------------------------------------

    public ParticleRenderer() {
        int vertexCount = MAX_PARTICLES * 4;
        coords = new float[vertexCount * 3];
        colors = new float[vertexCount * 4];
        uvs    = new float[vertexCount * 2];

        geometry = new QuadArray(vertexCount,
                QuadArray.COORDINATES | QuadArray.COLOR_4 | QuadArray.TEXTURE_COORDINATE_2);
        geometry.setCapability(QuadArray.ALLOW_COORDINATE_WRITE);
        geometry.setCapability(QuadArray.ALLOW_COLOR_WRITE);
        geometry.setCapability(QuadArray.ALLOW_TEXCOORD_WRITE);

        ParticleShader shader = new ParticleShader();
        Shape3D shape = new Shape3D(geometry, shader.getAppearance());
        shape.setCapability(Shape3D.ALLOW_GEOMETRY_WRITE);

        branchGroup = new BranchGroup();
        branchGroup.setCapability(BranchGroup.ALLOW_DETACH);
        branchGroup.addChild(shape);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the BranchGroup to attach to the scene exactly once.
     * Pass to {@code world.addNode(pr.getBranchGroup())} during setup.
     */
    public BranchGroup getBranchGroup() { return branchGroup; }

    /**
     * Queues a particle for emission.  Safe to call from any thread;
     * the particle enters the active list at the start of the next {@link #update}.
     */
    public void emit(Particle p) {
        synchronized (pending) { pending.add(p); }
    }

    /**
     * Advances all particles, removes expired ones, and rebuilds the billboard geometry.
     * Call once per frame from {@code World.update()} or {@code WorldUpdateBehavior}.
     *
     * @param dt          seconds since last frame
     * @param cameraYaw   camera yaw in radians ({@code Camera.getYaw()})
     * @param cameraPitch camera pitch in radians ({@code Camera.getPitch()})
     */
    public void update(double dt, double cameraYaw, double cameraPitch) {
        // Drain pending emissions
        synchronized (pending) {
            for (Particle p : pending)
                if (particles.size() < MAX_PARTICLES) particles.add(p);
            pending.clear();
        }

        // Camera basis vectors for screen-aligned billboard:
        //   right = ( cos(yaw),             0,          -sin(yaw)           )
        //   up    = ( sin(yaw)*sin(pitch),  cos(pitch),  cos(yaw)*sin(pitch) )
        double cy = Math.cos(cameraYaw),   sy = Math.sin(cameraYaw);
        double sp = Math.sin(cameraPitch), cp = Math.cos(cameraPitch);

        float rightX = (float)  cy,           rightY = 0f,        rightZ = (float) -sy;
        float upX    = (float) (sy * sp),      upY    = (float) cp, upZ   = (float) (cy * sp);

        Iterator<Particle> it = particles.iterator();
        int qi = 0; // active quad counter

        while (it.hasNext()) {
            Particle p = it.next();
            if (!p.update(dt))       { it.remove(); continue; }
            if (qi >= MAX_PARTICLES) { it.remove(); continue; }

            // --- Billboard corners ---
            float theta = (float) Math.toRadians(p.rotation);
            float cosR  = (float) Math.cos(theta);
            float sinR  = (float) Math.sin(theta);
            float h     = p.size * 0.5f;

            // Rotated right/up axes for this particle's spin
            float rX = cosR * rightX + sinR * upX;
            float rY = cosR * rightY + sinR * upY;
            float rZ = cosR * rightZ + sinR * upZ;
            float uX = -sinR * rightX + cosR * upX;
            float uY = -sinR * rightY + cosR * upY;
            float uZ = -sinR * rightZ + cosR * upZ;

            float px = (float) p.position.x;
            float py = (float) p.position.y;
            float pz = (float) p.position.z;

            // Vertices: BL, BR, TR, TL (CCW when facing camera)
            int ci = qi * 12;
            coords[ci     ] = px - h*rX - h*uX;  coords[ci +  1] = py - h*rY - h*uY;  coords[ci +  2] = pz - h*rZ - h*uZ;
            coords[ci +  3] = px + h*rX - h*uX;  coords[ci +  4] = py + h*rY - h*uY;  coords[ci +  5] = pz + h*rZ - h*uZ;
            coords[ci +  6] = px + h*rX + h*uX;  coords[ci +  7] = py + h*rY + h*uY;  coords[ci +  8] = pz + h*rZ + h*uZ;
            coords[ci +  9] = px - h*rX + h*uX;  coords[ci + 10] = py - h*rY + h*uY;  coords[ci + 11] = pz - h*rZ + h*uZ;

            // --- Colors (same RGBA for all 4 verts) ---
            int coi = qi * 16;
            for (int v = 0; v < 4; v++) {
                int base = coi + v * 4;
                colors[base    ] = p.color.x;
                colors[base + 1] = p.color.y;
                colors[base + 2] = p.color.z;
                colors[base + 3] = p.color.w;
            }

            // --- Atlas UVs ---
            float[] spriteUVs = ParticleShader.getSpriteUVs(p.spriteIndex);
            int ui = qi * 8;
            System.arraycopy(spriteUVs, 0, uvs, ui, 8);

            qi++;
        }

        // Zero out unused slots (invisible degenerate quads, alpha = 0)
        for (int i = qi * 12, end = MAX_PARTICLES * 12; i < end; i++) coords[i] = 0f;
        for (int i = qi * 16, end = MAX_PARTICLES * 16; i < end; i++) colors[i] = 0f;
        for (int i = qi *  8, end = MAX_PARTICLES *  8; i < end; i++) uvs[i]    = 0f;

        geometry.setCoordinates(0, coords);
        geometry.setColors(0, colors);
        geometry.setTextureCoordinates(0, 0, uvs);
    }

    /** Number of currently live particles. */
    public int getParticleCount() { return particles.size(); }

    /** Immediately removes all live and pending particles. */
    public void clear() {
        particles.clear();
        synchronized (pending) { pending.clear(); }
    }
}
