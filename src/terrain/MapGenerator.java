package terrain;

import objects.Brick;
import objects.TerrainMesh;
import physics.TerrainHeightProvider;
import util.FastNoiseLite;
import world.World;

import javax.media.j3d.*;
import javax.vecmath.*;

/**
 * Generates a continuous terrain mesh by displacing vertices of a flat grid
 * using layered (FBm) simplex noise with domain warping.
 * <p>
 * Unlike the legacy brick-based generator, the ground here is a single
 * triangulated surface — like a sheet of paper that rises and falls.
 * Per-vertex colours carry the height gradient (sand → grass → rock → snow).
 */
public class MapGenerator implements TerrainHeightProvider {

    private final FastNoiseLite noise;
    private final FastNoiseLite warpNoise;

    private int   gridSize    = 160;
    private float cellSize    = 0.8f;
    private float threshold   = -0.2f;   // noise value below which terrain is "under water"
    private float heightScale = 16.0f;
    private float zOffset     = -10.0f;  // shift the whole terrain along -Z

    public MapGenerator() {
        // Domain-warp pass: twists sample coordinates to produce organic coastlines
        warpNoise = new FastNoiseLite();
        warpNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warpNoise.SetDomainWarpType(FastNoiseLite.DomainWarpType.OpenSimplex2Reduced);
        warpNoise.SetDomainWarpAmp(28f);
        warpNoise.SetFrequency(0.028f);
        warpNoise.SetFractalType(FastNoiseLite.FractalType.DomainWarpIndependent);
        warpNoise.SetFractalOctaves(4);

        // Main terrain: FBm for natural fractal detail
        noise = new FastNoiseLite();
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.022f);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFractalOctaves(5);
        noise.SetFractalLacunarity(2.0f);
        noise.SetFractalGain(0.3f);
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public void generate(World world) {
        int cols = (int) (gridSize / cellSize) + 1;
        int rows = cols;

        float[]   heights = new float[rows * cols];
        Color3f[] colors  = new Color3f[rows * cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float nx = (c - cols / 2f) * cellSize;
                float nz = (r - rows / 2f) * cellSize;

                // Domain-warp the sample coordinates for organic shapes
                FastNoiseLite.Vector2 coord = new FastNoiseLite.Vector2(nx, nz);
                warpNoise.DomainWarp(coord);

                float noiseVal = noise.GetNoise(coord.x, coord.y); // [-1, 1]

                float   height;
                Color3f diffuse;

                if (noiseVal < threshold) {
                    // Sub-water: sand slopes gently downward from shore
                    float depth = Math.min((threshold - noiseVal) / (threshold + 1.0f), 1.0f);
                    height  = -(float) Math.pow(depth, 1.5) * 4.0f;
                    diffuse = lerp(new Color3f(0.75f, 0.68f, 0.42f),
                                   new Color3f(0.30f, 0.26f, 0.10f), depth);
                } else {
                    // Above water: t=0 is shore, t=1 is peak
                    float t = (noiseVal - threshold) / (1.0f - threshold);
                    height  = (float) Math.pow(t, 2.5) * heightScale;
                    diffuse = terrainColor(t);
                }

                heights[r * cols + c] = height;
                colors [r * cols + c] = diffuse;
            }
        }

        // Build the terrain mesh and configure its appearance
        TerrainMesh terrain = new TerrainMesh(heights, colors, rows, cols, cellSize);
        terrain.setPosition(0, 0, zOffset);

        Appearance terrainApp = terrain.getAppearance();
        Material   terrainMat = new Material();
        terrainMat.setLightingEnable(true);
        // Vertex colours provide the diffuse contribution; keep ambient dim
        terrainMat.setAmbientColor (new Color3f(0.25f, 0.25f, 0.25f));
        terrainMat.setDiffuseColor (new Color3f(1.0f,  1.0f,  1.0f));   // overridden by vertex colour
        terrainMat.setSpecularColor(new Color3f(0.08f, 0.08f, 0.08f));
        terrainMat.setShininess(18f);
        terrainApp.setMaterial(terrainMat);
        terrain.setAppearance(terrainApp);

