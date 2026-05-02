package physics;

/**
 * Immutable axis-aligned bounding box.
 *
 * Coordinates are always in world space. The standard construction pattern is
 * to define a LOCAL aabb (centred at the object's origin) and call
 * {@link #translate} each frame to get the world-space version.
 */
public final class AABB {

    public final float minX, minY, minZ;
    public final float maxX, maxY, maxZ;

    /** Local AABB centred at the origin with the given half-extents. */
    public AABB(float halfX, float halfY, float halfZ) {
        this(-halfX, -halfY, -halfZ, halfX, halfY, halfZ);
    }

    /** Local AABB with the given half-extents, offset from the origin. */
    public static AABB offset(float hX, float hY, float hZ, float oX, float oY, float oZ) {
        return new AABB(oX - hX, oY - hY, oZ - hZ, oX + hX, oY + hY, oZ + hZ);
    }

    /** Explicit min/max constructor (world or local). */
    public AABB(float minX, float minY, float minZ,
                float maxX, float maxY, float maxZ) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    /** Returns a new AABB shifted by (dx, dy, dz). */
    public AABB translate(float dx, float dy, float dz) {
        return new AABB(minX + dx, minY + dy, minZ + dz,
                        maxX + dx, maxY + dy, maxZ + dz);
    }

    /** True if this AABB overlaps another (touching edges do not count). */
    public boolean overlaps(AABB o) {
        return maxX > o.minX && minX < o.maxX
            && maxY > o.minY && minY < o.maxY
            && maxZ > o.minZ && minZ < o.maxZ;
    }

    public float centerX() { return (minX + maxX) * 0.5f; }
    public float centerY() { return (minY + maxY) * 0.5f; }
    public float centerZ() { return (minZ + maxZ) * 0.5f; }

    public float sizeX() { return maxX - minX; }
    public float sizeY() { return maxY - minY; }
    public float sizeZ() { return maxZ - minZ; }
}
