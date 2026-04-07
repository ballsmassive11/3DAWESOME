package terrain;

import objects.Brick;
import javax.media.j3d.ShaderAttributeValue;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Vector3d;

public class WaterHandlerLegacy {
    private final Brick          water;   // non-null for Brick path (MapGeneratorLegacy)
    private final TransformGroup waterTG; // non-null for flat-quad path (MapGenerator)
    private final double baseY;
    private final double amplitude;
    private final double speed;
    private double time = 0;
    private final ShaderAttributeValue timeAttr; // null if shaders not loaded

    /** Brick path — used by MapGeneratorLegacy. */
    public WaterHandlerLegacy(Brick water, double baseY) {
        this.water     = water;
        this.waterTG   = null;
        this.baseY     = baseY;
        this.amplitude = 0.08;
        this.speed     = 0.1;
        this.timeAttr  = null;
    }

    /** Flat-quad path — used by MapGenerator. */
    public WaterHandlerLegacy(TransformGroup waterTG, double baseY, ShaderAttributeValue timeAttr) {
        this.water     = null;
        this.waterTG   = waterTG;
        this.baseY     = baseY;
        this.amplitude = 0.08;
        this.speed     = 0.1;
        this.timeAttr  = timeAttr;
    }

    public void update(double deltaTime) {
        time += deltaTime;
        double y = baseY + amplitude * Math.sin(2 * Math.PI * speed * time);

        if (water != null) {
            Vector3d pos = water.getPosition();
            water.setPosition(pos.x, y, pos.z);
        } else if (waterTG != null) {
            Transform3D t = new Transform3D();
            t.setTranslation(new Vector3d(0, y, 0));
            waterTG.setTransform(t);
        }

        if (timeAttr != null) {
            timeAttr.setValue(new Float((float) time));
        }
    }
}
