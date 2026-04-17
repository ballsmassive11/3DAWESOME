package particles;

import com.sun.j3d.utils.image.TextureLoader;

import javax.media.j3d.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Builds and owns the {@link ShaderAppearance} used by {@link ParticleRenderer}.
 *
 * <h3>Atlas layout</h3>
 * The atlas is a single PNG ({@value #ATLAS_PATH}) divided into an
 * {@value #ATLAS_GRID}×{@value #ATLAS_GRID} grid of equal-sized sprites.
 * Sprite index 0 is the top-left cell; indices increase left-to-right, then top-to-bottom.
 *
 * <pre>
 *  0  1  2  3
 *  4  5  6  7
 *  8  9 10 11
 * 12 13 14 15
 * </pre>
 *
 * If {@code atlas.png} is missing, a fallback 1×1 white texture is used so particles
 * still render with their vertex color tint (identical to the old untextured renderer).
 *
 * <h3>GLSL shaders</h3>
 * Loaded from {@code resources/particles/particle.vert} and {@code particle.frag}.
 * The fragment shader multiplies the atlas sample by the vertex RGBA and applies scene fog.
 */
public class ParticleShader {

    public static final String SHADER_DIR = "resources/particles/";
    private static final String ATLAS_PATH = SHADER_DIR + "happyhappyhappy.png";

    /**
     * Number of sprites per row and per column in the atlas.
     * 1 = the whole image is sprite 0 (single-sprite mode).
     * 4 = 4×4 grid of 16 sprite slots (indices 0–15).
     */
    public static final int ATLAS_GRID = 1;

    private final ShaderAppearance appearance;

    // -------------------------------------------------------------------------

    public ParticleShader() {
        appearance = buildAppearance();
    }

    /**
     * Returns the fully configured {@link ShaderAppearance} to pass to the
     * {@link ParticleRenderer}.
     */
    public ShaderAppearance getAppearance() { return appearance; }

    // -------------------------------------------------------------------------
    // UV helper
    // -------------------------------------------------------------------------

    /**
     * Returns the 8 UV floats (4 pairs, one per billboard corner) for the given sprite
     * index, ordered BL → BR → TR → TL — matching the quad winding used by
     * {@link ParticleRenderer}.
     *
     * <p>Java3D's {@link TextureLoader} flips the image vertically so that V=0 is at
     * the bottom of the texture in OpenGL space.  This method compensates so that
     * sprite row 0 in the PNG still maps to the top-left of the atlas.</p>
     *
     * @param spriteIndex 0-based sprite index (clamped to the valid range).
     */
    public static float[] getSpriteUVs(int spriteIndex) {
        int total = ATLAS_GRID * ATLAS_GRID;
        spriteIndex = Math.max(0, Math.min(spriteIndex, total - 1));

        int   col      = spriteIndex % ATLAS_GRID;
        int   row      = spriteIndex / ATLAS_GRID;
        float cellSize = 1.0f / ATLAS_GRID;

        float u0 = col * cellSize;
        float u1 = u0 + cellSize;

        // TextureLoader flips Y: row 0 in the PNG ends up at high-V in GL coords.
        float v1 = 1.0f - row * cellSize;        // top of sprite (GL)
        float v0 = v1 - cellSize;                // bottom of sprite (GL)

        // BL, BR, TR, TL
        return new float[]{ u0,v0,  u1,v0,  u1,v1,  u0,v1 };
    }

    // -------------------------------------------------------------------------
    // Private builders
    // -------------------------------------------------------------------------

    private ShaderAppearance buildAppearance() {
        ShaderAppearance app = new ShaderAppearance();

        // Two-sided, no depth writes, additive/standard alpha blend
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);

        TransparencyAttributes ta = new TransparencyAttributes(TransparencyAttributes.BLENDED, 0f);
        ta.setSrcBlendFunction(TransparencyAttributes.BLEND_SRC_ALPHA);
        ta.setDstBlendFunction(TransparencyAttributes.BLEND_ONE_MINUS_SRC_ALPHA);
        app.setTransparencyAttributes(ta);

        RenderingAttributes ra = new RenderingAttributes();
        ra.setDepthBufferWriteEnable(false);
        app.setRenderingAttributes(ra);

        // Atlas texture — unit 0
        Texture2D atlas = loadAtlas();
        if (atlas != null) {
            TextureUnitState tus = new TextureUnitState();
            tus.setTexture(atlas);
            app.setTextureUnitState(new TextureUnitState[]{ tus });
        }

        // GLSL shader program
        try {
            String vertSrc = new String(Files.readAllBytes(Paths.get(SHADER_DIR + "particle.vert")));
            String fragSrc = new String(Files.readAllBytes(Paths.get(SHADER_DIR + "particle.frag")));

            SourceCodeShader vs = new SourceCodeShader(
                    Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_VERTEX,   vertSrc);
            SourceCodeShader fs = new SourceCodeShader(
                    Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_FRAGMENT, fragSrc);

            GLSLShaderProgram program = new GLSLShaderProgram();
            program.setShaders(new Shader[]{ vs, fs });
            program.setShaderAttrNames(new String[]{ "atlasTex" });
            app.setShaderProgram(program);

            ShaderAttributeSet attrs = new ShaderAttributeSet();
            attrs.put(new ShaderAttributeValue("atlasTex", 0));
            app.setShaderAttributeSet(attrs);

        } catch (IOException e) {
            System.err.println("[ParticleShader] Could not load particle shaders: " + e.getMessage());
        }

        return app;
    }

    private Texture2D loadAtlas() {
        File f = new File(ATLAS_PATH);

        TextureLoader tl;
        if (f.exists()) {
            tl = new TextureLoader(ATLAS_PATH, (java.awt.Component) null);
        } else {
            // Fallback: 4×4 solid-white image — particles use vertex color only
            System.out.println("[ParticleShader] atlas.png not found at " + ATLAS_PATH
                    + " — using white fallback (particles render with vertex colors only).");
            BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 4; y++)
                for (int x = 0; x < 4; x++)
                    img.setRGB(x, y, 0xFFFFFFFF);
            tl = new TextureLoader(img, (java.awt.Component) null);
        }

        Texture2D tex = (Texture2D) tl.getTexture();
        if (tex == null) {
            System.err.println("[ParticleShader] TextureLoader returned null for atlas.");
            return null;
        }
        tex.setBoundaryModeS(Texture.CLAMP);
        tex.setBoundaryModeT(Texture.CLAMP);
        tex.setMinFilter(Texture.MULTI_LEVEL_LINEAR);
        tex.setMagFilter(Texture.BASE_LEVEL_LINEAR);
        return tex;
    }
}
