package objects;

import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.geometry.Box;

public class Cube extends BaseObject {
    private float size;

    /**
     * Creates a cube with default size of 1.0
     */
    public Cube() {
        this(1.0f);
    }

    /**
     * Creates a cube with specified size
     * @param size The side length of the cube
     */
    public Cube(float size) {
        super();
        this.size = size;
        this.localAABB = new physics.AABB(size / 2f, size / 2f, size / 2f);
    }

    /**
     * Overrides getBranchGroup to add all 6 faces of the Box to the scene graph.
     */
    @Override
    public BranchGroup getBranchGroup() {
        Box box = new Box(size / 2.0f, size / 2.0f, size / 2.0f,
                         Box.GENERATE_NORMALS | Box.GENERATE_TEXTURE_COORDS,
                         appearance);
        transformGroup.addChild(box);
        branchGroup.addChild(transformGroup);
        addHitboxWireframe();
        updateTransform();
        return branchGroup;
    }

    @Override
    protected Shape3D createGeometry() {
        return null; // Not used; getBranchGroup() handles all faces
    }

    /**
     * Get the size of the cube
     */
    public float getSize() {
        return size;
    }

    /**
     * Set the size of the cube (requires rebuilding geometry)
     */
    public void setSize(float size) {
        this.size = size;
        // Note: Changing size after creation requires rebuilding the scene graph
    }
}
