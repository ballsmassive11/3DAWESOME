package particles;

import javax.media.j3d.*;
import javax.vecmath.Point3d;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages all particles that share the same atlas texture.
 * Each batch owns one {@link QuadArray} and one {@link ShaderAppearance},
 * allowing the {@link ParticleRenderer} to support multiple textures simultaneously
 * with one draw call per unique atlas.
 */
class ParticleBatch {

    static final int MAX_PARTICLES = 5_000;

    final String atlasPath; // null = solid-color (white fallback)

    private final List<Particle> particles = new ArrayList<>(256);
    private final List<Particle> pending   = new ArrayList<>(64);

    private final QuadArray    geometry;
    private final Shape3D      shape;
    private final BranchGroup  branchGroup;
    private final ParticleShader shader;

    // Flat arrays rebuilt every frame
    private final float[] coords; // MAX_PARTICLES * 4 * 3
    private final float[] colors; // MAX_PARTICLES * 4 * 4
    private final float[] uvs;    // MAX_PARTICLES * 4 * 2

    // -------------------------------------------------------------------------

    /**
     * @param atlasPath  Atlas PNG path (null = white fallback / vertex color only).
     * @param atlasGrid  Sprites per row and column (1 = whole image is sprite 0).
     */
    ParticleBatch(String atlasPath, int atlasGrid) {
        this.atlasPath = atlasPath;
        this.shader    = new ParticleShader(atlasPath, atlasGrid);

        int vertexCount = MAX_PARTICLES * 4;
        coords = new float[vertexCount * 3];
        colors = new float[vertexCount * 4];
        uvs    = new float[vertexCount * 2];

        geometry = new QuadArray(vertexCount,
                QuadArray.COORDINATES | QuadArray.COLOR_4 | QuadArray.TEXTURE_COORDINATE_2);
        geometry.setCapability(QuadArray.ALLOW_COORDINATE_WRITE);
        geometry.setCapability(QuadArray.ALLOW_COLOR_WRITE);
        geometry.setCapability(QuadArray.ALLOW_TEXCOORD_WRITE);

        shape = new Shape3D(geometry, shader.getAppearance());
        shape.setCapability(Shape3D.ALLOW_GEOMETRY_WRITE);
        // Let Java3D compute bounds naturally. Sorting is now handled by OrderedGroup
        // at the World level, or by TRANSPARENCY_SORT_GEOMETRY in Game3DRenderer.
        shape.setBoundsAutoCompute(true);

        branchGroup = new BranchGroup();
        branchGroup.setCapability(BranchGroup.ALLOW_DETACH);
        branchGroup.addChild(shape);
    }

    BranchGroup getBranchGroup() { return branchGroup; }
    int getParticleCount()       { return particles.size(); }

    // -------------------------------------------------------------------------

    void emit(Particle p) {
        synchronized (pending) { pending.add(p); }
    }

    /**
     * Steps all particles, removes dead ones, and uploads fresh billboard geometry.
     *
     * @param dt     seconds since last frame
     * @param rightX camera-right X component
     * @param rightY camera-right Y component
     * @param rightZ camera-right Z component
     * @param upX    camera-up X component
     * @param upY    camera-up Y component
     * @param upZ    camera-up Z component
     */
    void update(double dt,
                float rightX, float rightY, float rightZ,
                float upX,    float upY,    float upZ,
                double camX,  double camY,  double camZ) {
        // Drain pending emissions
        synchronized (pending) {
            for (Particle p : pending)
                if (particles.size() < MAX_PARTICLES) particles.add(p);
            pending.clear();
        }

        Iterator<Particle> it = particles.iterator();
        int qi = 0;

        while (it.hasNext()) {
            Particle p = it.next();
            if (!p.update(dt))       { it.remove(); continue; }
            if (qi >= MAX_PARTICLES) { it.remove(); continue; }

            // Rotated billboard axes for this particle's spin
            float theta = (float) Math.toRadians(p.rotation);
            float cosR  = (float) Math.cos(theta);
            float sinR  = (float) Math.sin(theta);
            float h     = p.size * 0.5f;

            float rX = cosR * rightX + sinR * upX;
            float rY = cosR * rightY + sinR * upY;
            float rZ = cosR * rightZ + sinR * upZ;
            float uX = -sinR * rightX + cosR * upX;
            float uY = -sinR * rightY + cosR * upY;
            float uZ = -sinR * rightZ + cosR * upZ;

            float px = (float) p.position.x;
            float py = (float) p.position.y;
            float pz = (float) p.position.z;

            // BL, BR, TR, TL
            int ci = qi * 12;
            coords[ci     ] = px - h*rX - h*uX;  coords[ci +  1] = py - h*rY - h*uY;  coords[ci +  2] = pz - h*rZ - h*uZ;
            coords[ci +  3] = px + h*rX - h*uX;  coords[ci +  4] = py + h*rY - h*uY;  coords[ci +  5] = pz + h*rZ - h*uZ;
            coords[ci +  6] = px + h*rX + h*uX;  coords[ci +  7] = py + h*rY + h*uY;  coords[ci +  8] = pz + h*rZ + h*uZ;
            coords[ci +  9] = px - h*rX + h*uX;  coords[ci + 10] = py - h*rY + h*uY;  coords[ci + 11] = pz - h*rZ + h*uZ;

            int coi = qi * 16;
            for (int v = 0; v < 4; v++) {
                int base = coi + v * 4;
                colors[base    ] = p.color.x;
                colors[base + 1] = p.color.y;
                colors[base + 2] = p.color.z;
                colors[base + 3] = p.color.w;
            }

            float[] spriteUVs = shader.getSpriteUVs(p.spriteIndex);
            System.arraycopy(spriteUVs, 0, uvs, qi * 8, 8);

            qi++;
        }

        // Zero unused slots
        for (int i = qi * 12, end = MAX_PARTICLES * 12; i < end; i++) coords[i] = 0f;
        for (int i = qi * 16, end = MAX_PARTICLES * 16; i < end; i++) colors[i] = 0f;
        for (int i = qi *  8, end = MAX_PARTICLES *  8; i < end; i++) uvs[i]    = 0f;

        geometry.setCoordinates(0, coords);
        geometry.setColors(0, colors);
        geometry.setTextureCoordinates(0, 0, uvs);
    }

    void clear() {
        particles.clear();
        synchronized (pending) { pending.clear(); }
    }
}
