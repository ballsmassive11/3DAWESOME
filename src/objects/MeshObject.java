package objects;

import com.sun.j3d.loaders.Scene;
import com.sun.j3d.loaders.objectfile.ObjectFile;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.*;
import java.nio.file.*;
import java.util.stream.Collectors;

public class MeshObject extends BaseObject {
    private String modelPath;
    private BranchGroup modelRoot;
    private boolean useModelMaterials = false;

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

    private void loadModel() {
        Path tempFile = null;
        try {
            ObjectFile loader = new ObjectFile(ObjectFile.RESIZE);
            Path original = Paths.get(modelPath);
            String filtered = Files.lines(original)
                    .filter(line -> !line.startsWith("o ") && !line.equals("o"))
                    .map(line -> line.startsWith("usemtl ") ? "g " + line.substring(7) + "\n" + line : line)
                    .collect(Collectors.joining("\n", "", "\n"));
            // Write to a temp file in the same directory so relative MTL paths resolve correctly
            tempFile = original.resolveSibling("_tmp_" + original.getFileName());
            Files.write(tempFile, filtered.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Scene scene = loader.load(tempFile.toFile().toURI().toURL());
            modelRoot = scene.getSceneGroup();
            
            // If not using model materials, we should apply our BaseObject's appearance.
            // But Scene/ObjectFile might have nested Shape3D nodes with their own appearances.
            if (!useModelMaterials) {
                applyAppearanceToGroup(modelRoot, appearance);
            }
            
            // Calculate polygons
            polygonCount = countPolygons(modelRoot);
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
        if (modelRoot != null) {
            // Detach if already attached somewhere (though ObjectFile's scene group is usually detached initially)
            if (modelRoot.getParent() != null) {
                modelRoot.detach();
            }
            transformGroup.addChild(modelRoot);
        }
        branchGroup.addChild(transformGroup);
        updateTransform();
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
