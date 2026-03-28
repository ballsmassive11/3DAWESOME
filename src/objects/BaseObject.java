package objects;

import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.geometry.*;

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
    private Vector3d velocity;
    private Vector3d angularVelocity;

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

    /**
     * Build and return the scene graph for this object.
     */
    public BranchGroup getBranchGroup() {
        Shape3D shape = createGeometry();
        shape.setAppearance(appearance);
        transformGroup.addChild(shape);
        branchGroup.addChild(transformGroup);
        updateTransform();
        return branchGroup;
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

        // Combine transforms: Scale -> Rotate -> Translate
        transform.mul(translationTransform);
        transform.mul(rotationTransform);
        transform.mul(scaleTransform);

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
