package objects;

import physics.AABB;

import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.geometry.*;
import java.util.Enumeration;

/**
 * Abstract base class for all 3D objects in the scene.
 * Provides common functionality for positioning, rotation, scaling, and rendering.
 */
public abstract class BaseObject {
    protected TransformGroup transformGroup;
    protected Transform3D transform;
    protected Vector3d position;
    protected Quat4d rotation;
    protected Vector3d scale;
    protected Appearance appearance;
    protected BranchGroup branchGroup;
    protected int polygonCount = 0;
    private Vector3d velocity;
    private Vector3d angularVelocity;

    // Collision / hitbox
    protected AABB localAABB = null;      // local-space AABB, null = no collision
    private boolean collidable = true;    // false = skip collision even if localAABB is set
    private RenderingAttributes hitboxRA; // controls wireframe visibility after compile

    // Pivot: model-space point that maps to the object's world position.
    // Applied as T(-pivot) after scale in the transform chain: T * R * S * T(-pivot).
    // Default (0,0,0) means the model origin is the world-position anchor.
    protected Vector3d pivot = new Vector3d(0.0, 0.0, 0.0);

    // True once getBranchGroup() has populated the scene sub-tree; subsequent calls
    // return the already-built branchGroup without rebuilding geometry.
    protected boolean geometryBuilt = false;

    // The built Shape3D, stored so applyAppearance() can update it after construction.
    protected Shape3D builtShape = null;

    /**
     * Constructor initializes the object with default values.
     */
    public BaseObject() {
        this.position = new Vector3d(0.0, 0.0, 0.0);
        this.rotation = new Quat4d(0.0, 0.0, 0.0, 1.0); // Identity quaternion (w=1)
        this.scale = new Vector3d(1.0, 1.0, 1.0);
        this.transform = new Transform3D();
        this.transformGroup = new TransformGroup();
        this.transformGroup.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        this.transformGroup.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
        this.branchGroup = new BranchGroup();
        this.branchGroup.setCapability(BranchGroup.ALLOW_DETACH);
        this.velocity = new Vector3d(0.0, 0.0, 0.0);
        this.angularVelocity = new Vector3d(0.0, 0.0, 0.0);

        initializeAppearance();
    }

    public int getPolygonCount() {
        return polygonCount;
    }

    protected static int countPolygons(Object node) {
        int count = 0;
        if (node instanceof Shape3D) {
            Shape3D shape = (Shape3D) node;
            for (int i = 0; i < shape.numGeometries(); i++) {
                count += polygonsInGeometry(shape.getGeometry(i));
            }
        } else if (node instanceof Group) {
            Enumeration<?> children = ((Group) node).getAllChildren();
            while (children.hasMoreElements()) {
                count += countPolygons(children.nextElement());
            }
        }
        return count;
    }

    private static int polygonsInGeometry(Geometry geom) {
        if (!(geom instanceof GeometryArray)) return 0;
        GeometryArray ga = (GeometryArray) geom;
        int v = (ga instanceof IndexedGeometryArray)
                ? ((IndexedGeometryArray) ga).getIndexCount()
                : ga.getVertexCount();
        if (geom instanceof TriangleArray || geom instanceof IndexedTriangleArray) {
            return v / 3;
        } else if (geom instanceof QuadArray || geom instanceof IndexedQuadArray) {
            return (v / 4) * 2;
        } else if (geom instanceof TriangleStripArray || geom instanceof TriangleFanArray
                || geom instanceof IndexedTriangleStripArray || geom instanceof IndexedTriangleFanArray) {
            return Math.max(0, v - 2);
        }
        return 0;
    }

    /**
     * Initialize default appearance (override for custom materials/colors).
     */
    protected void initializeAppearance() {
        appearance = new Appearance();
        Material material = new Material();
        material.setDiffuseColor(new Color3f(0.8f, 0.8f, 0.8f));
        material.setSpecularColor(new Color3f(1.0f, 1.0f, 1.0f));
        material.setShininess(64.0f);
        appearance.setMaterial(material);
    }

    /**
     * Abstract method to create the geometry for this object.
     * Must be implemented by all subclasses.
     */
    protected abstract Shape3D createGeometry();

    // ------------------------------------------------------------------
    // Collision / AABB
    // ------------------------------------------------------------------

    /** Set the local-space AABB for this object. Must be called before getBranchGroup(). */
    public void setLocalAABB(AABB aabb) { this.localAABB = aabb; }

    public AABB getLocalAABB() { return localAABB; }

    /** When false, this object is excluded from collision checks even if it has an AABB. */
    public void setCollidable(boolean collidable) { this.collidable = collidable; }

