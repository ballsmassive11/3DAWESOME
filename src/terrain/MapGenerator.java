package terrain;

import objects.MeshObject;
import objects.TerrainMesh;
import particles.ParticleEmitter;
import physics.AABB;
import physics.TerrainHeightProvider;
import util.FastNoiseLite;
import water.WaterTile;
import world.World;

import com.sun.j3d.utils.image.TextureLoader;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Generates a continuous terrain mesh by displacing vertices of a flat grid
 * using layered (FBm) simplex noise with domain warping.
 *
 * <p>Supports two terrain types controlled by {@link #setTerrainType}:
 * <ul>
 *   <li><b>biome</b> — original ocean/sand/grass/rock/snow landscape (default)</li>
 *   <li><b>hills</b> — all-land rolling hills with river channels and streetlamps</li>
 * </ul>
 *
 * <p>The four biome textures (sand, grass, rock, snow) are blended per-vertex via a GLSL
 * shader that reads the blend weight {@code t} stored in the vertex colour alpha.
 */
public class MapGenerator implements TerrainHeightProvider {

    private static final String SHADER_DIR  = "resources/terrain/";
    private static final String LAMP_PATH   = "resources/models/StreetLamp/streetlamp.obj";
    private static final String TREE_PATH   = "resources/models/Tree/Lowpoly_tree_sample.obj";

    // Hills terrain constants
    private static final float HILLS_BASE_Y = 0.3f;   // minimum hill height (above water)
    private static final float RIVER_BOTTOM = -2.0f;  // river-bed height (below water → looks filled)
    private static final float RIVER_WIDTH  = 0.40f;  // |riverNoise| threshold for channel width
    private static final int   LAMP_SPACING = 25;     // grid cells between streetlamp sample points
    private static final double LAMP_SCALE  = 4.0;    // uniform scale applied to each lamp

    // Tree spawning constants
    private static final float TREE_DENSITY = 0.01f;  // probability of a tree per cell
    private static final double TREE_SCALE_MIN = 4.5;
    private static final double TREE_SCALE_MAX = 7.5;

    // ------------------------------------------------------------------
    // Noise generators
    // ------------------------------------------------------------------

    /** Biome terrain: domain-warp pass for organic coastlines */
    private final FastNoiseLite warpNoise;
    /** Biome terrain: main FBm height noise */
    private final FastNoiseLite noise;

    /** Hills terrain: smoother FBm for rounded rolling hills */
    private final FastNoiseLite hillsNoise;

    /** River channels: domain-warp for organic river curves */
    private final FastNoiseLite riverWarp;
    /** River channels: low-frequency FBm; rivers where |val| < RIVER_WIDTH */
    private final FastNoiseLite riverNoise;

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    private int    gridSize    = 250;
    private float  cellSize    = 0.8f;
    private float  threshold   = -0.2f;   // biome: noise value below which terrain is "under water"
    private float  heightScale = 7.0f;
    private float  zOffset     = -10.0f;  // shift terrain along -Z
    private String terrainType = "hills"; // "biome" or "hills"
    private int    currentSeed = 0;

    public MapGenerator() {
        // --- biome warp ---
        warpNoise = new FastNoiseLite();
        warpNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warpNoise.SetDomainWarpType(FastNoiseLite.DomainWarpType.OpenSimplex2Reduced);
        warpNoise.SetDomainWarpAmp(28f);
        warpNoise.SetFrequency(0.028f);
        warpNoise.SetFractalType(FastNoiseLite.FractalType.DomainWarpIndependent);
        warpNoise.SetFractalOctaves(4);

        // --- biome height ---
        noise = new FastNoiseLite();
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.022f);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFractalOctaves(5);
        noise.SetFractalLacunarity(2.0f);
        noise.SetFractalGain(0.3f);

        // --- hills height: fewer octaves, higher gain → smoother, rounder hills ---
        hillsNoise = new FastNoiseLite();
        hillsNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        hillsNoise.SetFrequency(0.018f);
        hillsNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        hillsNoise.SetFractalOctaves(4);
        hillsNoise.SetFractalLacunarity(2.0f);
        hillsNoise.SetFractalGain(0.45f);

        // --- river warp: gentle domain warp for organic curves ---
        riverWarp = new FastNoiseLite();
        riverWarp.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        riverWarp.SetDomainWarpType(FastNoiseLite.DomainWarpType.OpenSimplex2Reduced);
        riverWarp.SetDomainWarpAmp(22f);
        riverWarp.SetFrequency(0.005f);
        riverWarp.SetFractalType(FastNoiseLite.FractalType.DomainWarpIndependent);
        riverWarp.SetFractalOctaves(2);

        // --- river channels: very low frequency so rivers are long and winding ---
        riverNoise = new FastNoiseLite();
        riverNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        riverNoise.SetFrequency(0.009f);
        riverNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        riverNoise.SetFractalOctaves(2);
        riverNoise.SetFractalLacunarity(2.0f);
        riverNoise.SetFractalGain(0.5f);
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public void generate(World world) {
        int cols = (int)(gridSize / cellSize) + 1;
        int rows = cols;

        float[]   heights = new float[rows * cols];
        Color4f[] colors  = new Color4f[rows * cols];

        if ("hills".equals(terrainType)) {
            buildHillsHeights(rows, cols, heights, colors);
        } else {
            buildBiomeHeights(rows, cols, heights, colors);
        }

        // Split the terrain into chunks so Java3D's per-object 8-light limit applies
        // locally — each chunk picks the 8 nearest lamps rather than the 8 nearest
        // to the entire map's center. Chunk size ~40 world units; lamp bounds are 50.
        final int CHUNK_SIZE = 30; // cells per chunk side (chunks share border vertices)
        ShaderAppearance terrainApp = buildTerrainAppearance(); // build once, shared by all chunks
        for (int r0 = 0; r0 < rows - 1; r0 += CHUNK_SIZE - 1) {
            int chunkRows = Math.min(CHUNK_SIZE, rows - r0);
            if (chunkRows < 2) continue;
            for (int c0 = 0; c0 < cols - 1; c0 += CHUNK_SIZE - 1) {
                int chunkCols = Math.min(CHUNK_SIZE, cols - c0);
                if (chunkCols < 2) continue;
                TerrainMesh chunk = new TerrainMesh(heights, colors, rows, cols,
                        r0, c0, chunkRows, chunkCols, cellSize);
                chunk.setPosition(0, 0, zOffset);
                chunk.setAppearance(terrainApp);
                world.addObject(chunk);
            }
        }

        // Tile the water surface across the terrain using WaterTile-sized quads.
        float terrainSize = gridSize * cellSize;
        int tilesPerAxis = (int) Math.ceil(terrainSize / WaterTile.TILE_SIZE);
        float tileStart  = -(tilesPerAxis - 1) * WaterTile.TILE_SIZE / 2f;
        for (int row = 0; row < tilesPerAxis; row++) {
            for (int col = 0; col < tilesPerAxis; col++) {
                float cx = tileStart + col * WaterTile.TILE_SIZE;
                float cz = tileStart + row * WaterTile.TILE_SIZE + zOffset;
                world.addObject(new WaterTile(cx, cz));
            }
        }
        world.setTerrainProvider(this);

        if ("hills".equals(terrainType)) {
            spawnStreetlamps(world, rows, cols, heights);
        }
        spawnTrees(world, rows, cols, heights);
    }

    // ------------------------------------------------------------------
    // Biome height generation (original logic)
    // ------------------------------------------------------------------

    private void buildBiomeHeights(int rows, int cols, float[] heights, Color4f[] colors) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float nx = (c - cols / 2f) * cellSize;
                float nz = (r - rows / 2f) * cellSize;

                FastNoiseLite.Vector2 coord = new FastNoiseLite.Vector2(nx, nz);
                warpNoise.DomainWarp(coord);
                float noiseVal = noise.GetNoise(coord.x, coord.y);

                float height, blendT;
                if (noiseVal < threshold) {
                    float depth = Math.min((threshold - noiseVal) / (threshold + 1.0f), 1.0f);
                    height = -(float) Math.pow(depth, 1.5) * 4.0f;
                    blendT = 0.0f;
                } else {
                    blendT = (noiseVal - threshold) / (1.0f - threshold);
                    height = (float) Math.pow(blendT, 2.5) * heightScale;
                }

                heights[r * cols + c] = height;
                colors [r * cols + c] = new Color4f(1.0f, 1.0f, 1.0f, blendT);
            }
        }
    }

    // ------------------------------------------------------------------
    // Hills height generation
    // ------------------------------------------------------------------

    private void buildHillsHeights(int rows, int cols, float[] heights, Color4f[] colors) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float nx = (c - cols / 2f) * cellSize;
                float nz = (r - rows / 2f) * cellSize;

                // Base hills: normalize FBm output to [0, 1] and lift above water
                float noiseVal    = hillsNoise.GetNoise(nx, nz);       // ~[-1, 1]
                float normalizedH = (noiseVal + 1.0f) * 0.5f;          // [0, 1]
                float hillHeight  = HILLS_BASE_Y + normalizedH * heightScale;

                // River channels: domain-warp then check |noise| < threshold
                FastNoiseLite.Vector2 rc = new FastNoiseLite.Vector2(nx, nz);
                riverWarp.DomainWarp(rc);
                float riverVal = Math.abs(riverNoise.GetNoise(rc.x, rc.y));

                float height, blendT;
                if (riverVal < RIVER_WIDTH) {
                    // Smoothstep blend: terrain dips from hillHeight down to RIVER_BOTTOM
                    float t     = riverVal / RIVER_WIDTH;
                    float blend = t * t * (3.0f - 2.0f * t);               // smoothstep [0, 1]
                    height  = RIVER_BOTTOM + (hillHeight - RIVER_BOTTOM) * blend;
                    // For river banks, we use sand (t < 0.12). 
                    // Lowering normalizedH factor to ensure it stays sandy even at high terrain.
                    blendT  = blend * normalizedH * 0.15f;
                } else {
                    height = hillHeight;
                    // Transitions: grass [0.12, 0.50] rock [0.50, 0.78] snow [0.78, 1.0]
                    // We want it to be "quite high" before transitioning to rock (0.12).
                    // If we use normalizedH * 0.20, then rock starts at normalizedH = 0.12 / 0.20 = 0.60 (60% height).
                    blendT = Math.min(normalizedH * 0.20f, 0.65f);
                }

                heights[r * cols + c] = height;
                colors [r * cols + c] = new Color4f(1.0f, 1.0f, 1.0f, blendT);
            }
        }
    }

    // ------------------------------------------------------------------
    // Streetlamp spawning (hills only)
    // ------------------------------------------------------------------

    private void spawnStreetlamps(World world, int rows, int cols, float[] heights) {
        Random rng  = new Random(currentSeed + 42L);
        int    half = LAMP_SPACING / 2;

        for (int r = LAMP_SPACING; r < rows - LAMP_SPACING; r += LAMP_SPACING) {
            for (int c = LAMP_SPACING; c < cols - LAMP_SPACING; c += LAMP_SPACING) {
                // Jitter the sample point within the spacing cell so lamps aren't on a grid
                int jr = r + rng.nextInt(LAMP_SPACING) - half;
                int jc = c + rng.nextInt(LAMP_SPACING) - half;
                jr = Math.max(0, Math.min(rows - 1, jr));
                jc = Math.max(0, Math.min(cols - 1, jc));

                float height = heights[jr * cols + jc];

                // Skip cells that are too close to a river channel
                float nx = (jc - cols / 2f) * cellSize;
                float nz = (jr - rows / 2f) * cellSize;
                FastNoiseLite.Vector2 rc = new FastNoiseLite.Vector2(nx, nz);
                riverWarp.DomainWarp(rc);
                if (Math.abs(riverNoise.GetNoise(rc.x, rc.y)) < RIVER_WIDTH * 1.5f) continue;

                MeshObject lamp = new MeshObject(LAMP_PATH, true);
                lamp.setCollidable(false);
                lamp.setScale(LAMP_SCALE);
                float lampY = height + 3.5f;
                lamp.setPosition(nx, lampY, nz + zOffset);
                world.addObject(lamp);

                // Point light near the lamp head (offset up from base by ~10 world units)
                float lightY = lampY + 10.0f;
                float lightZ = (float)(nz + zOffset);
                PointLight pl = new PointLight(
                    new Color3f(1.0f, 0.80f, 0.35f),                    // warm amber / sodium lamp
                    new Point3f(nx, lightY, lightZ),
                    new Point3f(0.002f, 0.01f, 0.007f)                     // constant, linear, quadratic — fast falloff
                );
                pl.setInfluencingBounds(new BoundingSphere(new Point3d(nx, lightY, lightZ), 20.0));
                world.addPointLight(pl);
            }
        }
    }

    private void spawnTrees(World world, int rows, int cols, float[] heights) {
        Random rng = new Random(currentSeed + 99L);
        int treesPlaced = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (rng.nextFloat() > TREE_DENSITY) continue;

                float height = heights[r * cols + c];
                
                // Avoid placing trees underwater
                if (height < 0.2f) continue;

                float nx = (c - cols / 2f) * cellSize;
                float nz = (r - rows / 2f) * cellSize;

                // For hills terrain, avoid river channels
                if ("hills".equals(terrainType)) {
                    FastNoiseLite.Vector2 rc = new FastNoiseLite.Vector2(nx, nz);
                    riverWarp.DomainWarp(rc);
                    if (Math.abs(riverNoise.GetNoise(rc.x, rc.y)) < RIVER_WIDTH * 1.0f) continue;
                }

                MeshObject tree = new MeshObject(TREE_PATH, true);
                tree.setCollidable(true);
                double scale = TREE_SCALE_MIN + (TREE_SCALE_MAX - TREE_SCALE_MIN) * rng.nextDouble();
                tree.setScale(scale);
                tree.setPosition(nx, height, nz + zOffset);
                // AABB in model space: trunk radius ±0.25, base at pivot y (-0.903), top at y=2.5
                float radius = 0.08f;
                Vector3d pivot = tree.getPivot();
                tree.setLocalAABB(new AABB(
                        (float)pivot.x - radius, (float)pivot.y, (float)pivot.z - radius,
                        (float)pivot.x + radius, 0.0f,           (float)pivot.z + radius
                ));
                // Random rotation (yaw)
                tree.setRotationEuler(0, rng.nextFloat() * Math.PI*2, 0);
                
                world.addObject(tree);

                // Leaf particles drifting down from the canopy
                double canopyY = height + scale * 1.8;
                float canopyRadius = (float)(scale * 0.55f);
                world.addEmitter(new ParticleEmitter(nx, canopyY-0.3, nz + zOffset)
                        .setSpawnMode(ParticleEmitter.SpawnMode.BRICK)
                        .setBrickSize(canopyRadius * 2, canopyRadius * 0.4f, canopyRadius * 2)
                        .setEmissionRate(1.0)
                        .setPitch(-Math.PI / 2)
                        .setSpread(0.6)
                        .setSpeed(0.5)
                        .setStartColor(new Color4f(1f, 1f, 1f, 0.9f))
                        .setEndColor(new Color4f(1f, 1f, 1f, 0f))
                        .setStartSize((float)(scale * 0.12f))
                        .setEndSize((float)(scale * 0.06f))
                        .setLifetime(10.0f)
                        .setGravityScale(0.04f)
                        .setRotationSpeed((float)(Math.random()*120f-60f))
                        .setAtlasPath("resources/particles/leaf.png"));

                treesPlaced++;
            }
        }
    }

    // ------------------------------------------------------------------
    // Shader appearance builder
    // ------------------------------------------------------------------

    private ShaderAppearance buildTerrainAppearance() {
        ShaderAppearance app = new ShaderAppearance();

        Material mat = new Material();
        mat.setLightingEnable(true);
        mat.setAmbientColor (new Color3f(0.65f, 0.65f, 0.65f));
        mat.setDiffuseColor (new Color3f(1.0f,  1.0f,  1.0f));
        mat.setSpecularColor(new Color3f(0.08f, 0.08f, 0.08f));
        mat.setShininess(18f);
        app.setMaterial(mat);

        String[] texPaths = {
            SHADER_DIR + "sand.jpg",
            SHADER_DIR + "grass.jpg",
            SHADER_DIR + "rock.jpg",
            SHADER_DIR + "snow.jpg"
        };
        TextureUnitState[] tus = new TextureUnitState[texPaths.length];
        for (int i = 0; i < texPaths.length; i++) {
            tus[i] = new TextureUnitState();
            TextureLoader tl = new TextureLoader(texPaths[i], null);
            Texture2D tex = (Texture2D) tl.getTexture();
            if (tex != null) {
                tex.setBoundaryModeS(Texture.WRAP);
                tex.setBoundaryModeT(Texture.WRAP);
                tex.setMinFilter(Texture.MULTI_LEVEL_LINEAR);
                tex.setMagFilter(Texture.BASE_LEVEL_LINEAR);
                tus[i].setTexture(tex);
            } else {
                System.err.println("Warning: could not load terrain texture: " + texPaths[i]);
            }
        }
        app.setTextureUnitState(tus);

        try {
            String vertSrc = new String(Files.readAllBytes(Paths.get(SHADER_DIR + "terrain.vert")));
            String fragSrc = new String(Files.readAllBytes(Paths.get(SHADER_DIR + "terrain.frag")));

            SourceCodeShader vs = new SourceCodeShader(
                    Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_VERTEX,   vertSrc);
            SourceCodeShader fs = new SourceCodeShader(
                    Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_FRAGMENT, fragSrc);

            GLSLShaderProgram program = new GLSLShaderProgram();
            program.setShaders(new Shader[] { vs, fs });
            program.setShaderAttrNames(new String[] { "sandTex", "grassTex", "rockTex", "snowTex" });
            app.setShaderProgram(program);

            ShaderAttributeSet attrs = new ShaderAttributeSet();
            attrs.put(new ShaderAttributeValue("sandTex",  new Integer(0)));
            attrs.put(new ShaderAttributeValue("grassTex", new Integer(1)));
            attrs.put(new ShaderAttributeValue("rockTex",  new Integer(2)));
            attrs.put(new ShaderAttributeValue("snowTex",  new Integer(3)));
            app.setShaderAttributeSet(attrs);

        } catch (IOException e) {
            System.err.println("Could not load terrain shaders: " + e.getMessage());
        }

        return app;
    }

    // ------------------------------------------------------------------
    // TerrainHeightProvider
    // ------------------------------------------------------------------

    @Override
    public float getHeightAt(float wx, float wz) {
        float nx = wx;
        float nz = wz - zOffset;
        return "hills".equals(terrainType) ? getHillsHeightAt(nx, nz) : getBiomeHeightAt(nx, nz);
    }

    private float getBiomeHeightAt(float nx, float nz) {
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

    private float getHillsHeightAt(float nx, float nz) {
        float noiseVal    = hillsNoise.GetNoise(nx, nz);
        float normalizedH = (noiseVal + 1.0f) * 0.5f;
        float hillHeight  = HILLS_BASE_Y + normalizedH * heightScale;

        FastNoiseLite.Vector2 rc = new FastNoiseLite.Vector2(nx, nz);
        riverWarp.DomainWarp(rc);
        float riverVal = Math.abs(riverNoise.GetNoise(rc.x, rc.y));

        if (riverVal < RIVER_WIDTH) {
            float t     = riverVal / RIVER_WIDTH;
            float blend = t * t * (3.0f - 2.0f * t);
            return RIVER_BOTTOM + (hillHeight - RIVER_BOTTOM) * blend;
        }
        return hillHeight;
    }

    // ------------------------------------------------------------------
    // Configuration setters
    // ------------------------------------------------------------------

    public void setSeed(int seed) {
        this.currentSeed = seed;
        noise.SetSeed(seed);
        warpNoise.SetSeed(seed);
        hillsNoise.SetSeed(seed);
        riverNoise.SetSeed(seed + 1337);
        riverWarp.SetSeed(seed + 2674);
    }

    public void setTerrainType(String type)    { this.terrainType = type;      }
    public void setGridSize(int gridSize)      { this.gridSize    = gridSize;  }
    public void setCellSize(float cellSize)    { this.cellSize    = cellSize;  }
    public void setThreshold(float threshold)  { this.threshold   = threshold; }
    public void setHeightScale(float hs)       { this.heightScale = hs;        }
    public void setZOffset(float zOffset)      { this.zOffset     = zOffset;   }
}