        world.addObject(terrain);

        // Transparent water plane — same as legacy setup
        Brick water = new Brick(gridSize, 80f, gridSize);
        water.setCollidable(false);   // purely visual — player should not collide with the water box
        water.setPosition(0, -35.1f, zOffset);

        Appearance waterApp = water.getAppearance();
        Material   waterMat = new Material();
        waterMat.setAmbientColor (new Color3f(0.00f, 0.05f, 0.40f));
        waterMat.setDiffuseColor (new Color3f(0.00f, 0.10f, 0.55f));
        waterMat.setSpecularColor(new Color3f(0.40f, 0.60f, 0.90f));
        waterMat.setShininess(80f);
        waterApp.setMaterial(waterMat);
        waterApp.setTransparencyAttributes(
                new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.5f));
        water.setAppearance(waterApp);

        world.addObject(water);
        world.setWaterHandler(new WaterHandlerLegacy(water, -40.1f));

        world.setTerrainProvider(this);
    }

    // ------------------------------------------------------------------
    // TerrainHeightProvider
    // ------------------------------------------------------------------

    /**
     * Returns the terrain Y height (world-space) at (wx, wz).
     * Mirrors the height formula used during mesh generation so the physics
     * system can query the exact ground level without touching the geometry.
     */
    @Override
    public float getHeightAt(float wx, float wz) {
        // Undo the terrain's world-space Z offset to get noise-space coordinates
        float nx = wx;
        float nz = wz - zOffset;

        FastNoiseLite.Vector2 coord = new FastNoiseLite.Vector2(nx, nz);
        warpNoise.DomainWarp(coord);
        float noiseVal = noise.GetNoise(coord.x, coord.y);

        if (noiseVal < threshold) {
            float depth = Math.min((threshold - noiseVal) / (threshold + 1.0f), 1.0f);
            return -(float) Math.pow(depth, 1.5) * 4.0f;
        } else {
            float t = (noiseVal - threshold) / (1.0f - threshold);
            return (float) Math.pow(t, 2.5) * heightScale;
        }
    }

    // ------------------------------------------------------------------
    // Colour gradient helpers
    // ------------------------------------------------------------------

    /** Height-based colour: sand → grass → forest → rock → snow */
    private Color3f terrainColor(float t) {
        if (t < 0.12f) {
            return lerp(new Color3f(0.78f, 0.71f, 0.44f),
                        new Color3f(0.55f, 0.76f, 0.28f), t / 0.12f);
        } else if (t < 0.50f) {
            return lerp(new Color3f(0.55f, 0.76f, 0.28f),
                        new Color3f(0.18f, 0.50f, 0.12f), (t - 0.12f) / 0.38f);
        } else if (t < 0.78f) {
            return lerp(new Color3f(0.18f, 0.50f, 0.12f),
                        new Color3f(0.54f, 0.41f, 0.36f), (t - 0.50f) / 0.28f);
        } else {
            float blend = Math.min((t - 0.78f) / 0.22f, 1.0f);
            return lerp(new Color3f(0.44f, 0.41f, 0.36f),
                        new Color3f(0.82f, 0.82f, 0.85f), blend);
        }
    }

    private static Color3f lerp(Color3f a, Color3f b, float t) {
        return new Color3f(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    // ------------------------------------------------------------------
    // Configuration setters
    // ------------------------------------------------------------------

    public void setSeed(int seed)              { noise.SetSeed(seed); warpNoise.SetSeed(seed); }
    public void setGridSize(int gridSize)      { this.gridSize    = gridSize;    }
    public void setCellSize(float cellSize)    { this.cellSize    = cellSize;    }
    public void setThreshold(float threshold)  { this.threshold   = threshold;  }
    public void setHeightScale(float hs)       { this.heightScale = hs;         }
    public void setZOffset(float zOffset)      { this.zOffset     = zOffset;    }
}
