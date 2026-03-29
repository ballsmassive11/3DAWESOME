package terrain;

import objects.Brick;
import javax.vecmath.Vector3d;

public class WaterHandler {
    private final Brick water;
    private final double baseY;
    private final double amplitude;
    private final double speed;
    private double time = 0;

    public WaterHandler(Brick water, double baseY) {
        this.water = water;
        this.baseY = baseY;
        this.amplitude = 0.08;
        this.speed = 0.1; // cycles per second
    }

    public void update(double deltaTime) {
        time += deltaTime;
        double y = baseY + amplitude * Math.sin(2 * Math.PI * speed * time);
        Vector3d pos = water.getPosition();
        water.setPosition(pos.x, y, pos.z);
    }
}
