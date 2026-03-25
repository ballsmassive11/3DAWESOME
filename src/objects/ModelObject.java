package objects;

import com.sun.j3d.loaders.Scene;
import com.sun.j3d.loaders.objectfile.ObjectFile;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.FileNotFoundException;

/**
 * A class for loading and displaying OBJ files in the 3D scene.
 */
public class ModelObject extends BaseObject {
    private String modelPath;
    private BranchGroup modelRoot;

    /**
     * Creates a ModelObject by loading an OBJ file from the specified path.
     * @param modelPath The path to the OBJ file.
     */
    public ModelObject(String modelPath) {
        super();
        this.modelPath = modelPath;
        loadModel();
    }

    private void loadModel() {
        ObjectFile loader = new ObjectFile(ObjectFile.RESIZE);
        try {
            Scene scene = loader.load(modelPath);
            modelRoot = scene.getSceneGroup();
            
            // Allow modification of appearance for all shapes in the model
            java.util.Enumeration<?> nodes = modelRoot.getAllChildren();
            while (nodes.hasMoreElements()) {
                Object node = nodes.nextElement();
                applyAppearance(node);
            }
        } catch (FileNotFoundException e) {
            System.err.println("Model file not found: " + modelPath);
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error loading model: " + modelPath);
            e.printStackTrace();
        }
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
