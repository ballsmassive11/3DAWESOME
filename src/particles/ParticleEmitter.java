package particles;

import javax.vecmath.*;
import java.util.Random;

/**
 * Emits particles each frame into a {@link ParticleRenderer}.
 *
 * <h3>Spawn modes</h3>
 * <ul>
 *   <li>{@link SpawnMode#POINT} — all particles originate from the emitter's exact position.</li>
 *   <li>{@link SpawnMode#BRICK} — particles originate from random positions within a box volume
 *       centred on the emitter (configured with {@link #setBrickSize}).</li>
 * </ul>
 *
 * <h3>Typical usage</h3>
 * <pre>
 *   ParticleEmitter fire = new ParticleEmitter(10, 0, 5)
 *       .setEmissionRate(30)
 *       .setSpeed(3.0)
 *       .setSpread(0.4)
 *       .setPitch(Math.PI / 2)          // straight up
 *       .setStartColor(new Color4f(1f, 0.6f, 0.1f, 1f))
 *       .setEndColor  (new Color4f(0.8f, 0.1f, 0f,   0f))
 *       .setStartSize(0.5f)
 *       .setLifetime(1.2f)
 *       .setGravityScale(0f);
 *
 *   world.addEmitter(fire);
 * </pre>
 */
public class ParticleEmitter {

    // ── Spawn mode ────────────────────────────────────────────────────────────

    public enum SpawnMode {
        /** All particles emit from the emitter's exact position. */
        POINT,
        /** Particles emit from random positions within a box volume centred on the emitter. */
        BRICK
    }

    // ── Location & orientation ────────────────────────────────────────────────

    private final Vector3d position;
    /** Horizontal emission direction in radians (0 = −Z, matches camera yaw convention). */
    private double yaw   = 0.0;
    /** Vertical emission direction in radians (positive = upward). */
    private double pitch = Math.PI / 2.0;  // default: straight up

    // ── Spawn volume ──────────────────────────────────────────────────────────

    private SpawnMode spawnMode = SpawnMode.POINT;
    /** Full extents of the brick spawn volume (x width, y height, z depth). */
    private final Vector3f brickSize = new Vector3f(1f, 1f, 1f);

    // ── Velocity ──────────────────────────────────────────────────────────────

    /** Mean speed of emitted particles (world units / second). */
    private double speed  = 2.0;
    /**
     * Half-angle of the emission cone in radians.
     * 0 = perfectly collimated, {@code Math.PI} = full sphere.
     */
    private double spread = 0.3;

    // ── Per-particle properties ───────────────────────────────────────────────

    private Color4f startColor    = new Color4f(1f, 1f, 1f, 1f);
    private Color4f endColor      = new Color4f(1f, 1f, 1f, 0f);
    private float   startSize     = 0.3f;
    private float   endSize       = 0f;
    private float   lifetime      = 1.5f;
    private float   gravityScale  = 0.5f;
    private float   rotationSpeed = 0f;
    private int     spriteIndex   = 0;
    /** Atlas texture path, or {@code null} for a solid-colour particle. */
    private String  atlasPath     = null;

    // ── Emission control ──────────────────────────────────────────────────────

    private double  emissionRate = 10.0;  // particles per second
    private boolean active       = true;
    private double  accumulator  = 0.0;

    private final Random rng = new Random();

    // ── Constructors ──────────────────────────────────────────────────────────

    public ParticleEmitter(Vector3d position) {
        this.position = new Vector3d(position);
    }