    /**
     * Returns the world-space AABB for this frame, or null if not collidable.
     * The local AABB is transformed by scale and rotation relative to the pivot,
     * then translated by the object's current position.
     */
    public AABB getWorldAABB() {
        if (!collidable || localAABB == null) return null;

        float sx = (float) scale.x, sy = (float) scale.y, sz = (float) scale.z;
        float px = (float) pivot.x, py = (float) pivot.y, pz = (float) pivot.z;

        // 1. Define the 8 corners of the local AABB relative to the pivot
        float x0 = (localAABB.minX - px) * sx;
        float x1 = (localAABB.maxX - px) * sx;
        float y0 = (localAABB.minY - py) * sy;
        float y1 = (localAABB.maxY - py) * sy;
        float z0 = (localAABB.minZ - pz) * sz;
        float z1 = (localAABB.maxZ - pz) * sz;

        Point3f[] corners = {
            new Point3f(x0, y0, z0), new Point3f(x1, y0, z0),
            new Point3f(x0, y1, z0), new Point3f(x1, y1, z0),
            new Point3f(x0, y0, z1), new Point3f(x1, y0, z1),
            new Point3f(x0, y1, z1), new Point3f(x1, y1, z1)
        };

        // 2. Rotate each corner using the current quaternion rotation
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        // Transform corners by rotation
        for (Point3f p : corners) {
            rotatePoint(p, rotation);
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x;
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y;
            if (p.z < minZ) minZ = p.z; if (p.z > maxZ) maxZ = p.z;
        }

        // 3. Translate by current world position
        return new AABB(
            minX + (float) position.x, minY + (float) position.y, minZ + (float) position.z,
            maxX + (float) position.x, maxY + (float) position.y, maxZ + (float) position.z
        );
    }

    /** Helper to rotate a point by a quaternion. */
    private void rotatePoint(Point3f p, Quat4d q) {
        // q * p * q^-1
        double qx = q.x, qy = q.y, qz = q.z, qw = q.w;
        double px = p.x, py = p.y, pz = p.z;

        double ix =  qw * px + qy * pz - qz * py;
        double iy =  qw * py + qz * px - qx * pz;
        double iz =  qw * pz + qx * py - qy * px;
        double iw = -qx * px - qy * py - qz * pz;

        p.x = (float) (ix * qw + iw * -qx + iy * -qz - iz * -qy);
        p.y = (float) (iy * qw + iw * -qy + iz * -qx - ix * -qz);
        p.z = (float) (iz * qw + iw * -qz + ix * -qy - iy * -qx);
    }

    /**
     * Show or hide the yellow wireframe hitbox overlay.
     * Safe to call at any time after getBranchGroup() has been invoked.
     */
    public void setHitboxVisible(boolean visible) {
        if (hitboxRA != null) hitboxRA.setVisible(visible);
    }

    /**
     * Adds a yellow wireframe matching localAABB as a child of transformGroup.
     * No-op if localAABB is null. Must be called inside getBranchGroup() AFTER
     * transformGroup has been populated (but before the scene is compiled).
     */
    protected void addHitboxWireframe() {
        if (localAABB == null) return;

        // Use local-space AABB coordinates so the wireframe is transformed
        // along with the object by transformGroup.
        float x0 = localAABB.minX, y0 = localAABB.minY, z0 = localAABB.minZ;
        float x1 = localAABB.maxX, y1 = localAABB.maxY, z1 = localAABB.maxZ;

        Point3f[] pts = {
            // Bottom face
            new Point3f(x0,y0,z0), new Point3f(x1,y0,z0),
            new Point3f(x1,y0,z0), new Point3f(x1,y0,z1),
            new Point3f(x1,y0,z1), new Point3f(x0,y0,z1),
            new Point3f(x0,y0,z1), new Point3f(x0,y0,z0),
            // Top face
            new Point3f(x0,y1,z0), new Point3f(x1,y1,z0),
            new Point3f(x1,y1,z0), new Point3f(x1,y1,z1),
            new Point3f(x1,y1,z1), new Point3f(x0,y1,z1),
            new Point3f(x0,y1,z1), new Point3f(x0,y1,z0),
            // Vertical edges
            new Point3f(x0,y0,z0), new Point3f(x0,y1,z0),
            new Point3f(x1,y0,z0), new Point3f(x1,y1,z0),
            new Point3f(x1,y0,z1), new Point3f(x1,y1,z1),
            new Point3f(x0,y0,z1), new Point3f(x0,y1,z1),
        };

        LineArray la = new LineArray(24, GeometryArray.COORDINATES);
        la.setCoordinates(0, pts);

        hitboxRA = new RenderingAttributes();
        hitboxRA.setCapability(RenderingAttributes.ALLOW_VISIBLE_WRITE);
        hitboxRA.setVisible(false);

        Appearance wireApp = new Appearance();
        wireApp.setColoringAttributes(
                new ColoringAttributes(1f, 1f, 0f, ColoringAttributes.FASTEST));
        wireApp.setLineAttributes(
                new LineAttributes(2f, LineAttributes.PATTERN_SOLID, true));
        wireApp.setRenderingAttributes(hitboxRA);

        // Attach to transformGroup so the wireframe follows the object's
        // position, rotation, and scale.
        transformGroup.addChild(new Shape3D(la, wireApp));
    }

