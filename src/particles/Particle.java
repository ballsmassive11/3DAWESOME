package particles;

import javax.vecmath.Color4f;
import javax.vecmath.Vector3d;


/**
 * A single particle: lightweight data + per-frame update.
 * No Java3D scene-graph nodes; ParticleRenderer owns those.
 */
public class Particle {

    // World-space state
    public final Vector3d position;
    public final Vector3d velocity;

    // Visual
    public final Color4f startColor;
    public final Color4f endColor;
    /** Current interpolated RGBA (updated each frame). */
    public final Color4f color;
    /** World-space point size at birth (pixels). */
    public float startSize;
    /** World-space point size at death. */
    public float endSize;
    /** Current size (updated each frame). */
    public float size;

    // Spin (degrees/second around the view axis)
    public float rotationSpeed;
    public float rotation;   // accumulated degrees

    // Lifetime
    public final float lifetime;  // total seconds
    private float age;            // seconds elapsed

    // Gravity scale (1 = full gravity, 0 = floaty)
    public float gravityScale;

    /**
     * Index into the texture atlas grid (0 = top-left sprite).
     * With a 4×4 atlas: 0–15. Defaults to 0 (solid white, particle uses vertex color).
     */
    public final int spriteIndex;

    /** Gravity constant used by update() (m/s²). */
    public static final double GRAVITY = -9.8;

    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------

    /**
     * @param position      Spawn position (copied).
     * @param velocity      Initial velocity (copied).
     * @param startColor    RGBA tint at birth (multiplied with the atlas sprite).
     * @param endColor      RGBA tint at death.
     * @param startSize     Billboard size at birth (world units).
     * @param endSize       Billboard size at death.
     * @param lifetime      Seconds until dead.
     * @param gravityScale  Fraction of gravity to apply (0–1 typical).
     * @param rotationSpeed Degrees per second of billboard spin.
     * @param spriteIndex   Sprite slot in the atlas (0 = top-left; default atlas is 4×4 = 0–15).
     */
    public Particle(Vector3d position, Vector3d velocity,
                    Color4f startColor, Color4f endColor,
                    float startSize, float endSize,
                    float lifetime, float gravityScale, float rotationSpeed,
                    int spriteIndex) {
        this.position      = new Vector3d(position);
        this.velocity      = new Vector3d(velocity);
        this.startColor    = new Color4f(startColor);
        this.endColor      = new Color4f(endColor);
        this.color         = new Color4f(startColor);
        this.startSize     = startSize;
        this.endSize       = endSize;
        this.size          = startSize;
        this.lifetime      = lifetime;
        this.gravityScale  = gravityScale;
        this.rotationSpeed = rotationSpeed;
        this.spriteIndex   = spriteIndex;
        this.age           = 0f;
    }

    /** Convenience: sprite particle, white tint fading to transparent, half gravity, no spin. */
    public Particle(Vector3d position, Vector3d velocity,
                    int spriteIndex, float size, float lifetime) {
        this(position, velocity,
             new Color4f(1f, 1f, 1f, 1f), new Color4f(1f, 1f, 1f, 0f),
             size, 0f, lifetime, 0.5f, 0f, spriteIndex);
    }

    /** Convenience: solid color, no spin, half gravity, sprite 0 (solid white = pure vertex color). */
    public Particle(Vector3d position, Vector3d velocity,
                    Color4f color, float size, float lifetime) {
        this(position, velocity, color, new Color4f(color.x, color.y, color.z, 0f),
             size, 0f, lifetime, 0.5f, 0f, 0);
    }

    // -------------------------------------------------------------------------

    /**
     * Advance this particle by {@code dt} seconds.
     * @return true if still alive, false if expired.
     */
    public boolean update(double dt) {
        age += (float) dt;
        if (age >= lifetime) return false;

        float t = age / lifetime;  // 0 → 1

        // Physics
        velocity.y += GRAVITY * gravityScale * dt;
        position.x += velocity.x * dt;
        position.y += velocity.y * dt;
        position.z += velocity.z * dt;

        // Interpolate color
        color.x = startColor.x + (endColor.x - startColor.x) * t;
        color.y = startColor.y + (endColor.y - startColor.y) * t;
        color.z = startColor.z + (endColor.z - startColor.z) * t;
        color.w = startColor.w + (endColor.w - startColor.w) * t;

        // Interpolate size
        size = startSize + (endSize - startSize) * t;

        // Rotation
        rotation += rotationSpeed * (float) dt;

        return true;
    }

    public boolean isAlive() { return age < lifetime; }

    /** 0 at birth, 1 at death. */
    public float getNormalizedAge() { return age / lifetime; }
}
