package terrain;

import objects.Brick;
import objects.TerrainMesh;
import physics.TerrainHeightProvider;
import util.FastNoiseLite;
import world.World;

import com.sun.j3d.utils.image.TextureLoader;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generates a continuous terrain mesh by displacing vertices of a flat grid
 * using layered (FBm) simplex noise with domain warping.
 * <p>
 * Unlike the legacy brick-based generator, the ground here is a single
 * triangulated surface — like a sheet of paper that rises and falls.
 * Each biome zone (sand, grass, rock, snow) is textured via a GLSL shader
 * that blends the four textures based on the per-vertex height gradient t,
 * stored in the alpha channel of the vertex colour.
 */
public class MapGenerator implements TerrainHeightProvider {

    private static final String SHADER_DIR = "src/resources/terrain/";

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
        Color4f[] colors  = new Color4f[rows * cols]; // alpha = blend weight t

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float nx = (c - cols / 2f) * cellSize;
                float nz = (r - rows / 2f) * cellSize;

                // Domain-warp the sample coordinates for organic shapes
                FastNoiseLite.Vector2 coord = new FastNoiseLite.Vector2(nx, nz);
                warpNoise.DomainWarp(coord);

                float noiseVal = noise.GetNoise(coord.x, coord.y); // [-1, 1]

                float height;
                float blendT;

                if (noiseVal < threshold) {
                    // Sub-water: sand slopes gently downward from shore; t=0 → sand texture
                    float depth = Math.min((threshold - noiseVal) / (threshold + 1.0f), 1.0f);
                    height = -(float) Math.pow(depth, 1.5) * 4.0f;
                    blendT = 0.0f;
                } else {
                    // Above water: t=0 is shore, t=1 is peak
                    blendT = (noiseVal - threshold) / (1.0f - threshold);
                    height = (float) Math.pow(blendT, 2.5) * heightScale;
                }

                heights[r * cols + c] = height;
                // RGB not used by the shader; store as neutral white so a fallback fixed-function
                // pass would still look reasonable.
                colors [r * cols + c] = new Color4f(1.0f, 1.0f, 1.0f, blendT);
            }
        }

        // Build the terrain mesh
        TerrainMesh terrain = new TerrainMesh(heights, colors, rows, cols, cellSize);
        terrain.setPosition(0, 0, zOffset);

        // Build the shader appearance
        ShaderAppearance terrainApp = buildTerrainAppearance();
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
    // Shader appearance builder
    // ------------------------------------------------------------------

    private ShaderAppearance buildTerrainAppearance() {
        ShaderAppearance app = new ShaderAppearance();

        // Material properties — fed into gl_FrontMaterial in the GLSL vertex shader
        Material mat = new Material();
        mat.setLightingEnable(true);
        mat.setAmbientColor (new Color3f(0.25f, 0.25f, 0.25f));
        mat.setDiffuseColor (new Color3f(1.0f,  1.0f,  1.0f));
        mat.setSpecularColor(new Color3f(0.08f, 0.08f, 0.08f));
        mat.setShininess(18f);
        app.setMaterial(mat);

        // Load the four biome textures into texture units 0-3
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
                tex.setMinFilter(Texture.MULTI_LEVEL_LINEAR); // trilinear
                tex.setMagFilter(Texture.BASE_LEVEL_LINEAR);  // bilinear up-close
                tus[i].setTexture(tex);
            } else {
                System.err.println("Warning: could not load terrain texture: " + texPaths[i]);
            }
        }
        app.setTextureUnitState(tus);

        // Load and compile the GLSL shader program
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

            // Bind sampler uniforms to their texture unit indices
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
    // Configuration setters
    // ------------------------------------------------------------------

    public void setSeed(int seed)              { noise.SetSeed(seed); warpNoise.SetSeed(seed); }
    public void setGridSize(int gridSize)      { this.gridSize    = gridSize;    }
    public void setCellSize(float cellSize)    { this.cellSize    = cellSize;    }
    public void setThreshold(float threshold)  { this.threshold   = threshold;  }
    public void setHeightScale(float hs)       { this.heightScale = hs;         }
    public void setZOffset(float zOffset)      { this.zOffset     = zOffset;    }
}
