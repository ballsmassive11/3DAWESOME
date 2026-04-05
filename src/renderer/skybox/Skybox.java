package renderer.skybox;

import com.sun.j3d.utils.image.TextureLoader;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Skybox {
    private final Background background;

    // Fog overlay transparency lists per sky (for show/hide via setFogVisible)
    private final List<TransparencyAttributes> dayOverlayTransparencies   = new ArrayList<>();
    private final List<TransparencyAttributes> nightOverlayTransparencies = new ArrayList<>();
    private List<TransparencyAttributes> activeOverlayTransparencies = dayOverlayTransparencies;

    // All fog overlay quads across both day and night geometries, with their per-vertex alphas.
    // Updated every frame so both skies always have the current fog color, preventing any snap.
    private static final class FogOverlayQuad {
        final QuadArray qa;
        final float[] alphas; // 4 vertex alphas
        FogOverlayQuad(QuadArray qa, float a0, float a1, float a2, float a3) {
            this.qa = qa;
            this.alphas = new float[]{a0, a1, a2, a3};
        }
    }
    private final List<FogOverlayQuad> fogOverlayQuads = new ArrayList<>();

    // Veil quads (in both geometries) for cross-fade transition
    private final List<TransparencyAttributes> veilTransparencies = new ArrayList<>();

    // One TransformGroup per sky so both can be rotated independently
    private TransformGroup dayTG;
    private TransformGroup nightTG;
    private TransformGroup activeTG;

    private BranchGroup dayBG;
    private BranchGroup nightBG;

    private double rotation = 0;
    private boolean fogVisible = true;

    private static final double ROTATION_SPEED = 0.005;
    private static final Color3f VEIL_COLOR = new Color3f(0.02f, 0.02f, 0.05f);

    public Skybox(String folderPath, String extension, Color3f fogColor) {
        background = new Background();
        background.setApplicationBounds(new BoundingSphere(new Point3d(0, 0, 0), Double.MAX_VALUE));
        background.setCapability(Background.ALLOW_APPLICATION_BOUNDS_WRITE);
        background.setCapability(Background.ALLOW_GEOMETRY_WRITE);

        TransformGroup[] tgOut = new TransformGroup[1];
        dayBG = createSkyboxGeometry(folderPath, extension, fogColor, dayOverlayTransparencies, tgOut);
        dayTG    = tgOut[0];
        activeTG = dayTG;

        if (dayBG != null) {
            background.setGeometry(dayBG);
        } else {
            background.setColor(fogColor != null ? fogColor : new Color3f(0.5f, 0.6f, 0.8f));
        }
    }

    /** Pre-builds the night sky geometry. Must be called before the scene is compiled. */
    public void preloadNightSky(String folderPath, String extension, Color3f nightFogColor) {
        TransformGroup[] tgOut = new TransformGroup[1];
        nightBG = createSkyboxGeometry(folderPath, extension, nightFogColor, nightOverlayTransparencies, tgOut);
        nightTG = tgOut[0];
    }

    /** Swaps to day or night geometry. Call this mid-veil so the snap is hidden. */
    public void setDaytime(boolean day) {
        BranchGroup target = day ? dayBG : nightBG;
        if (target == null) return;
        activeOverlayTransparencies = day ? dayOverlayTransparencies : nightOverlayTransparencies;
        activeTG = day ? dayTG : nightTG;
        background.setGeometry(target);
        setFogVisible(fogVisible);
    }

    /**
     * Updates fog overlay vertex colors on ALL skybox geometries (day + night).
     * Call this every frame with the current fog color so both skies stay in sync
     * and there is no color snap when the geometry swaps.
     */
    public void setFogOverlayColor(Color3f color) {
        for (FogOverlayQuad fq : fogOverlayQuads) {
            for (int v = 0; v < 4; v++) {
                fq.qa.setColor(v, new Color4f(color.x, color.y, color.z, fq.alphas[v]));
            }
        }
    }

    /** Sets the opacity of the transition veil (0 = invisible, 1 = fully covers sky). */
    public void setVeilAlpha(float alpha) {
        // In Java3D: transparency=0.0 is opaque, transparency=1.0 is fully transparent.
        float transparency = 1f - Math.max(0f, Math.min(1f, alpha));
        for (TransparencyAttributes ta : veilTransparencies) {
            ta.setTransparency(transparency);
        }
    }

    /**
     * Builds skybox geometry: textured cube faces + fog gradient overlays + transition veil.
     * An OrderedGroup ensures veil renders on top of fog overlays regardless of transparency sorting.
     */
    private BranchGroup createSkyboxGeometry(String folderPath, String ext, Color3f fogColor,
                                             List<TransparencyAttributes> overlayList,
                                             TransformGroup[] tgOut) {
        String[] faceNames    = {"pz", "nz", "py", "ny", "px", "nx"};
        String[] altFaceNames = {"right", "left", "top", "bottom", "front", "back"};

        float s = 1.0f / (float) Math.sqrt(3.0);

        float[][][] faceVerts = {
            {{s,-s,-s},{s,-s, s},{s, s, s},{s, s,-s}},   // +X
            {{-s,-s, s},{-s,-s,-s},{-s, s,-s},{-s, s, s}}, // -X
            {{-s, s,-s},{ s, s,-s},{ s, s, s},{-s, s, s}}, // +Y
            {{ s,-s,-s},{-s,-s,-s},{-s,-s, s},{ s,-s, s}}, // -Y
            {{ s,-s, s},{-s,-s, s},{-s, s, s},{ s, s, s}}, // +Z
            {{-s,-s,-s},{ s,-s,-s},{ s, s,-s},{-s, s,-s}}, // -Z
        };

        float[][] uvs = {{0,0},{1,0},{1,1},{0,1}};

        BranchGroup bg = new BranchGroup();

        // OrderedGroup guarantees render order: textured faces, then fog overlays, then veil on top.
        OrderedGroup og = new OrderedGroup();
        bg.addChild(og);

        // --- 1. Textured faces (inside a rotatable TransformGroup) ---
        TransformGroup tg = new TransformGroup();
        tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        tgOut[0] = tg;

        boolean anyLoaded = false;
        for (int f = 0; f < 6; f++) {
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
            Material mat = new Material();
            mat.setLightingEnable(false);
            app.setMaterial(mat);
            PolygonAttributes pa = new PolygonAttributes();
            pa.setCullFace(PolygonAttributes.CULL_NONE);
            app.setPolygonAttributes(pa);

            tg.addChild(new Shape3D(qa, app));
            anyLoaded = true;
        }
        og.addChild(tg);

        // --- 2. Fog gradient overlays (rendered after textured faces) ---
        if (fogColor != null) {
            float splitY = s * -0.05f;
            float topY   = s * 0.35f;

            // Bottom face: fully opaque
            addOverlayQuad(og, fogColor, 1f, 1f, 1f, 1f,
                    faceVerts[3][0], faceVerts[3][1], faceVerts[3][2], faceVerts[3][3], overlayList);

            int[] sideFaces = {0, 1, 4, 5};
            for (int f : sideFaces) {
                float[] b0 = faceVerts[f][0], b1 = faceVerts[f][1];
                float[] m0 = {b0[0], splitY, b0[2]}, m1 = {b1[0], splitY, b1[2]};
                float[] g0 = {b0[0], topY,   b0[2]}, g1 = {b1[0], topY,   b1[2]};
                addOverlayQuad(og, fogColor, 1f, 1f, 1f, 1f, b0, b1, m1, m0, overlayList);
                addOverlayQuad(og, fogColor, 1f, 1f, 0f, 0f, m0, m1, g1, g0, overlayList);
            }
        }

        // --- 3. Veil quads (rendered last = always on top of fog overlays) ---
        for (int f = 0; f < 6; f++) {
            addVeilQuad(og, faceVerts[f]);
        }

        return anyLoaded ? bg : null;
    }

    private void addOverlayQuad(OrderedGroup og, Color3f fog,
                                float a0, float a1, float a2, float a3,
                                float[] v0, float[] v1, float[] v2, float[] v3,
                                List<TransparencyAttributes> list) {
        QuadArray qa = new QuadArray(4, QuadArray.COORDINATES | QuadArray.COLOR_4);
        // Allow color updates at runtime so fog color can transition smoothly
        qa.setCapability(GeometryArray.ALLOW_COLOR_WRITE);
        qa.setCoordinate(0, v0); qa.setColor(0, new Color4f(fog.x, fog.y, fog.z, a0));
        qa.setCoordinate(1, v1); qa.setColor(1, new Color4f(fog.x, fog.y, fog.z, a1));
        qa.setCoordinate(2, v2); qa.setColor(2, new Color4f(fog.x, fog.y, fog.z, a2));
        qa.setCoordinate(3, v3); qa.setColor(3, new Color4f(fog.x, fog.y, fog.z, a3));

        fogOverlayQuads.add(new FogOverlayQuad(qa, a0, a1, a2, a3));

        TransparencyAttributes ta = new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.0f);
        ta.setCapability(TransparencyAttributes.ALLOW_MODE_WRITE);
        list.add(ta);

        Appearance app = new Appearance();
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);
        app.setTransparencyAttributes(ta);
        Material mat = new Material();
        mat.setLightingEnable(false);
        app.setMaterial(mat);

        og.addChild(new Shape3D(qa, app));
    }

    private void addVeilQuad(OrderedGroup og, float[][] verts) {
        QuadArray qa = new QuadArray(4, QuadArray.COORDINATES | QuadArray.COLOR_4);
        for (int v = 0; v < 4; v++) {
            qa.setCoordinate(v, verts[v]);
            qa.setColor(v, new Color4f(VEIL_COLOR.x, VEIL_COLOR.y, VEIL_COLOR.z, 1.0f));
        }

        // Start fully transparent (transparency=1.0 = invisible in Java3D).
        // NONE mode means "no transparency = opaque", so we must use BLENDED.
        TransparencyAttributes ta = new TransparencyAttributes(TransparencyAttributes.BLENDED, 1.0f);
        ta.setCapability(TransparencyAttributes.ALLOW_VALUE_WRITE);
        veilTransparencies.add(ta);

        Appearance app = new Appearance();
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);
        app.setTransparencyAttributes(ta);
        Material mat = new Material();
        mat.setLightingEnable(false);
        app.setMaterial(mat);

        og.addChild(new Shape3D(qa, app));
    }

    /** Called each frame to rotate the active sky. */
    public void update(double dt) {
        if (activeTG == null) return;
        rotation += ROTATION_SPEED * dt;
        Transform3D t = new Transform3D();
        t.rotY(rotation);
        activeTG.setTransform(t);
    }

    /** Shows or hides the fog gradient overlay on the currently active skybox. */
    public void setFogVisible(boolean visible) {
        this.fogVisible = visible;
        int mode = visible ? TransparencyAttributes.BLENDED : TransparencyAttributes.NONE;
        for (TransparencyAttributes ta : activeOverlayTransparencies) {
            ta.setTransparencyMode(mode);
        }
    }

    public Background getBackground() {
        return background;
    }
}
