package terrain;

import java.util.Objects;

/**
 * Immutable integer key identifying a terrain chunk on the XZ grid.
 * Chunk {@code (x, z)} covers world positions
 * {@code [x * CHUNK_SIZE_WORLD, (x+1) * CHUNK_SIZE_WORLD)} on the X axis and
 * {@code [z * CHUNK_SIZE_WORLD, (z+1) * CHUNK_SIZE_WORLD)} on the Z axis.
 */
public final class ChunkCoord {
    public final int x;
    public final int z;

    public ChunkCoord(int x, int z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkCoord)) return false;
        ChunkCoord c = (ChunkCoord) o;
        return x == c.x && z == c.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return "Chunk(" + x + ", " + z + ")";
    }
}
