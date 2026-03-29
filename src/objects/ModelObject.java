package objects;

import com.sun.j3d.loaders.Scene;
import com.sun.j3d.loaders.objectfile.ObjectFile;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.*;
import java.nio.file.*;

/**
 * A class for loading and displaying OBJ files in the 3D scene.
 */
public class ModelObject extends BaseObject {
    private String modelPath;
    private BranchGroup modelRoot;
    private boolean useModelMaterials = false;

    /**
     * Creates a ModelObject by loading an OBJ file from the specified path.
     * @param modelPath The path to the OBJ file.
     */
    public ModelObject(String modelPath) {
        this(modelPath, false);
    }

    /**
     * Creates a ModelObject by loading an OBJ file from the specified path.
     * @param modelPath The path to the OBJ file.
     * @param useModelMaterials If true, the materials defined in the model (e.g. MTL file) will be used.
     *                          If false, the default appearance from BaseObject will be applied.
     */
    public ModelObject(String modelPath, boolean useModelMaterials) {
        super();
        this.modelPath = modelPath;
        this.useModelMaterials = useModelMaterials;
        loadModel();
    }

    private void loadModel() {
        int flags = ObjectFile.RESIZE;
        if (useModelMaterials) {
            // Add flags to support loading materials and textures
            // ObjectFile.TRIANGULATE is often recommended for consistency
            // LOAD_ALL is needed to load all associated data (like textures)
            flags |= ObjectFile.TRIANGULATE | ObjectFile.STRIPIFY | 0x40; // 0x40 is ObjectFile.LOAD_ALL
        }

        ObjectFile loader = new ObjectFile(flags);
        loader.setBasePath(new File(modelPath).getParentFile().getAbsolutePath() + File.separator);
        try {
            String loadPath = preprocessObj(modelPath);
            Scene scene = loader.load(loadPath);
            modelRoot = scene.getSceneGroup();
            polygonCount = countPolygons(modelRoot);
            
            if (!useModelMaterials) {
                // Allow modification of appearance for all shapes in the model
                java.util.Enumeration<?> nodes = modelRoot.getAllChildren();
                while (nodes.hasMoreElements()) {
                    Object node = nodes.nextElement();
                    applyAppearance(node);
                }
            } else {
                // If using model materials, we still want to set capabilities so we can read them if needed
                enableAppearanceCapabilities(modelRoot);
            }
        } catch (IOException e) {
            System.err.println("Model file not found or I/O error: " + modelPath);
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error loading model: " + modelPath);
            e.printStackTrace();
        }
    }

    // Java3D's ObjectFile loader doesn't support the 'o' (object name) token from
    // newer Blender exports. This rewrites 'o <name>' lines as 'g <name>' in a
    // temp file so the loader can parse them without errors.
    private String preprocessObj(String path) throws IOException {
        File src = new File(path);
        boolean needsRewrite = false;
        for (String line : Files.readAllLines(src.toPath())) {
            if (line.startsWith("o ") || line.equals("o")) { needsRewrite = true; break; }
        }
        if (!needsRewrite) return path;

        File tmp = File.createTempFile("model_", ".obj", src.getParentFile());
        tmp.deleteOnExit();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(tmp))) {
            for (String line : Files.readAllLines(src.toPath())) {
                if (line.startsWith("o ") || line.equals("o")) {
                    w.write("g" + line.substring(1));
                } else {
                    w.write(line);
                }
                w.newLine();
            }
        }
        return tmp.getAbsolutePath();
    }

    private void applyAppearance(Object node) {
        if (node instanceof Shape3D) {
            Shape3D shape = (Shape3D) node;
            shape.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
            shape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
            shape.setAppearance(appearance);
        } else if (node instanceof Group) {
            java.util.Enumeration<?> children = ((Group) node).getAllChildren();
            while (children.hasMoreElements()) {
                applyAppearance(children.nextElement());
            }
        }
    }

    private void enableAppearanceCapabilities(Object node) {
        if (node instanceof Shape3D) {
            Shape3D shape = (Shape3D) node;
            shape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
            shape.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);

            // Enable read/write on Appearance attributes to support textures
            Appearance app = shape.getAppearance();
            if (app != null) {
                app.setCapability(Appearance.ALLOW_TEXTURE_READ);
                app.setCapability(Appearance.ALLOW_TEXTURE_WRITE);
                app.setCapability(Appearance.ALLOW_TEXTURE_ATTRIBUTES_READ);
                app.setCapability(Appearance.ALLOW_TEXTURE_ATTRIBUTES_WRITE);
                app.setCapability(Appearance.ALLOW_MATERIAL_READ);
                app.setCapability(Appearance.ALLOW_MATERIAL_WRITE);
            }
        } else if (node instanceof Group) {
            java.util.Enumeration<?> children = ((Group) node).getAllChildren();
            while (children.hasMoreElements()) {
                enableAppearanceCapabilities(children.nextElement());
            }
        }
    }

    @Override
    public BranchGroup getBranchGroup() {
        if (modelRoot != null) {
            // Add the loaded model group to our transformGroup
            transformGroup.addChild(modelRoot);
        }
        
        branchGroup.addChild(transformGroup);
        updateTransform();
        return branchGroup;
    }

    @Override
    protected Shape3D createGeometry() {
        // Geometry is loaded from the file and added in getBranchGroup
        return null;
    }
}
