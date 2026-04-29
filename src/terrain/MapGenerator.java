package terrain;

import entity.EntityPhysics;
import objects.MeshObject;
import objects.TerrainMesh;
import particles.ParticleEmitter;
import physics.AABB;
import physics.TerrainHeightProvider;
import util.FastNoiseLite;
import util.ProgressReporter;
import water.WaterTile;
import world.World;
import world.WorldBorder;

import com.sun.j3d.utils.image.TextureLoader;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

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

    // Shared GPU resources for terrain chunks – loaded once, reused per-chunk.
    private Texture2D[]        terrainTextures;
    private GLSLShaderProgram  terrainShaderProgram;
    private ShaderAttributeSet terrainShaderAttrs;
    // Single shared appearance – all chunks reference the same instance.
    // After the first chunk makes it live, subsequent chunks reuse the already-live
    // NodeComponent so Java3D skips full initialization (reference-count bump only).
    private ShaderAppearance   cachedChunkAppearance;

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
    private float  zOffset     = 0.0f;  // chunk system uses absolute world coordinates; keep at 0
    private String terrainType = "hills"; // "biome" or "hills"
    private int    currentSeed = 0;
    private ProgressReporter reporter;

    public MapGenerator() {
        // --- biome warp ---
        warpNoise = new FastNoiseLite();
        warpNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        warpNoise.SetDomainWarpType(FastNoiseLite.DomainWarpType.OpenSimplex2Reduced);
        warpNoise.SetDomainWarpAmp(28f);
        warpNoise.SetFrequency(0.028f);
        warpNoise.SetFractalType(FastNoiseLite.FractalType.DomainWarpIndependent);
        warpNoise.SetFractalOctaves(3);

        // --- biome height ---
        noise = new FastNoiseLite();
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noise.SetFrequency(0.022f);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFractalOctaves(5);
        noise.SetFractalLacunarity(2.0f);
        noise.SetFractalGain(0.3f);

        // --- hills height: fewer octaves, higher gain → smoother, rounder hills ---
        hillsNoise = new FastNoiseLite();
        hillsNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        hillsNoise.SetFrequency(0.018f);
        hillsNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        hillsNoise.SetFractalOctaves(3);
        hillsNoise.SetFractalLacunarity(2.0f);
        hillsNoise.SetFractalGain(0.45f);

        // --- river warp: gentle domain warp for organic curves ---
        riverWarp = new FastNoiseLite();
        riverWarp.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        riverWarp.SetDomainWarpType(FastNoiseLite.DomainWarpType.OpenSimplex2Reduced);
        riverWarp.SetDomainWarpAmp(22f);
        riverWarp.SetFrequency(0.005f);
        riverWarp.SetFractalType(FastNoiseLite.FractalType.DomainWarpIndependent);
        riverWarp.SetFractalOctaves(2);

        // --- river channels: very low frequency so rivers are long and winding ---
        riverNoise = new FastNoiseLite();
        riverNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        riverNoise.SetFrequency(0.009f);
        riverNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        riverNoise.SetFractalOctaves(2);
        riverNoise.SetFractalLacunarity(2.0f);
        riverNoise.SetFractalGain(0.5f);
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public void setReporter(ProgressReporter reporter) {
        this.reporter = reporter;
    }

    private void report(float progress, String status) {
        if (reporter != null) {
            reporter.report(progress, status);
        }
    }

    public void generate(World world) {
        report(0.0f, "Initialising terrain...");

        // Shut down any previous chunk manager attached to this world before replacing it.
        if (world.getChunkManager() != null) {
            world.getChunkManager().shutdown();
        }

        buildTerrainAppearance(); // loads textures and shader into terrainTextures/terrainShaderProgram/terrainShaderAttrs
        world.setTerrainProvider(this);

        // Create the chunk manager and do a synchronous preload of the area around spawn.
        report(0.05f, "Creating chunk manager...");
        ChunkManager cm = new ChunkManager(this, world);
        world.setChunkManager(cm);

        report(0.1f, "Preloading terrain chunks around spawn...");
        javax.vecmath.Vector3d spawn = world.getPlayer().getPosition();
        cm.preload(spawn, 5, reporter);   // radius 5 → up to 81 chunks synchronously

        report(0.8f, "Creating world border...");
        WorldBorder border = new WorldBorder(ChunkManager.BORDER_RADIUS, world);
        world.setWorldBorder(border);

        // Place player safely on ground
        float groundY = getHeightAt((float) spawn.x, (float) spawn.z);
        world.getPlayer().getPosition().y = groundY + EntityPhysics.EYE_HEIGHT;
    }

    // ------------------------------------------------------------------
    // Biome height generation (original logic)
    // ------------------------------------------------------------------

    private void buildBiomeHeights(int rows, int cols, float[] heights, float[] colors) {
        report(0.1f, "Calculating biome heights...");
        float halfCols = cols / 2.0f;
        float halfRows = rows / 2.0f;

        IntStream.range(0, rows).parallel().forEach(r -> {
            float nz = (r - halfRows) * cellSize;
            FastNoiseLite.Vector2 coord = new FastNoiseLite.Vector2(0f, 0f);
            int rowIdx = r * cols;

            for (int c = 0; c < cols; c++) {
                float nx = (c - halfCols) * cellSize;
                int idx = rowIdx + c;

                coord.x = nx; coord.y = nz;
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

                heights[idx] = height;
                int cIdx = idx * 4;
                colors[cIdx    ] = 1.0f;
                colors[cIdx + 1] = 1.0f;
                colors[cIdx + 2] = 1.0f;
                colors[cIdx + 3] = blendT;
            }
        });
    }

    // ------------------------------------------------------------------
    // Hills height generation
    // ------------------------------------------------------------------

    private void buildHillsHeights(int rows, int cols, float[] heights, float[] colors, float[] riverVals) {
        report(0.1f, "Calculating hill heights...");
        float halfCols = cols / 2.0f;
        float halfRows = rows / 2.0f;

        IntStream.range(0, rows).parallel().forEach(r -> {
            float nz = (r - halfRows) * cellSize;
            FastNoiseLite.Vector2 rc = new FastNoiseLite.Vector2(0f, 0f);
            int rowIdx = r * cols;

            for (int c = 0; c < cols; c++) {
                float nx = (c - halfCols) * cellSize;
                int idx = rowIdx + c;

                // Base hills: normalize FBm output to [0, 1] and lift above water
                float noiseVal    = hillsNoise.GetNoise(nx, nz);       // ~[-1, 1]
                float normalizedH = (noiseVal + 1.0f) * 0.5f;          // [0, 1]
                float hillHeight  = HILLS_BASE_Y + normalizedH * heightScale;

                // River channels: domain-warp then check |noise| < threshold
                rc.x = nx; rc.y = nz;
                riverWarp.DomainWarp(rc);
                float riverVal = Math.abs(riverNoise.GetNoise(rc.x, rc.y));
                riverVals[idx] = riverVal; // cache for lamp/tree spawning

                float height, blendT;
                if (riverVal < RIVER_WIDTH) {
                    // Smoothstep blend: terrain dips from hillHeight down to RIVER_BOTTOM
                    float t     = riverVal / RIVER_WIDTH;
                    float blend = t * t * (3.0f - 2.0f * t);               // smoothstep [0, 1]
                    height  = RIVER_BOTTOM + (hillHeight - RIVER_BOTTOM) * blend;
                    blendT  = blend * normalizedH * 0.15f;
                } else {
                    height = hillHeight;
                    blendT = Math.min(normalizedH * 0.20f, 0.65f);
                }

                heights[idx] = height;
                int cIdx = idx * 4;
                colors[cIdx    ] = 1.0f;
                colors[cIdx + 1] = 1.0f;
                colors[cIdx + 2] = 1.0f;
                colors[cIdx + 3] = blendT;
            }
        });
    }

    // ------------------------------------------------------------------
    // Per-chunk data generation (used by ChunkManager)
    // ------------------------------------------------------------------

    /**
     * Fills {@code heights} and {@code colors} (and optionally {@code riverVals}) for a
     * standalone {@code n × n} vertex grid whose (0,0) vertex sits at world position
     * {@code (wx0, wz0)}.  Adjacent chunks built with matching offsets share the same
     * border heights and therefore tile seamlessly.
     *
     * @param wx0       World X of the chunk's (row=0, col=0) vertex.
     * @param wz0       World Z of the chunk's (row=0, col=0) vertex.
     * @param n         Number of vertices per side (so {@code n-1} cells per side).
     * @param heights   Output array of size {@code n*n}.
     * @param colors    Output array of size {@code n*n*4} (RGBA per vertex).
     * @param riverVals Output array of size {@code n*n}, or {@code null} for biome terrain.
     */
    public void buildChunkData(float wx0, float wz0, int n, float chunkCellSize,
                               float[] heights, float[] colors, float[] riverVals) {
        if (isHillsTerrain()) {
            buildHillsChunk(wx0, wz0, n, chunkCellSize, heights, colors, riverVals);
        } else {
            buildBiomeChunk(wx0, wz0, n, chunkCellSize, heights, colors);
        }
    }

    private void buildHillsChunk(float wx0, float wz0, int n, float chunkCellSize,
                                  float[] heights, float[] colors, float[] riverVals) {
        IntStream.range(0, n).parallel().forEach(r -> {
            float nz = wz0 + r * chunkCellSize;
            FastNoiseLite.Vector2 rc = new FastNoiseLite.Vector2(0f, 0f);
            int rowIdx = r * n;

            for (int c = 0; c < n; c++) {
                float nx  = wx0 + c * chunkCellSize;
                int   idx = rowIdx + c;

                float noiseVal    = hillsNoise.GetNoise(nx, nz);
                float normalizedH = (noiseVal + 1.0f) * 0.5f;
                float hillHeight  = HILLS_BASE_Y + normalizedH * heightScale;

                rc.x = nx; rc.y = nz;
                riverWarp.DomainWarp(rc);
                float riverVal = Math.abs(riverNoise.GetNoise(rc.x, rc.y));
                if (riverVals != null) riverVals[idx] = riverVal;

                float height, blendT;
                if (riverVal < RIVER_WIDTH) {
                    float t     = riverVal / RIVER_WIDTH;
                    float blend = t * t * (3.0f - 2.0f * t);
                    height  = RIVER_BOTTOM + (hillHeight - RIVER_BOTTOM) * blend;
                    blendT  = blend * normalizedH * 0.15f;
                } else {
                    height = hillHeight;
                    blendT = Math.min(normalizedH * 0.20f, 0.65f);
                }

                heights[idx] = height;
                int cIdx = idx * 4;
                colors[cIdx    ] = 1.0f;
                colors[cIdx + 1] = 1.0f;
                colors[cIdx + 2] = 1.0f;
                colors[cIdx + 3] = blendT;
            }
        });
    }

    private void buildBiomeChunk(float wx0, float wz0, int n, float chunkCellSize,
                                  float[] heights, float[] colors) {
        IntStream.range(0, n).parallel().forEach(r -> {
            float nz = wz0 + r * chunkCellSize;
            FastNoiseLite.Vector2 coord = new FastNoiseLite.Vector2(0f, 0f);
            int rowIdx = r * n;

            for (int c = 0; c < n; c++) {
                float nx  = wx0 + c * chunkCellSize;
                int   idx = rowIdx + c;

                coord.x = nx; coord.y = nz;
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

                heights[idx] = height;
                int cIdx = idx * 4;
                colors[cIdx    ] = 1.0f;
                colors[cIdx + 1] = 1.0f;
                colors[cIdx + 2] = 1.0f;
                colors[cIdx + 3] = blendT;
            }
        });
    }

    public boolean isHillsTerrain() { return "hills".equals(terrainType); }

    // ------------------------------------------------------------------
    // Streetlamp spawning (hills only)
    // ------------------------------------------------------------------

    private void spawnStreetlamps(World world, int rows, int cols, float[] heights, float[] riverVals) {
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

                // Skip cells that are too close to a river channel (use cached value)
                float nx = (jc - cols / 2f) * cellSize;
                float nz = (jr - rows / 2f) * cellSize;
                if (riverVals[jr * cols + jc] < RIVER_WIDTH * 1.5f) continue;

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

    private void spawnTrees(World world, int rows, int cols, float[] heights, float[] riverVals) {
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

                // For hills terrain, avoid river channels (use cached value)
                if (riverVals != null && riverVals[r * cols + c] < RIVER_WIDTH * 1.0f) continue;

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

    ShaderAppearance buildTerrainAppearance() {
        String[] texPaths = {
            SHADER_DIR + "sand.jpg",
            SHADER_DIR + "grass.jpg",
            SHADER_DIR + "rock.jpg",
            SHADER_DIR + "snow.jpg"
        };
        terrainTextures = new Texture2D[texPaths.length];
        for (int i = 0; i < texPaths.length; i++) {
            TextureLoader tl = new TextureLoader(texPaths[i], null);
            Texture2D tex = (Texture2D) tl.getTexture();
            if (tex != null) {
                tex.setBoundaryModeS(Texture.WRAP);
                tex.setBoundaryModeT(Texture.WRAP);
                tex.setMinFilter(Texture.MULTI_LEVEL_LINEAR);
                tex.setMagFilter(Texture.BASE_LEVEL_LINEAR);
            } else {
                System.err.println("Warning: could not load terrain texture: " + texPaths[i]);
            }
            terrainTextures[i] = tex;
        }

        try {
            String vertSrc = new String(Files.readAllBytes(Paths.get(SHADER_DIR + "terrain.vert")));
            String fragSrc = new String(Files.readAllBytes(Paths.get(SHADER_DIR + "terrain.frag")));

            SourceCodeShader vs = new SourceCodeShader(
                    Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_VERTEX,   vertSrc);
            SourceCodeShader fs = new SourceCodeShader(
                    Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_FRAGMENT, fragSrc);

            terrainShaderProgram = new GLSLShaderProgram();
            terrainShaderProgram.setShaders(new Shader[] { vs, fs });
            terrainShaderProgram.setShaderAttrNames(new String[] { "sandTex", "grassTex", "rockTex", "snowTex" });

            terrainShaderAttrs = new ShaderAttributeSet();
            terrainShaderAttrs.put(new ShaderAttributeValue("sandTex",  new Integer(0)));
            terrainShaderAttrs.put(new ShaderAttributeValue("grassTex", new Integer(1)));
            terrainShaderAttrs.put(new ShaderAttributeValue("rockTex",  new Integer(2)));
            terrainShaderAttrs.put(new ShaderAttributeValue("snowTex",  new Integer(3)));

        } catch (IOException e) {
            System.err.println("Could not load terrain shaders: " + e.getMessage());
        }

        return createChunkAppearance();
    }

    /**
     * Returns the shared {@link ShaderAppearance} for terrain chunks.
     * <p>
     * A single instance is created lazily and reused by every chunk.  Once the
     * first chunk makes it live, Java3D only needs a reference-count bump for
     * subsequent chunks — no full shader/texture initialisation per chunk.
     */
    ShaderAppearance createChunkAppearance() {
        if (cachedChunkAppearance != null) return cachedChunkAppearance;
        ShaderAppearance app = new ShaderAppearance();

        Material mat = new Material();
        mat.setLightingEnable(true);
        mat.setAmbientColor (new Color3f(0.65f, 0.65f, 0.65f));
        mat.setDiffuseColor (new Color3f(1.0f,  1.0f,  1.0f));
        mat.setSpecularColor(new Color3f(0.08f, 0.08f, 0.08f));
        mat.setShininess(18f);
        app.setMaterial(mat);

        if (terrainTextures != null) {
            TextureUnitState[] tus = new TextureUnitState[terrainTextures.length];
            for (int i = 0; i < terrainTextures.length; i++) {
                tus[i] = new TextureUnitState();
                if (terrainTextures[i] != null) tus[i].setTexture(terrainTextures[i]);
            }
            app.setTextureUnitState(tus);
        }

        if (terrainShaderProgram != null) app.setShaderProgram(terrainShaderProgram);
        if (terrainShaderAttrs   != null) app.setShaderAttributeSet(terrainShaderAttrs);

        cachedChunkAppearance = app;
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
