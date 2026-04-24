package water;

import objects.BaseObject;

import com.sun.j3d.utils.image.TextureLoader;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A flat water quad that uses DuDv + normal-map GLSL shaders for a semi-realistic
 * animated water surface. Tiles are placed at y=0 by MapGenerator to form the ocean.
 *
 * <p>Shared resources (textures, shader program) are loaded once and reused across
 * all tiles.  Each tile owns its own {@code time} ShaderAttributeValue so that
 * Java3D can push per-frame time updates without sharing a live NodeComponent across
 * multiple live scene-graph nodes.
 */
public class WaterTile extends BaseObject {

    /** Side length of each tile in world units. */
    public static final float TILE_SIZE = 150f;

    private static final String SHADER_DIR   = "resources/water/";
    private static final long   START_MILLIS = System.currentTimeMillis();

    // Shared across all tiles (loaded once)
    private static Texture2D         sDudvTex    = null;
    private static Texture2D         sNormalTex  = null;
    private static GLSLShaderProgram sShaderProg = null;

    // Per-tile: allows each tile to push its own time value after the scene goes live
    private ShaderAttributeValue timeAttr;

    /**
     * @param cx centre X in world space
     * @param cz centre Z in world space
     */
    public WaterTile(float cx, float cz) {
        super();
        setPosition(cx, 0.0, cz);
        setCollidable(false);
    }

    // ------------------------------------------------------------------
    // BaseObject overrides
    // ------------------------------------------------------------------

    @Override
    protected void initializeAppearance() {
        ensureSharedResources();
        appearance = buildTileAppearance();
    }

    @Override
    protected Shape3D createGeometry() {
        float h = TILE_SIZE / 2f;

        // Both texture units sample from the same UV set (coord set 0)
        int[] texMap = { 0, 0 };
        QuadArray quad = new QuadArray(4,
                GeometryArray.COORDINATES
                        | GeometryArray.NORMALS
                        | GeometryArray.TEXTURE_COORDINATE_2,
                1, texMap);

        // Corners in XZ plane (y=0 in local space)
        quad.setCoordinate(0, new Point3f(-h, 0f, -h));
        quad.setCoordinate(1, new Point3f( h, 0f, -h));
        quad.setCoordinate(2, new Point3f( h, 0f,  h));
        quad.setCoordinate(3, new Point3f(-h, 0f,  h));

        Vector3f up = new Vector3f(0f, 1f, 0f);
        for (int i = 0; i < 4; i++) quad.setNormal(i, up);

        // UV 0–1 across the tile; the vertex shader scales by ×4 for tiling density
        quad.setTextureCoordinate(0, 0, new TexCoord2f(0f, 0f));
        quad.setTextureCoordinate(0, 1, new TexCoord2f(1f, 0f));
        quad.setTextureCoordinate(0, 2, new TexCoord2f(1f, 1f));
        quad.setTextureCoordinate(0, 3, new TexCoord2f(0f, 1f));

        return new Shape3D(quad);
    }

    /** Push elapsed time (seconds) to the shader uniform every frame. */
    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        if (timeAttr != null) {
            float t = (System.currentTimeMillis() - START_MILLIS) * 0.001f;
            timeAttr.setValue(t);
        }
    }

    // ------------------------------------------------------------------
    // Appearance builder
    // ------------------------------------------------------------------

    private ShaderAppearance buildTileAppearance() {
        ShaderAppearance app = new ShaderAppearance();

        // Water material — specular colour drives the sun-glint highlights in the shader
        Material mat = new Material();
        mat.setLightingEnable(true);
        mat.setAmbientColor (new Color3f(0.05f, 0.25f, 0.40f));
        mat.setDiffuseColor (new Color3f(0.10f, 0.40f, 0.60f));
        mat.setSpecularColor(new Color3f(1.00f, 1.00f, 1.00f));
        mat.setShininess(80f);
        app.setMaterial(mat);

        // Fully opaque — no terrain below water level, so no blending to avoid skybox show-through
        app.setTransparencyAttributes(
                new TransparencyAttributes(TransparencyAttributes.NONE, 0.0f));

        // Visible from above and below
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);

        // Unit 0 = DuDv distortion map, unit 1 = normal map
        TextureUnitState[] tus = new TextureUnitState[2];
        tus[0] = new TextureUnitState();
        tus[0].setTexture(sDudvTex);
        tus[1] = new TextureUnitState();
        tus[1].setTexture(sNormalTex);
        app.setTextureUnitState(tus);

        if (sShaderProg != null) {
            app.setShaderProgram(sShaderProg);

            // time uniform — ALLOW_VALUE_WRITE must be set before the node goes live
            timeAttr = new ShaderAttributeValue("time", 0.0f);
            timeAttr.setCapability(ShaderAttributeValue.ALLOW_VALUE_WRITE);

            ShaderAttributeSet attrs = new ShaderAttributeSet();
            attrs.put(new ShaderAttributeValue("waterDuDvTex",   0));
            attrs.put(new ShaderAttributeValue("waterNormalTex", 1));
            attrs.put(timeAttr);
            app.setShaderAttributeSet(attrs);
        }

        return app;
    }

    // ------------------------------------------------------------------
    // Shared resource helpers
    // ------------------------------------------------------------------

    private static void ensureSharedResources() {
        if (sDudvTex == null)   sDudvTex   = loadTexture(SHADER_DIR + "waterdudv.png");
        if (sNormalTex == null) sNormalTex = loadTexture(SHADER_DIR + "waternormal.png");
        if (sShaderProg == null) buildShaderProgram();
    }

    private static void buildShaderProgram() {
        try {
            String vert = new String(Files.readAllBytes(Paths.get(SHADER_DIR + "water.vert")));
            String frag = new String(Files.readAllBytes(Paths.get(SHADER_DIR + "water.frag")));

            SourceCodeShader vs = new SourceCodeShader(
                    Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_VERTEX, vert);
            SourceCodeShader fs = new SourceCodeShader(
                    Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_FRAGMENT, frag);

            sShaderProg = new GLSLShaderProgram();
            sShaderProg.setShaders(new Shader[]{ vs, fs });
            sShaderProg.setShaderAttrNames(
                    new String[]{ "waterDuDvTex", "waterNormalTex", "time" });
        } catch (IOException e) {
            System.err.println("[WaterTile] Failed to load water shaders: " + e.getMessage());
        }
    }

    private static Texture2D loadTexture(String path) {
        TextureLoader tl = new TextureLoader(path, null);
        Texture2D tex = (Texture2D) tl.getTexture();
        if (tex == null) {
            System.err.println("[WaterTile] Could not load texture: " + path);
            return null;
        }
        tex.setBoundaryModeS(Texture.WRAP);
        tex.setBoundaryModeT(Texture.WRAP);
        tex.setMinFilter(Texture.MULTI_LEVEL_LINEAR);
        tex.setMagFilter(Texture.BASE_LEVEL_LINEAR);
        return tex;
    }
}
