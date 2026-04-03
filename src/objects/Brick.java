package objects;

import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.geometry.Box;

public class Brick extends BaseObject {
    private Vector3f size;

    /**
     * Creates a cube with default size of 1.0
     */
    public Brick() {
        this(1f,1f,1f);
    }

    /**
     * Creates a cube with specified size
     */
    public Brick(float xSize, float ySize, float zSize) {
        super();
        this.size = new Vector3f(xSize, ySize, zSize);
        this.localAABB = new physics.AABB(xSize / 2f, ySize / 2f, zSize / 2f);
    }

    /**
     * Overrides getBranchGroup to add all 6 faces of the Box to the scene graph.
     */
    @Override
    public BranchGroup getBranchGroup() {
        Box box = new Box(size.x / 2.0f, size.y / 2.0f, size.z / 2.0f,
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
    public Vector3f getSize() {
        return size;
    }

    /**
     * Set the size of the cube (requires rebuilding geometry)
     */
    public void setSize(Vector3f size) {
        this.size = size;
        // Note: Changing size after creation requires rebuilding the scene graph
    }
}
