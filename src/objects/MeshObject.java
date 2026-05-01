package objects;

import com.sun.j3d.loaders.Scene;
import com.sun.j3d.loaders.objectfile.ObjectFile;
import com.sun.j3d.utils.image.TextureLoader;
import javax.media.j3d.*;
import javax.vecmath.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class MeshObject extends BaseObject {
    private static final Map<String, BranchGroup> modelCache = new ConcurrentHashMap<>();

    private String modelPath;
    private BranchGroup modelRoot;
    private boolean useModelMaterials = false;

    /**
     * Pre-loads an OBJ file into the cache without creating a full MeshObject.
     * Safe to call from a background thread.
     */
    public static void preload(String modelPath) {
        if (!modelCache.containsKey(modelPath)) {
            loadFromFile(modelPath);
        }
    }

    /**
     * Loads a mesh from an OBJ file at the specified path.
     * @param modelPath The path to the .obj model file.
     */
    public MeshObject(String modelPath) {
        this(modelPath, false);
    }

    /**
     * Loads a mesh from an OBJ file at the specified path, optionally using its materials.
     * @param modelPath The path to the .obj model file.
     * @param useModelMaterials Whether to use materials from the OBJ file if available.
     */
    public MeshObject(String modelPath, boolean useModelMaterials) {
        super();
        this.modelPath = modelPath;
        this.useModelMaterials = useModelMaterials;
        loadModel();
    }

    private static BranchGroup loadFromFile(String modelPath) {
        Path tempFile = null;
        try {
            ObjectFile loader = new ObjectFile(ObjectFile.RESIZE);
            Path original = Paths.get(modelPath);
            String filtered = Files.lines(original)
                    .filter(line -> !line.startsWith("o ") && !line.equals("o"))
                    .map(line -> line.startsWith("usemtl ") ? "g " + line.substring(7) + "\n" + line : line)
                    .collect(Collectors.joining("\n", "", "\n"));
            tempFile = original.resolveSibling("_tmp_" + original.getFileName());
            Files.write(tempFile, filtered.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Scene scene = loader.load(tempFile.toFile().toURI().toURL());
            applyMtlAlphaMaps(scene, original);
            BranchGroup root = scene.getSceneGroup();
            applyMipmappingToGroup(root);
            modelCache.put(modelPath, root);
            return root;
        } catch (FileNotFoundException e) {
            System.err.println("Model file not found: " + modelPath);
        } catch (Exception e) {
            System.err.println("Error loading model: " + modelPath + " (" + e.getMessage() + ")");
            e.printStackTrace();
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private static void applyMtlAlphaMaps(Scene scene, Path objPath) {
        Map<String, MaterialMaps> materialMaps = readMaterialMaps(objPath);
        if (materialMaps.isEmpty()) return;

        Hashtable<?, ?> namedObjects = scene.getNamedObjects();
        for (Map.Entry<String, MaterialMaps> entry : materialMaps.entrySet()) {
            MaterialMaps maps = entry.getValue();
            if (maps.alphaPath == null && !textureHasAlpha(maps.diffusePath)) continue;

            Texture2D texture = createRgbaTexture(maps.diffusePath, maps.alphaPath);
            if (texture == null) continue;

            Object named = namedObjects.get(entry.getKey());
            if (named instanceof Shape3D) {
                replaceTexture((Shape3D) named, texture);
            } else if (materialMaps.size() == 1) {
                applyTextureToTexturedShapes(scene.getSceneGroup(), texture);
            }
        }
    }

    private static Map<String, MaterialMaps> readMaterialMaps(Path objPath) {
        Map<String, MaterialMaps> materialMaps = new HashMap<>();
        Path objDir = objPath.getParent();
        if (objDir == null) objDir = Paths.get(".");

        try {
            for (String line : Files.readAllLines(objPath, java.nio.charset.StandardCharsets.UTF_8)) {
                line = stripComment(line).trim();
                if (!line.startsWith("mtllib ")) continue;
                Path mtlPath = objDir.resolve(line.substring(7).trim()).normalize();
                readMaterialFile(mtlPath, materialMaps);
            }
        } catch (IOException e) {
            System.err.println("Warning: could not read OBJ materials for alpha maps: " + objPath);
        }
        return materialMaps;
    }

    private static void readMaterialFile(Path mtlPath, Map<String, MaterialMaps> materialMaps) {
        Path mtlDir = mtlPath.getParent();
        if (mtlDir == null) mtlDir = Paths.get(".");

        String currentName = null;
        try {
            for (String rawLine : Files.readAllLines(mtlPath, java.nio.charset.StandardCharsets.UTF_8)) {
                String line = stripComment(rawLine).trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("newmtl ")) {
                    currentName = line.substring(7).trim();
                    materialMaps.putIfAbsent(currentName, new MaterialMaps());
                    continue;
                }
                if (currentName == null) continue;

                MaterialMaps maps = materialMaps.get(currentName);
                if (line.startsWith("map_Kd ")) {
                    maps.diffusePath = resolveTexturePath(mtlDir, line.substring(7));
                } else if (line.startsWith("map_d ")) {
                    maps.alphaPath = resolveTexturePath(mtlDir, line.substring(6));
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: could not read material file: " + mtlPath);
        }
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    private static Path resolveTexturePath(Path baseDir, String mapArgs) {
        String[] tokens = mapArgs.trim().split("\\s+");
        if (tokens.length == 0) return null;
        return baseDir.resolve(tokens[tokens.length - 1]).normalize();
    }

    private static boolean textureHasAlpha(Path path) {
        if (path == null || !Files.exists(path)) return false;
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            return image != null && image.getColorModel().hasAlpha();
        } catch (IOException e) {
            return false;
        }
    }

    private static Texture2D createRgbaTexture(Path diffusePath, Path alphaPath) {
        if (diffusePath == null || !Files.exists(diffusePath)) return null;

        try {
            BufferedImage diffuse = ImageIO.read(diffusePath.toFile());
            if (diffuse == null) return null;

            BufferedImage rgba = new BufferedImage(
                    diffuse.getWidth(), diffuse.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < rgba.getHeight(); y++) {
                for (int x = 0; x < rgba.getWidth(); x++) {
                    rgba.setRGB(x, y, diffuse.getRGB(x, y));
                }
            }

            if (alphaPath != null && !alphaPath.equals(diffusePath) && Files.exists(alphaPath)) {
                BufferedImage alpha = ImageIO.read(alphaPath.toFile());
                if (alpha != null) {
                    mergeAlpha(rgba, alpha);
                }
            }

            Texture2D texture = (Texture2D) new TextureLoader(
                    rgba, "RGBA", TextureLoader.GENERATE_MIPMAP).getTexture();
            if (texture != null) {
                texture.setMinFilter(Texture.MULTI_LEVEL_LINEAR);
                texture.setMagFilter(Texture.BASE_LEVEL_LINEAR);
            }
            return texture;
        } catch (IOException e) {
            System.err.println("Warning: could not load alpha texture: " + diffusePath);
            return null;
        }
    }

    private static void mergeAlpha(BufferedImage rgba, BufferedImage alpha) {
        int width = rgba.getWidth();
        int height = rgba.getHeight();
        for (int y = 0; y < height; y++) {
            int ay = y * alpha.getHeight() / height;
            for (int x = 0; x < width; x++) {
                int ax = x * alpha.getWidth() / width;
                int alphaRgb = alpha.getRGB(ax, ay);
                int a = alpha.getColorModel().hasAlpha()
                        ? (alphaRgb >>> 24)
                        : (((alphaRgb >> 16) & 0xff) + ((alphaRgb >> 8) & 0xff) + (alphaRgb & 0xff)) / 3;
                rgba.setRGB(x, y, (a << 24) | (rgba.getRGB(x, y) & 0x00ffffff));
            }
        }
    }

    private static void replaceTexture(Shape3D shape, Texture2D texture) {
        Appearance app = shape.getAppearance();
        if (app != null && app.getTexture() != null) {
            app.setTexture(texture);
        }
    }

    private static void applyTextureToTexturedShapes(Group group, Texture2D texture) {
        java.util.Enumeration<?> children = group.getAllChildren();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof Shape3D) {
                replaceTexture((Shape3D) child, texture);
            } else if (child instanceof Group) {
                applyTextureToTexturedShapes((Group) child, texture);
            }
        }
    }

    private static class MaterialMaps {
        Path diffusePath;
        Path alphaPath;
    }

    private void loadModel() {
        BranchGroup cached = modelCache.get(modelPath);
        if (cached == null) {
            cached = loadFromFile(modelPath);
        }
        if (cached == null) return;

        modelRoot = (BranchGroup) cached.cloneTree();

        if (!useModelMaterials) {
            applyAppearanceToGroup(modelRoot, appearance);
        }

        polygonCount = countPolygons(modelRoot);
        loadModelProperties(modelPath);
    }

    /**
     * Reads a .properties file alongside the OBJ (same path, .properties extension).
     * Recognised keys: pivot.x, pivot.y, pivot.z.
     * Missing file or missing keys silently use the default (0, 0, 0).
     */
    private void loadModelProperties(String objPath) {
        String propsPath = objPath.replaceAll("\\.[^./\\\\]+$", ".properties");
        Path path = Paths.get(propsPath);
        if (!Files.exists(path)) return;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("Warning: could not read model properties: " + propsPath);
            return;
        }

        pivot.x = parseDouble(props, "pivot.x", pivot.x);
        pivot.y = parseDouble(props, "pivot.y", pivot.y);
        pivot.z = parseDouble(props, "pivot.z", pivot.z);
    }

    private static double parseDouble(Properties props, String key, double defaultVal) {
        String v = props.getProperty(key);
        if (v == null) return defaultVal;
        try { return Double.parseDouble(v.trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    /** Traverses a group tree and applies trilinear mipmap filters to every texture found,
     *  and ensures every Shape3D has a Material so lighting calculations apply. */
    private static void applyMipmappingToGroup(Group group) {
        java.util.Enumeration<?> children = group.getAllChildren();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof Shape3D) {
                Appearance app = ((Shape3D) child).getAppearance();
                if (app == null) continue;
                Texture tex = app.getTexture();
                if (tex != null) {
                    tex.setMinFilter(Texture.MULTI_LEVEL_LINEAR);
                    tex.setMagFilter(Texture.BASE_LEVEL_LINEAR);
                    // REPLACE mode (Java3D default) ignores lighting; MODULATE multiplies
                    // the texture by the lit material color so shading applies correctly.
                    if (app.getTextureAttributes() == null) {
                        TextureAttributes ta = new TextureAttributes();
                        ta.setTextureMode(TextureAttributes.MODULATE);
                        app.setTextureAttributes(ta);
                    }

                    // Enable transparency for textures that might have alpha (e.g. leaves, bushes).
                    // We use alpha testing (nicest for foliage to avoid sorting issues)
                    // and also enable BLENDED for smooth edges if needed.
                    if (app.getTransparencyAttributes() == null) {
                        TransparencyAttributes ta = new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.0f);
                        app.setTransparencyAttributes(ta);
                    }
                    if (app.getRenderingAttributes() == null) {
                        RenderingAttributes ra = new RenderingAttributes();
                        ra.setAlphaTestFunction(RenderingAttributes.GREATER);
                        ra.setAlphaTestValue(0.5f);
                        app.setRenderingAttributes(ra);
                    }
                }
                // Without a Material, Java3D disables lighting entirely (full brightness).
                // Add a default one so diffuse shading applies.
                if (app.getMaterial() == null) {
                    Material mat = new Material();
                    mat.setDiffuseColor(new Color3f(1.0f, 1.0f, 1.0f));
                    mat.setAmbientColor(new Color3f(0.2f, 0.2f, 0.2f));
                    mat.setSpecularColor(new Color3f(0.3f, 0.3f, 0.3f));
                    mat.setShininess(32.0f);
                    app.setMaterial(mat);
                }
            } else if (child instanceof Group) {
                applyMipmappingToGroup((Group) child);
            }
        }
    }

    private void applyAppearanceToGroup(Group group, Appearance app) {
        java.util.Enumeration<?> children = group.getAllChildren();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof Shape3D) {
                ((Shape3D) child).setAppearance(app);
            } else if (child instanceof Group) {
                applyAppearanceToGroup((Group) child, app);
            }
        }
    }

    @Override
    public BranchGroup getBranchGroup() {
        if (!geometryBuilt) {
            if (modelRoot != null) {
                if (modelRoot.getParent() != null) {
                    modelRoot.detach();
                }
                transformGroup.addChild(modelRoot);
            }
            branchGroup.addChild(transformGroup);
            addHitboxWireframe();
            updateTransform();
            geometryBuilt = true;
        }
        return branchGroup;
    }

    @Override
    protected Shape3D createGeometry() {
        // Since we are overriding getBranchGroup(), we don't use createGeometry()
        return null;
    }

    public String getModelPath() {
        return modelPath;
    }

    public boolean isUsingModelMaterials() {
        return useModelMaterials;
    }
}
