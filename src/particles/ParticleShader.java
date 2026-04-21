package particles;

import com.sun.j3d.utils.image.TextureLoader;

import javax.media.j3d.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Builds the {@link ShaderAppearance} for one particle batch.
 *
 * Each instance is tied to one atlas image and one grid size.
 * {@link ParticleRenderer} creates one {@code ParticleShader} per unique atlas path.
 *
 * <h3>Atlas layout</h3>
 * The atlas PNG is divided into an N×N grid of equal-sized sprites.
 * Sprite index 0 is the top-left cell; indices increase left-to-right, then top-to-bottom.
 * Pass {@code atlasGrid = 1} to use the whole image as a single sprite.
 *
 * <h3>Null atlas</h3>
 * Passing {@code atlasPath = null} uses a solid-white 1×1 fallback texture so particles
 * render with their vertex tint color only (useful for plain colored particles).
 */
public class ParticleShader {

    public static final String SHADER_DIR = "resources/particles/";

    private final int atlasGrid;
    private final ShaderAppearance appearance;

    // -------------------------------------------------------------------------

    /**
     * @param atlasPath  Path to the atlas PNG (e.g. {@code "resources/particles/fire.png"}),
     *                   or {@code null} to render with vertex colors only (white 1×1 fallback).
     * @param atlasGrid  Sprites per row/column (1 = whole image is sprite 0).
     */
    public ParticleShader(String atlasPath, int atlasGrid) {
        this.atlasGrid  = atlasGrid;
        this.appearance = buildAppearance(atlasPath);
    }

    public ShaderAppearance getAppearance() { return appearance; }
    public int getAtlasGrid()               { return atlasGrid;  }

    // -------------------------------------------------------------------------
    // UV helper
    // -------------------------------------------------------------------------

    /**
     * Returns 8 UV floats (BL → BR → TR → TL) for the given sprite index in this atlas.
     * Compensates for Java3D's TextureLoader Y-flip so row 0 in the PNG maps to the
     * top-left sprite visually.
     */
    public float[] getSpriteUVs(int spriteIndex) {
        int total = atlasGrid * atlasGrid;
        spriteIndex = Math.max(0, Math.min(spriteIndex, total - 1));

        int   col      = spriteIndex % atlasGrid;
        int   row      = spriteIndex / atlasGrid;
        float cellSize = 1.0f / atlasGrid;

        float u0 = col * cellSize;
        float u1 = u0 + cellSize;

        // TextureLoader flips Y: row 0 in the PNG ends up at high-V in GL coords.
        float v1 = 1.0f - row * cellSize;   // top of sprite (GL)
        float v0 = v1 - cellSize;           // bottom of sprite (GL)

        // BL, BR, TR, TL
        return new float[]{ u0,v0,  u1,v0,  u1,v1,  u0,v1 };
    }

    // -------------------------------------------------------------------------
    // Private builders
    // -------------------------------------------------------------------------

    private ShaderAppearance buildAppearance(String atlasPath) {
        ShaderAppearance app = new ShaderAppearance();

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

        Texture2D atlas = loadAtlas(atlasPath);
        if (atlas != null) {
            TextureUnitState tus = new TextureUnitState();
            tus.setTexture(atlas);
            app.setTextureUnitState(new TextureUnitState[]{ tus });
        }

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

    private static Texture2D loadAtlas(String atlasPath) {
        TextureLoader tl;

        if (atlasPath != null && new File(atlasPath).exists()) {
            tl = new TextureLoader(atlasPath, TextureLoader.GENERATE_MIPMAP, (java.awt.Component) null);
        } else {
            if (atlasPath != null)
                System.out.println("[ParticleShader] Atlas not found: " + atlasPath + " — using white fallback.");
            // 1×1 solid-white: particles render with vertex color only
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, 0xFFFFFFFF);
            tl = new TextureLoader(img, (java.awt.Component) null);
        }

        Texture2D tex = (Texture2D) tl.getTexture();
        if (tex == null) { System.err.println("[ParticleShader] TextureLoader returned null."); return null; }
        tex.setBoundaryModeS(Texture.CLAMP);
        tex.setBoundaryModeT(Texture.CLAMP);
        tex.setMinFilter(Texture.BASE_LEVEL_LINEAR);
        tex.setMagFilter(Texture.BASE_LEVEL_LINEAR);
        return tex;
    }
}
