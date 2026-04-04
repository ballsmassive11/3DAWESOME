package physics;

/**
 * A vertical capsule (pillbox): a cylinder capped with hemispheres at both ends.
 *
 * Defined by a radius and a total height (must be >= 2 * radius).
 * The cylindrical spine runs from {@link #spineBot} to {@link #spineTop},
 * with each endpoint being the centre of a hemispherical cap.
 *
 * When used as a player shape the bottom edge sits at foot level and the top
 * at eye level, so {@code height == EYE_HEIGHT} and the spine runs from
 * {@code feetY + radius} to {@code eyeY - radius}.
 */
public final class Pillbox {

    public final float radius;
    public final float height; // total height including both hemispherical caps

    public Pillbox(float radius, float height) {
        this.radius = radius;
        this.height = Math.max(height, 2f * radius);
    }

    /** Y of the bottom hemisphere centre, given the capsule's bottom-edge Y. */
    public float spineBot(float feetY) { return feetY + radius; }

    /** Y of the top hemisphere centre, given the capsule's bottom-edge Y. */
    public float spineTop(float feetY) { return feetY + height - radius; }
}
