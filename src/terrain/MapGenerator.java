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
 * <p>The four terrain textures (sand, grass, rock, snow) are blended per-vertex via a GLSL
 * shader that reads the blend weight {@code t} stored in the vertex colour alpha.
 */
public class MapGenerator implements TerrainHeightProvider {

    private static final String SHADER_DIR  = "resources/terrain/";
    private static final String TREE_PATH   = "resources/models/Tree/Lowpoly_tree_sample.obj";

    // Hills terrain constants
    private static final float HILLS_BASE_Y = 0.3f;   // minimum hill height (above water)
    private static final float RIVER_BOTTOM = -2.0f;  // river-bed height (below water → looks filled)
    private static final float RIVER_WIDTH  = 0.15f;  // |riverNoise| threshold for channel width
    private static final float ALTITUDE_BONUS = 70f;

    // Tree spawning constants
    private static final float TREE_DENSITY = 0.01f;
    private static final double TREE_SCALE_MIN = 4.5;
    private static final double TREE_SCALE_MAX = 7.5;

    // Shared GPU resources for terrain chunks – loaded once, reused per-chunk.
    private Texture2D[]        terrainTextures;
    private GLSLShaderProgram  terrainShaderProgram;
    private ShaderAttributeSet terrainShaderAttrs;
    private ShaderAppearance   cachedChunkAppearance;

    // ------------------------------------------------------------------
    // Noise generators
    // ------------------------------------------------------------------

    /** Smoother FBm for rounded rolling hills */
    private final FastNoiseLite hillsNoise;

    /** River channels: domain-warp for organic river curves */
    private final FastNoiseLite riverWarp;
    /** River channels: low-frequency FBm; rivers where |val| < RIVER_WIDTH */
    private final FastNoiseLite riverNoise;

    /** Altitude noise for mountain detection */
    private final FastNoiseLite altNoise;

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    private int    gridSize    = 250;
    private float  cellSize    = 0.8f;
    private float  heightScale = 7.0f;
    private int    currentSeed = 0;
    private ProgressReporter reporter;

    public MapGenerator() {
        // --- hills height: fewer octaves, higher gain → smoother, rounder hills ---
        hillsNoise = new FastNoiseLite();
        hillsNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        hillsNoise.SetFrequency(0.015f);
        hillsNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        hillsNoise.SetFractalOctaves(3);
        hillsNoise.SetFractalLacunarity(2.0f);
        hillsNoise.SetFractalGain(0.45f);

        // --- river warp: gentle domain warp for organic curves ---
        riverWarp = new FastNoiseLite();
        riverWarp.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        riverWarp.SetDomainWarpType(FastNoiseLite.DomainWarpType.OpenSimplex2Reduced);
        riverWarp.SetDomainWarpAmp(22f);
        riverWarp.SetFrequency(0.001f);
        riverWarp.SetFractalType(FastNoiseLite.FractalType.DomainWarpIndependent);
        riverWarp.SetFractalOctaves(2);

        // --- river channels: very low frequency so rivers are long and winding ---
        riverNoise = new FastNoiseLite();
        riverNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        riverNoise.SetFrequency(0.003f);
        riverNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        riverNoise.SetFractalOctaves(2);
        riverNoise.SetFractalLacunarity(2.0f);
        riverNoise.SetFractalGain(0.5f);

        // --- altitude noise: medium frequency for mountainous areas ---
        altNoise = new FastNoiseLite();
        altNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        altNoise.SetFrequency(0.002f);
        altNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        altNoise.SetFractalOctaves(4);
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

        buildTerrainAppearance();
        world.setTerrainProvider(this);

        report(0.05f, "Creating chunk manager...");
        ChunkManager cm = new ChunkManager(this, world);
        world.setChunkManager(cm);

        report(0.1f, "Preloading terrain chunks around spawn...");
        javax.vecmath.Vector3d spawn = world.getPlayer().getPosition();
        cm.preload(spawn, 5, reporter);

        report(0.8f, "Creating world border...");
        WorldBorder border = new WorldBorder(ChunkManager.BORDER_RADIUS, world);
        world.setWorldBorder(border);

        // Place player safely on ground
        float groundY = getHeightAt((float) spawn.x, (float) spawn.z);
        world.getPlayer().getPosition().y = groundY + EntityPhysics.EYE_HEIGHT;
    }