    // ------------------------------------------------------------------

    /**
     * Build and return the scene graph for this object.
     */
    /** Detach this object's branch group from the live scene graph. */
    public void detachFromScene() {
        branchGroup.detach();
    }

    public BranchGroup getBranchGroup() {
        if (!geometryBuilt) {
            builtShape = createGeometry();
            polygonCount = countPolygons(builtShape);
            builtShape.setAppearance(appearance);
            transformGroup.addChild(builtShape);
            branchGroup.addChild(transformGroup);
            addHitboxWireframe();
            updateTransform();
            geometryBuilt = true;
        }
        return branchGroup;
    }

    /**
     * Re-applies the current {@link #appearance} to the built shape.
     * <p>
     * Use this when you want to defer binding a shared live {@code NodeComponent}
     * (e.g. a {@code ShaderAppearance} already referenced by live scene nodes) to
     * the main thread, avoiding lock contention from background generation threads.
     * Call after {@link #getBranchGroup()} and before the object is added to the
     * live scene.
     */
    public void applyAppearance() {
        if (builtShape != null) {
            builtShape.setAppearance(appearance);
        }
    }

    /**
     * Update the transform based on current position, rotation (quaternion), and scale.
     */
    protected void updateTransform() {
        transform.setIdentity();

        // Create individual transforms
        Transform3D translationTransform = new Transform3D();
        Transform3D rotationTransform = new Transform3D();
        Transform3D scaleTransform = new Transform3D();

        // Set translation
        translationTransform.setTranslation(position);

        // Set rotation using quaternion (no gimbal lock!)
        rotationTransform.setRotation(rotation);

        // Set scale
        scaleTransform.setScale(scale);

        // Combine transforms: T * R * S * T(-pivot)
        transform.mul(translationTransform);
        transform.mul(rotationTransform);
        transform.mul(scaleTransform);

        // Shift model so the pivot point lands at world position
        if (pivot.x != 0.0 || pivot.y != 0.0 || pivot.z != 0.0) {
            Transform3D pivotTransform = new Transform3D();
            pivotTransform.setTranslation(new Vector3d(-pivot.x, -pivot.y, -pivot.z));
            transform.mul(pivotTransform);
        }

        transformGroup.setTransform(transform);
    }

    // Position methods
    public void setPosition(double x, double y, double z) {
        position.set(x, y, z);
        updateTransform();
    }

    public void setPosition(Vector3d pos) {
        position.set(pos);
        updateTransform();
    }

    public Vector3d getPosition() {
        return new Vector3d(position);
    }

    // Rotation methods (using quaternions - no gimbal lock!)

    public Vector3d getPivot() {
        return new Vector3d(pivot);
    }

    /**
     * Set rotation using a quaternion directly.
     */
    public void setRotation(Quat4d quat) {
        rotation.set(quat);
        updateTransform();
    }

    /**
     * Set rotation using axis-angle representation.
     * @param axis The axis of rotation (will be normalized)
     * @param angle The angle in radians
     */
    public void setRotation(Vector3d axis, double angle) {
        AxisAngle4d axisAngle = new AxisAngle4d(axis, angle);
        rotation.set(axisAngle);
        updateTransform();
    }

    /**
     * Set rotation using Euler angles (converted to quaternion internally).
     * Note: While this accepts Euler angles for convenience, they're converted to quaternions
     * to avoid gimbal lock during interpolation and animation.
     * @param pitch Rotation around X axis in radians
     * @param yaw Rotation around Y axis in radians
     * @param roll Rotation around Z axis in radians
     */
    public void setRotationEuler(double pitch, double yaw, double roll) {
        // Convert Euler angles to quaternion
        Quat4d qx = new Quat4d();
        Quat4d qy = new Quat4d();
        Quat4d qz = new Quat4d();

        qx.set(new AxisAngle4d(1, 0, 0, pitch));
        qy.set(new AxisAngle4d(0, 1, 0, yaw));
        qz.set(new AxisAngle4d(0, 0, 1, roll));

        // Combine rotations: Z * Y * X
        rotation.set(qz);
        rotation.mul(qy);
        rotation.mul(qx);

        updateTransform();
    }

