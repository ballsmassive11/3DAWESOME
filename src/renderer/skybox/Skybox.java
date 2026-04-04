package renderer.skybox;

import com.sun.j3d.utils.image.TextureLoader;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.net.URL;

public class Skybox {
    private final Background background;

    public Skybox(String folderPath, String extension) {
        background = new Background();
        background.setApplicationBounds(new BoundingSphere(new Point3d(0, 0, 0), Double.MAX_VALUE));
        background.setCapability(Background.ALLOW_APPLICATION_BOUNDS_WRITE);

        BranchGroup bgGeometry = createSkyboxGeometry(folderPath, extension);
        if (bgGeometry != null) {
            background.setGeometry(bgGeometry);
        } else {
            // Fallback: solid sky color
            background.setColor(new Color3f(0.5f, 0.6f, 0.8f));
        }
    }

    /**
     * Creates a cube with 6 individually-textured faces, wound CCW from inside.
     * Returns null if no face images could be loaded.
     */
    private BranchGroup createSkyboxGeometry(String folderPath, String ext) {
        // Face name, then the 4 corner vertices in CCW order as seen from INSIDE the cube,
        // then the corresponding 2D UV coordinates.
        // Faces: px, nx, py, ny, pz, nz
        String[] faceNames    = {"px", "nx", "py", "ny", "pz", "nz"};
        String[] altFaceNames = {"right", "left", "top", "bottom", "front", "back"};

        // Cube corners must stay within Java3D's unit sphere for Background geometry.
        // A corner at (s,s,s) has radius = s*sqrt(3), so s = 1/sqrt(3) puts corners exactly on the sphere.
        float s = 1.0f / (float) Math.sqrt(3.0);

        // Each sub-array: 4 vertices (x,y,z) in CCW order when viewed from inside
        float[][][] faceVerts = {
            // +X (right)  — viewed from inside, CCW
            {{s,-s,-s},{s,-s, s},{s, s, s},{s, s,-s}},
            // -X (left)
            {{-s,-s, s},{-s,-s,-s},{-s, s,-s},{-s, s, s}},
            // +Y (top)
            {{-s, s,-s},{ s, s,-s},{ s, s, s},{-s, s, s}},
            // -Y (bottom)
            {{ s,-s,-s},{-s,-s,-s},{-s,-s, s},{ s,-s, s}},
            // +Z (front/back)
            {{ s,-s, s},{-s,-s, s},{-s, s, s},{ s, s, s}},
            // -Z
            {{-s,-s,-s},{ s,-s,-s},{ s, s,-s},{-s, s,-s}},
        };

        // Standard 2D UVs for each quad (bottom-left→bottom-right→top-right→top-left)
        float[][] uvs = {
            {0,0},{1,0},{1,1},{0,1}
        };

        BranchGroup bg = new BranchGroup();
        boolean anyLoaded = false;

        for (int f = 0; f < 6; f++) {
            // Try primary name, then alternate
            URL url = getClass().getResource(folderPath + "/" + faceNames[f] + "." + ext);
            if (url == null)
                url = getClass().getResource(folderPath + "/" + altFaceNames[f] + "." + ext);
            if (url == null) {
                System.err.println("Skybox: could not find face " + faceNames[f] + "." + ext);
                continue;
            }

            TextureLoader loader = new TextureLoader(url, null);
            Texture2D tex = (Texture2D) loader.getTexture();
            if (tex == null) {
                System.err.println("Skybox: failed to load texture for " + faceNames[f]);
                continue;
            }
            tex.setBoundaryModeS(Texture.CLAMP_TO_EDGE);
            tex.setBoundaryModeT(Texture.CLAMP_TO_EDGE);
            tex.setMagFilter(Texture.BASE_LEVEL_LINEAR);
            tex.setMinFilter(Texture.BASE_LEVEL_LINEAR);

            QuadArray qa = new QuadArray(4, QuadArray.COORDINATES | QuadArray.TEXTURE_COORDINATE_2);
            for (int v = 0; v < 4; v++) {
                qa.setCoordinate(v, faceVerts[f][v]);
                qa.setTextureCoordinate(0, v, uvs[v]);
            }

            Appearance app = new Appearance();
            app.setTexture(tex);

            TextureAttributes ta = new TextureAttributes();
            ta.setTextureMode(TextureAttributes.REPLACE);
            app.setTextureAttributes(ta);

            // No lighting, no culling (camera is inside the cube)
            Material mat = new Material();
            mat.setLightingEnable(false);
            app.setMaterial(mat);

            PolygonAttributes pa = new PolygonAttributes();
            pa.setCullFace(PolygonAttributes.CULL_NONE);
            app.setPolygonAttributes(pa);

            bg.addChild(new Shape3D(qa, app));
            anyLoaded = true;
        }

        return anyLoaded ? bg : null;
    }

    public Background getBackground() {
        return background;
    }
}
