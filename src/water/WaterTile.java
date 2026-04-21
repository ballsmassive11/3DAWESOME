package water;

import objects.BaseObject;
import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.image.TextureLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A simple water plane that uses DuDv and Normal maps for a wavy effect.
 */
public class WaterTile extends BaseObject {
    public static final float WATER_HEIGHT = -0.5f;
    public static final float TILE_SIZE = 64.0f;

    public WaterTile(float x, float z) {
        super();
        setPosition(x, WATER_HEIGHT, z);
        setAppearance(createWaterAppearance());
    }

    @Override
    protected Shape3D createGeometry() {
        QuadArray geom = new QuadArray(4, GeometryArray.COORDINATES | GeometryArray.TEXTURE_COORDINATE_2 | GeometryArray.NORMALS);
        
        float halfSize = TILE_SIZE / 2f;
        Point3f[] coords = {
            new Point3f(-halfSize, 0, -halfSize),
            new Point3f(-halfSize, 0,  halfSize),
            new Point3f( halfSize, 0,  halfSize),
            new Point3f( halfSize, 0, -halfSize)
        };
        
        TexCoord2f[] texCoords = {
            new TexCoord2f(0, 0),
            new TexCoord2f(0, 1),
            new TexCoord2f(1, 1),
            new TexCoord2f(1, 0)
        };
        
        Vector3f[] normals = {
            new Vector3f(0, 1, 0),
            new Vector3f(0, 1, 0),
            new Vector3f(0, 1, 0),
            new Vector3f(0, 1, 0)
        };

        geom.setCoordinates(0, coords);
        geom.setTextureCoordinates(0, 0, texCoords);
        geom.setNormals(0, normals);
        
        Shape3D shape = new Shape3D(geom);
        // Let Java3D compute the bounds naturally so it's not culled.
        // The View.TRANSPARENCY_SORT_GEOMETRY policy in Game3DRenderer should
        // handle sorting based on the geometry's distance.
        shape.setBoundsAutoCompute(true);

        return shape;
    }

    private Appearance createWaterAppearance() {
        Appearance app = new Appearance();
        
        // Load textures
        TextureLoader dudvLoader = new TextureLoader("resources/water/waterdudv.png", null);
        Texture dudvTex = dudvLoader.getTexture();
        dudvTex.setBoundaryModeS(Texture.WRAP);
        dudvTex.setBoundaryModeT(Texture.WRAP);
        
        TextureLoader normalLoader = new TextureLoader("resources/water/waternormal.png", null);
        Texture normalTex = normalLoader.getTexture();
        normalTex.setBoundaryModeS(Texture.WRAP);
        normalTex.setBoundaryModeT(Texture.WRAP);

        TextureUnitState[] tus = new TextureUnitState[2];
        tus[0] = new TextureUnitState(dudvTex, new TextureAttributes(), null);
        tus[1] = new TextureUnitState(normalTex, new TextureAttributes(), null);

        // Shaders
        try {
            String vert = new String(Files.readAllBytes(Paths.get("resources/water/water.vert")));
            String frag = new String(Files.readAllBytes(Paths.get("resources/water/water.frag")));
            
            SourceCodeShader vs = new SourceCodeShader(
                Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_VERTEX, vert);
            SourceCodeShader fs = new SourceCodeShader(
                Shader.SHADING_LANGUAGE_GLSL, Shader.SHADER_TYPE_FRAGMENT, frag);
            
            GLSLShaderProgram program = new GLSLShaderProgram();
            program.setShaders(new Shader[]{vs, fs});
            program.setShaderAttrNames(new String[]{"time", "waterDuDvTex", "waterNormalTex"});
            
            ShaderAppearance sa = new ShaderAppearance();
            sa.setCapability(ShaderAppearance.ALLOW_SHADER_ATTRIBUTE_SET_READ);
            sa.setShaderProgram(program);
            sa.setTextureUnitState(tus);
            
            ShaderAttributeSet attrs = new ShaderAttributeSet();
            attrs.setCapability(ShaderAttributeSet.ALLOW_ATTRIBUTES_READ);
            attrs.setCapability(ShaderAttributeSet.ALLOW_ATTRIBUTES_WRITE);
            ShaderAttributeValue timeAttr = new ShaderAttributeValue("time", 0.0f);
            timeAttr.setCapability(ShaderAttributeObject.ALLOW_VALUE_WRITE);
            timeAttr.setCapability(ShaderAttributeObject.ALLOW_VALUE_READ);
            attrs.put(timeAttr);
            attrs.put(new ShaderAttributeValue("waterDuDvTex", new Integer(0)));
            attrs.put(new ShaderAttributeValue("waterNormalTex", new Integer(1)));
            sa.setShaderAttributeSet(attrs);
            
            // Transparency
            TransparencyAttributes ta = new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.6f);
            sa.setTransparencyAttributes(ta);
            
            // Material for specular
            Material mat = new Material();
            mat.setSpecularColor(new Color3f(1f, 1f, 1f));
            mat.setShininess(128.0f);
            sa.setMaterial(mat);

            return sa;
        } catch (IOException e) {
            e.printStackTrace();
            app.setTextureUnitState(tus);
            return app;
        }
    }
    
    @Override
    public void update(double deltaTime) {
        super.update(deltaTime);
        Appearance app = getAppearance();
        if (app instanceof ShaderAppearance) {
            ShaderAttributeSet attrs = ((ShaderAppearance) app).getShaderAttributeSet();
            ShaderAttributeValue timeAttr = (ShaderAttributeValue) attrs.get("time");
            if (timeAttr != null) {
                float time = (float) timeAttr.getValue();
                time += (float) deltaTime;
                timeAttr.setValue(time);
            }
        }
    }
}
