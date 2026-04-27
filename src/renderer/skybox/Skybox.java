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

    // Transparency attributes for day and night textured faces to allow cross-fade
    private final List<TransparencyAttributes> dayFaceTransparencies   = new ArrayList<>();
    private final List<TransparencyAttributes> nightFaceTransparencies = new ArrayList<>();

    // One TransformGroup per sky so both can be rotated independently
    private TransformGroup dayTG;
    private TransformGroup nightTG;

    // Sun and moon billboards — live in skyBG directly, independent of cloud rotation
    private TransformGroup sunTG;
    private TransformGroup moonTG;
    private TransparencyAttributes sunTrans;
    private TransparencyAttributes moonTrans;

    private BranchGroup skyBG;

    private double rotation = 0;
    private boolean fogVisible = true;

    private static final double ROTATION_SPEED = 0.008;

    public Skybox(String folderPath, String extension, Color3f fogColor) {
        background = new Background();
        background.setApplicationBounds(new BoundingSphere(new Point3d(0, 0, 0), Double.MAX_VALUE));
        background.setCapability(Background.ALLOW_APPLICATION_BOUNDS_WRITE);
        background.setCapability(Background.ALLOW_GEOMETRY_WRITE);
        background.setColor(new Color3f(0.04f, 0.08f, 0.15f)); // dark navy — shows through transparent water naturally

        skyBG = new BranchGroup();
        skyBG.setCapability(Group.ALLOW_CHILDREN_WRITE);
        skyBG.setCapability(Group.ALLOW_CHILDREN_EXTEND);

        // OrderedGroup ensures elements are rendered in the order they are added.
        // We want the veil to be rendered LAST so it covers the lower hemisphere of the sky/sun/moon.
        OrderedGroup mainOG = new OrderedGroup();
        skyBG.addChild(mainOG);

        dayTG = createSkyboxGeometry(folderPath, extension, fogColor, dayFaceTransparencies, dayOverlayTransparencies);
        mainOG.addChild(dayTG);

        background.setGeometry(skyBG);
    }

    /** Pre-builds the night sky geometry. Must be called before the scene is compiled. */
    public void preloadNightSky(String folderPath, String extension, Color3f nightFogColor) {
        nightTG = createSkyboxGeometry(folderPath, extension, nightFogColor, nightFaceTransparencies, nightOverlayTransparencies);
        // Start night sky as transparent
        for (TransparencyAttributes ta : nightFaceTransparencies) {
            ta.setTransparency(1.0f);
        }
        for (TransparencyAttributes ta : nightOverlayTransparencies) {
            ta.setTransparency(1.0f);
        }
        // Add to the OrderedGroup if it exists, otherwise directly to skyBG
        Node child = skyBG.getChild(0);
        if (child instanceof OrderedGroup) {
            ((OrderedGroup) child).addChild(nightTG);
        } else {
            skyBG.addChild(nightTG);
        }
    }

    /** Controls the cross-fade between day and night (0.0 = day, 1.0 = night). */
    public void setSkyMix(float mix) {
        float nightAlpha = Math.max(0f, Math.min(1f, mix));
        float dayAlpha   = 1f - nightAlpha;

        // Java3D: transparency=0.0 is opaque, transparency=1.0 is fully transparent.
        float dayTrans   = 1f - dayAlpha;
        float nightTrans = 1f - nightAlpha;

        for (TransparencyAttributes ta : dayFaceTransparencies) {
            ta.setTransparency(dayTrans);
        }
        for (TransparencyAttributes ta : nightFaceTransparencies) {
            ta.setTransparency(nightTrans);
        }

        if (fogVisible) {
            for (TransparencyAttributes ta : dayOverlayTransparencies) {
                ta.setTransparency(dayTrans);
            }
            for (TransparencyAttributes ta : nightOverlayTransparencies) {
                ta.setTransparency(nightTrans);
            }
        }
    }

    /** No longer needed as we use setSkyMix. Kept for compatibility if necessary. */
    public void setDaytime(boolean day) {
        setSkyMix(day ? 0f : 1f);
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

    /** No longer used as we cross-fade directly between cubemaps. */
    public void setVeilAlpha(float alpha) {}

    /**
     * Builds skybox geometry: textured cube faces + fog gradient overlays.
     * An OrderedGroup ensures fog overlays render on top of textured faces.
     */
    private TransformGroup createSkyboxGeometry(String folderPath, String ext, Color3f fogColor,
                                             List<TransparencyAttributes> faceTransList,
                                             List<TransparencyAttributes> overlayList) {
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

        // Root for this sky: allows rotation
        TransformGroup tg = new TransformGroup();
        tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);

        // OrderedGroup guarantees render order: textured faces, then fog overlays.
        OrderedGroup og = new OrderedGroup();
        tg.addChild(og);

        // --- 1. Textured faces ---
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

            QuadArray qa = new QuadArray(4, QuadArray.COORDINATES | QuadArray.TEXTURE_COORDINATE_2 | QuadArray.COLOR_4);
            for (int v = 0; v < 4; v++) {
                qa.setCoordinate(v, faceVerts[f][v]);
                qa.setTextureCoordinate(0, v, uvs[v]);
                float vy = faceVerts[f][v][1];
                // Fades out below horizon (y=0) to fully transparent at the bottom (y=-s)
                float alpha = Math.max(0.0f, Math.min(1.0f, (vy / s) + 1.0f));
                qa.setColor(v, new Color4f(1f, 1f, 1f, alpha));
            }

            Appearance app = new Appearance();
            app.setTexture(tex);
            TextureAttributes texAttr = new TextureAttributes();
            texAttr.setTextureMode(TextureAttributes.MODULATE);
            app.setTextureAttributes(texAttr);
            Material mat = new Material();
            mat.setLightingEnable(false);
            app.setMaterial(mat);
            PolygonAttributes pa = new PolygonAttributes();
            pa.setCullFace(PolygonAttributes.CULL_NONE);
            app.setPolygonAttributes(pa);

            // Add transparency to allow cross-fade (and vertex alpha to show Background.color)
            TransparencyAttributes tra = new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.0f);
            tra.setCapability(TransparencyAttributes.ALLOW_VALUE_WRITE);
            app.setTransparencyAttributes(tra);
            faceTransList.add(tra);

            og.addChild(new Shape3D(qa, app));
        }

        // --- 2. Fog gradient overlays (rendered after textured faces) ---
        if (fogColor != null) {
            float splitY = s * -0.05f;
            float topY   = s * 0.35f;

            int[] sideFaces = {0, 1, 4, 5};
            for (int f : sideFaces) {
                float[] b0 = faceVerts[f][0], b1 = faceVerts[f][1];
                float[] m0 = {b0[0], splitY, b0[2]}, m1 = {b1[0], splitY, b1[2]};
                float[] g0 = {b0[0], topY,   b0[2]}, g1 = {b1[0], topY,   b1[2]};
                addOverlayQuad(og, fogColor, 1f, 1f, 1f, 1f, b0, b1, m1, m0, overlayList);
                addOverlayQuad(og, fogColor, 1f, 1f, 0f, 0f, m0, m1, g1, g0, overlayList);
            }
        }

        return tg;
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
        // ALLOW_VALUE_WRITE lets setFogVisible hide the overlay by setting
        // transparency=1.0 (fully transparent) rather than switching to NONE mode.
        // NONE mode disables alpha blending entirely, making the grey quad opaque.
        ta.setCapability(TransparencyAttributes.ALLOW_VALUE_WRITE);
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

    private void addVeilQuad(OrderedGroup og, float[][] verts) {}

    /**
     * Loads sun and moon textures and adds them as billboard quads to the skybox.
     * Must be called before the scene is compiled (i.e. before universe.addBranchGraph).
     */
    public void addSunMoon(String sunPath, String moonPath) {
        sunTG  = createCelestialBody(sunPath, true);
        moonTG = createCelestialBody(moonPath, false);
        
        Node child = skyBG.getChild(0);
        if (child instanceof OrderedGroup) {
            OrderedGroup og = (OrderedGroup) child;
            if (sunTG  != null) og.addChild(sunTG);
            if (moonTG != null) og.addChild(moonTG);
        } else {
            if (sunTG  != null) skyBG.addChild(sunTG);
            if (moonTG != null) skyBG.addChild(moonTG);
        }
    }

    /**
     * Builds a single textured billboard quad positioned at (0, 0, -RADIUS) in local space.
     * Rotating this TransformGroup by rotX(phase) swings the quad through a vertical arc
     * while keeping its face pointing toward the origin (camera position in background space).
     */
    private TransformGroup createCelestialBody(String path, boolean isSun) {
        URL url = getClass().getResource(path);
        if (url == null) {
            System.err.println("Skybox: could not find celestial body texture: " + path);
            return null;
        }
        TextureLoader loader = new TextureLoader(url, "RGBA", null);
        Texture2D tex = (Texture2D) loader.getTexture();
        if (tex == null) {
            System.err.println("Skybox: failed to load celestial body texture: " + path);
            return null;
        }
        tex.setBoundaryModeS(Texture.CLAMP_TO_EDGE);
        tex.setBoundaryModeT(Texture.CLAMP_TO_EDGE);
        tex.setMagFilter(Texture.BASE_LEVEL_LINEAR);
        tex.setMinFilter(Texture.BASE_LEVEL_LINEAR);

        float r    = 0.45f;  // distance from origin — inside the skybox cube (faces at ~0.577)
        float size = 0.055f; // half-extent of the quad (~14° apparent diameter)

        if (!isSun) {size *= 0.65f; r *= 1f;}

        // Quad in the XY plane at z=-r, facing +Z (toward origin).
        // After rotX(phase), position and normal both rotate together, so the face
        // always points toward the origin regardless of elevation angle.
        QuadArray qa = new QuadArray(4, QuadArray.COORDINATES | QuadArray.TEXTURE_COORDINATE_2);
        qa.setCoordinate(0, new float[]{-size, -size, -r});
        qa.setCoordinate(1, new float[]{ size, -size, -r});
        qa.setCoordinate(2, new float[]{ size,  size, -r});
        qa.setCoordinate(3, new float[]{-size,  size, -r});
        qa.setTextureCoordinate(0, 0, new float[]{0f, 0f});
        qa.setTextureCoordinate(0, 1, new float[]{1f, 0f});
        qa.setTextureCoordinate(0, 2, new float[]{1f, 1f});
        qa.setTextureCoordinate(0, 3, new float[]{0f, 1f});

        Appearance app = new Appearance();
        app.setTexture(tex);
        TextureAttributes ta = new TextureAttributes();
        ta.setTextureMode(TextureAttributes.REPLACE);
        app.setTextureAttributes(ta);
        TransparencyAttributes tra = new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.0f);
        tra.setCapability(TransparencyAttributes.ALLOW_VALUE_WRITE);
        app.setTransparencyAttributes(tra);
        if (isSun) sunTrans = tra; else moonTrans = tra;
        
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);
        Material mat = new Material();
        mat.setLightingEnable(false);
        app.setMaterial(mat);

        TransformGroup tg = new TransformGroup();
        tg.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        tg.addChild(new Shape3D(qa, app));
        return tg;
    }

    /** Called each frame to rotate the active sky and update sun/moon positions. */
    public void update(double dt, double timeOfDay) {
        rotation += ROTATION_SPEED * dt;
        Transform3D t = new Transform3D();
        t.rotY(rotation);
        if (dayTG != null) dayTG.setTransform(t);
        if (nightTG != null) nightTG.setTransform(t);

        // Sun/moon arc: phase=0 at dawn (horizon, front), phase=π/2 at noon (zenith),
        // phase=π at dusk (horizon, back). Moon is always opposite the sun.
        double phase = (timeOfDay - 0.25) * 2.0 * Math.PI;
        
        // Elevation = sin(phase). If elevation < 0, the body is below the horizon.
        // We fade them out slightly before the horizon to avoid popping.
        float sunAlpha = (float) Math.sin(phase);
        float moonAlpha = (float) Math.sin(phase + Math.PI);

        if (sunTG != null) {
            Transform3D st = new Transform3D();
            st.rotX(phase);
            sunTG.setTransform(st);
            if (sunTrans != null) {
                // Fade out between elevation 0.0 and -0.1
                float trans = 1.0f - Math.max(0f, Math.min(1f, sunAlpha * 10f));
                sunTrans.setTransparency(trans);
            }
        }
        if (moonTG != null) {
            Transform3D mt = new Transform3D();
            mt.rotX(phase + Math.PI);
            moonTG.setTransform(mt);
            if (moonTrans != null) {
                float trans = 1.0f - Math.max(0f, Math.min(1f, moonAlpha * 10f));
                moonTrans.setTransparency(trans);
            }
        }
    }

    /** Shows or hides the fog gradient overlay on the currently active skybox. */
    public void setFogVisible(boolean visible) {
        this.fogVisible = visible;
        // This will be properly applied on the next setSkyMix call or immediately here for both.
        // For simplicity, we just trigger a refresh via a small hack or explicit loop.
        // Actually, we should just update all transparencies.
        for (TransparencyAttributes ta : dayOverlayTransparencies) {
            ta.setTransparency(visible ? 0.0f : 1.0f);
        }
        for (TransparencyAttributes ta : nightOverlayTransparencies) {
            ta.setTransparency(visible ? 0.0f : 1.0f);
        }
        // Then let setSkyMix override it with the correct blend.
    }

    public Background getBackground() {
        return background;
    }
}
