package objects;

import physics.AABB;
import javax.media.j3d.*;
import javax.vecmath.*;

public class StreetLamp {

    private static final String MODEL_PATH     = "resources/models/StreetLamp/streetlamp.obj";
    private static final float  LIGHT_OFFSET_Y = 3.5f;  // estimated lamp-head height above ground
    private static final double LIGHT_RADIUS   = 14.0;

    private final MeshObject model;
    private final PointLight light;

    public StreetLamp(double x, double y, double z) {
        model = new MeshObject(MODEL_PATH, true);
        model.setPosition(x, y, z);
        model.setScale(4f);
        model.setCollidable(true);
        model.setLocalAABB(new AABB(0.10f, 1.0f, 0.10f));

        double headY = y + LIGHT_OFFSET_Y;
        light = new PointLight();
        light.setColor(new Color3f(1.0f, 0.85f, 0.45f));
        light.setPosition((float) x, (float) headY, (float) z);
        light.setAttenuation(1.0f, 0.10f, 0.0f);
        light.setInfluencingBounds(new BoundingSphere(new Point3d(x, headY, z), LIGHT_RADIUS));
    }

    /** Attaches the lamp model and point light to the given group. */
    public void addToGroup(BranchGroup group) {
        group.addChild(model.getBranchGroup());
        group.addChild(light);
    }

    public MeshObject getModel() {
        return model;
    }
}