    /**
     * Get the current rotation as a quaternion.
     */
    public Quat4d getRotation() {
        return new Quat4d(rotation);
    }

    /**
     * Get the current rotation as axis-angle representation.
     */
    public AxisAngle4d getRotationAxisAngle() {
        AxisAngle4d axisAngle = new AxisAngle4d();
        axisAngle.set(rotation);
        return axisAngle;
    }

    // Scale methods
    public void setScale(double x, double y, double z) {
        scale.set(x, y, z);
        updateTransform();
    }

    public void setScale(double uniformScale) {
        scale.set(uniformScale, uniformScale, uniformScale);
        updateTransform();
    }

    public Vector3d getScale() {
        return new Vector3d(scale);
    }

    // Appearance methods
    public void setColor(Color3f color) {
        Material material = appearance.getMaterial();
        if (material == null) {
            material = new Material();
            appearance.setMaterial(material);
        }
        material.setDiffuseColor(color);
    }

    public void setShininess(float shininess) {
        Material material = appearance.getMaterial();
        if (material == null) {
            material = new Material();
            appearance.setMaterial(material);
        }
        material.setShininess(shininess);
    }

    public void setAppearance(Appearance app) {
        this.appearance = app;
    }

    public Appearance getAppearance() {
        return appearance;
    }

    // Utility methods
    public void translate(double dx, double dy, double dz) {
        position.add(new Vector3d(dx, dy, dz));
        updateTransform();
    }

    /**
     * Apply an incremental rotation using axis-angle.
     * This multiplies the current quaternion with a new rotation.
     * @param axis The axis of rotation
     * @param angle The angle in radians
     */
    public void rotate(Vector3d axis, double angle) {
        Quat4d deltaRotation = new Quat4d();
        deltaRotation.set(new AxisAngle4d(axis, angle));
        rotation.mul(deltaRotation);
        updateTransform();
    }

    /**
     * Apply an incremental rotation using Euler angles.
     * @param pitch Rotation around X axis in radians
     * @param yaw Rotation around Y axis in radians
     * @param roll Rotation around Z axis in radians
     */
    public void rotateEuler(double pitch, double yaw, double roll) {
        Quat4d qx = new Quat4d();
        Quat4d qy = new Quat4d();
        Quat4d qz = new Quat4d();

        qx.set(new AxisAngle4d(1, 0, 0, pitch));
        qy.set(new AxisAngle4d(0, 1, 0, yaw));
        qz.set(new AxisAngle4d(0, 0, 1, roll));

        // Combine delta rotations
        Quat4d deltaRotation = new Quat4d(qz);
        deltaRotation.mul(qy);
        deltaRotation.mul(qx);

        // Apply to current rotation
        rotation.mul(deltaRotation);
        updateTransform();
    }

    // Velocity methods

    public void setVelocity(double x, double y, double z) {
        velocity.set(x, y, z);
    }

    public void setVelocity(Vector3d v) {
        velocity.set(v);
    }

    public Vector3d getVelocity() {
        return new Vector3d(velocity);
    }

    // Angular velocity methods (radians per second around each axis)

    public void setAngularVelocity(double x, double y, double z) {
        angularVelocity.set(x, y, z);
    }

    public void setAngularVelocity(Vector3d av) {
        angularVelocity.set(av);
    }

    public Vector3d getAngularVelocity() {
        return new Vector3d(angularVelocity);
    }

    /**
     * Advance this object's position and rotation by deltaTime seconds.
     * Called each frame by the world update loop.
     */
    public void update(double deltaTime) {
        boolean changed = false;

        if (velocity.lengthSquared() > 0) {
            position.x += velocity.x * deltaTime;
            position.y += velocity.y * deltaTime;
            position.z += velocity.z * deltaTime;
            changed = true;
        }

        if (angularVelocity.lengthSquared() > 0) {
            Quat4d qx = new Quat4d();
            Quat4d qy = new Quat4d();
            Quat4d qz = new Quat4d();
            qx.set(new AxisAngle4d(1, 0, 0, angularVelocity.x * deltaTime));
            qy.set(new AxisAngle4d(0, 1, 0, angularVelocity.y * deltaTime));
            qz.set(new AxisAngle4d(0, 0, 1, angularVelocity.z * deltaTime));
            Quat4d delta = new Quat4d(qz);
            delta.mul(qy);
            delta.mul(qx);
            rotation.mul(delta);
            changed = true;
        }

        if (changed) {
            updateTransform();
        }
    }
}