    public ParticleEmitter(double x, double y, double z) {
        this.position = new Vector3d(x, y, z);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Advances the emitter by {@code dt} seconds and emits any due particles into {@code renderer}.
     * Call once per frame (e.g. from {@code World.update()}).
     */
    public void update(double dt, ParticleRenderer renderer) {
        if (!active) return;

        accumulator += emissionRate * dt;
        int toEmit = (int) accumulator;
        accumulator -= toEmit;

        for (int i = 0; i < toEmit; i++) {
            renderer.emit(createParticle());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Particle createParticle() {
        return new Particle(
                spawnPosition(),
                emitVelocity(),
                new Color4f(startColor),
                new Color4f(endColor),
                startSize, endSize,
                lifetime, gravityScale, rotationSpeed,
                spriteIndex, atlasPath
        );
    }

    /** Returns a spawn position based on the current {@link SpawnMode}. */
    private Vector3d spawnPosition() {
        if (spawnMode == SpawnMode.BRICK) {
            return new Vector3d(
                    position.x + (rng.nextDouble() - 0.5) * brickSize.x,
                    position.y + (rng.nextDouble() - 0.5) * brickSize.y,
                    position.z + (rng.nextDouble() - 0.5) * brickSize.z
            );
        }
        return new Vector3d(position);
    }

    /**
     * Computes an initial velocity by rotating the emission direction (yaw + pitch) by a
     * random angle within the spread cone, then scaling by {@link #speed}.
     */
    private Vector3d emitVelocity() {
        // Unit direction from yaw + pitch
        double cosP = Math.cos(pitch);
        double dx = -Math.sin(yaw) * cosP;
        double dy =  Math.sin(pitch);
        double dz = -Math.cos(yaw) * cosP;

        if (spread <= 0.0) {
            return new Vector3d(dx * speed, dy * speed, dz * speed);
        }

        // Pick a random rotation inside the cone using Rodrigues' formula
        Vector3d dir      = new Vector3d(dx, dy, dz);
        Vector3d perp     = perpendicular(dir);
        // Random azimuth around the cone axis
        Vector3d randPerp = rotateAround(perp, dir, rng.nextDouble() * 2 * Math.PI);
        // Random polar angle within the half-angle spread
        Vector3d result   = rotateAround(dir, randPerp, rng.nextDouble() * spread);
        result.scale(speed);
        return result;
    }

    /** Returns a unit vector perpendicular to {@code v}. */
    private static Vector3d perpendicular(Vector3d v) {
        Vector3d result;
        if (Math.abs(v.x) <= Math.abs(v.y) && Math.abs(v.x) <= Math.abs(v.z)) {
            result = new Vector3d(0, -v.z, v.y);
        } else if (Math.abs(v.y) <= Math.abs(v.z)) {
            result = new Vector3d(-v.z, 0, v.x);
        } else {
            result = new Vector3d(-v.y, v.x, 0);
        }
        result.normalize();
        return result;
    }

    /** Rotates {@code v} around unit {@code axis} by {@code angle} radians (Rodrigues). */
    private static Vector3d rotateAround(Vector3d v, Vector3d axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vector3d cross = new Vector3d();
        cross.cross(axis, v);
        double dot = axis.dot(v);
        return new Vector3d(
                v.x * cos + cross.x * sin + axis.x * dot * (1 - cos),
                v.y * cos + cross.y * sin + axis.y * dot * (1 - cos),
                v.z * cos + cross.z * sin + axis.z * dot * (1 - cos)
        );
    }

    // ── Fluent setters ────────────────────────────────────────────────────────

    /** Sets the spawn mode (POINT or BRICK). */
    public ParticleEmitter setSpawnMode(SpawnMode mode) {
        this.spawnMode = mode;
        return this;
    }

    /** Sets the full extents of the brick spawn volume. Only used in BRICK mode. */
    public ParticleEmitter setBrickSize(float x, float y, float z) {
        this.brickSize.set(x, y, z);
        return this;
    }

    /** Horizontal emission direction in radians. */
    public ParticleEmitter setYaw(double yaw) {
        this.yaw = yaw;
        return this;
    }

    /** Vertical emission direction in radians (positive = upward). */
    public ParticleEmitter setPitch(double pitch) {
        this.pitch = pitch;
        return this;
    }

    /** Mean particle speed in world units / second. */
    public ParticleEmitter setSpeed(double speed) {
        this.speed = speed;
        return this;
    }

    /** Half-angle cone spread in radians (0 = collimated, PI = sphere). */
    public ParticleEmitter setSpread(double spread) {
        this.spread = spread;
        return this;
    }

    public ParticleEmitter setStartColor(Color4f c) {
        this.startColor = new Color4f(c);
        return this;
    }

    public ParticleEmitter setEndColor(Color4f c) {
        this.endColor = new Color4f(c);
        return this;
    }

    public ParticleEmitter setStartSize(float s) {
        this.startSize = s;
        return this;
    }

    public ParticleEmitter setEndSize(float s) {
        this.endSize = s;
        return this;
    }

    public ParticleEmitter setLifetime(float lt) {
        this.lifetime = lt;
        return this;
    }

    public ParticleEmitter setGravityScale(float g) {
        this.gravityScale = g;
        return this;
    }

    public ParticleEmitter setRotationSpeed(float rs) {
        this.rotationSpeed = rs;
        return this;
    }

    public ParticleEmitter setSpriteIndex(int idx) {
        this.spriteIndex = idx;
        return this;
    }

    /** Texture atlas path, or {@code null} for a solid-colour particle. */
    public ParticleEmitter setAtlasPath(String path) {
        this.atlasPath = path;
        return this;
    }

    /** Particles emitted per second. */
    public ParticleEmitter setEmissionRate(double rate) {
        this.emissionRate = rate;
        return this;
    }

    /** When false the emitter produces no particles but retains its state. */
    public ParticleEmitter setActive(boolean active) {
        this.active = active;
        return this;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Vector3d  getPosition()     { return position; }
    public SpawnMode getSpawnMode()    { return spawnMode; }
    public double    getYaw()          { return yaw; }
    public double    getPitch()        { return pitch; }
    public double    getSpeed()        { return speed; }
    public double    getSpread()       { return spread; }
    public double    getEmissionRate() { return emissionRate; }
    public boolean   isActive()        { return active; }
    public Vector3f  getBrickSize()    { return brickSize; }
}
