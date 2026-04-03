package physics;

/**
 * Implemented by any terrain generator that can answer "what is the ground
 * Y height at world-space (x, z)?" — used by PlayerPhysics for ground clamping.
 */
public interface TerrainHeightProvider {
    float getHeightAt(float x, float z);
}