    // ------------------------------------------------------------------
    // Per-chunk data generation (used by ChunkManager)
    // ------------------------------------------------------------------

    /**
     * Fills {@code heights}, {@code colors} (RGBA per vertex; alpha = blend height),
     * and {@code riverVals} for an {@code n × n} vertex grid whose (0,0) vertex sits
     * at world position {@code (wx0, wz0)}.
     */
    public void buildChunkData(float wx0, float wz0, int n, float chunkCellSize,
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
                float altVal      = altNoise.GetNoise(nx, nz);

                rc.x = nx; rc.y = nz;
                riverWarp.DomainWarp(rc);
                float riverVal = Math.abs(riverNoise.GetNoise(rc.x, rc.y));
                if (riverVals != null) riverVals[idx] = riverVal;

                float height, blendT;
                if (riverVal < RIVER_WIDTH) {
                    float t     = riverVal / RIVER_WIDTH;
                    float blend = t * t * (3.0f - 2.0f * t);
                    float baseHeight = RIVER_BOTTOM + (hillHeight - RIVER_BOTTOM) * blend;
                    if (altVal > 0.3f) {
                        baseHeight += (altVal - 0.3f) * ALTITUDE_BONUS * blend;
                    }
                    height = baseHeight;
                    float baseT = blend * normalizedH * 0.15f;
                    blendT = altVal > 0.3f
                            ? Math.min(baseT + (altVal - 0.3f) * 0.5f, 1.0f)
                            : Math.min(baseT * 1.2f, 1.0f);
                } else {
                    height = hillHeight;
                    float baseT = Math.min(normalizedH * 0.20f, 0.65f);
                    if (altVal > 0.3f) {
                        height += (altVal - 0.3f) * ALTITUDE_BONUS;
                        blendT = Math.min(baseT + (altVal - 0.3f) * 0.5f, 1.0f);
                    } else {
                        blendT = Math.min(baseT * 1.2f, 1.0f);
                    }
                }

                heights[idx] = height;
                int cIdx = idx * 4;
                colors[cIdx    ] = 0f;
                colors[cIdx + 1] = 0f;
                colors[cIdx + 2] = 0f;
                colors[cIdx + 3] = blendT;
            }
        });
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
        float noiseVal    = hillsNoise.GetNoise(wx, wz);
        float normalizedH = (noiseVal + 1.0f) * 0.5f;
        float hillHeight  = HILLS_BASE_Y + normalizedH * heightScale;
        float altVal      = altNoise.GetNoise(wx, wz);

        FastNoiseLite.Vector2 rc = new FastNoiseLite.Vector2(wx, wz);
        riverWarp.DomainWarp(rc);
        float riverVal = Math.abs(riverNoise.GetNoise(rc.x, rc.y));

        if (riverVal < RIVER_WIDTH) {
            float t     = riverVal / RIVER_WIDTH;
            float blend = t * t * (3.0f - 2.0f * t);
            float h = RIVER_BOTTOM + (hillHeight - RIVER_BOTTOM) * blend;
            if (altVal > 0.3f) {
                h += (altVal - 0.3f) * ALTITUDE_BONUS * blend;
            }
            return h;
        }

        float h = hillHeight;
        if (altVal > 0.3f) {
            h += (altVal - 0.3f) * ALTITUDE_BONUS;
        }
        return h;
    }

    // ------------------------------------------------------------------
    // Configuration setters
    // ------------------------------------------------------------------

    public void setSeed(int seed) {
        this.currentSeed = seed;
        hillsNoise.SetSeed(seed);
        riverNoise.SetSeed(seed + 1337);
        riverWarp.SetSeed(seed + 2674);
        altNoise.SetSeed(seed + 6685);
    }

    public void setGridSize(int gridSize)    { this.gridSize    = gridSize;  }
    public void setCellSize(float cellSize)  { this.cellSize    = cellSize;  }
    public void setHeightScale(float hs)     { this.heightScale = hs;        }
}
