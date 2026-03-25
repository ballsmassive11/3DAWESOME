package objects;

public class OscillatingCube extends Cube {
    private double totalTime;

    /**
     * Creates an oscillating cube with default size.
     */
    public OscillatingCube() {
        super();
        this.totalTime = 0.0;
    }

    /**
     * Creates an oscillating cube with specified size.
     * @param size The side length of the cube
     */
    public OscillatingCube(float size) {
        super(size);
        this.totalTime = 0.0;
    }

    @Override
    public void update(double deltaTime) {
        totalTime += deltaTime;

        // Change velocity based on sin and cos
        double vx = Math.sin(totalTime) * 2.0;
        double vy = Math.cos(totalTime * 1.5) * 1.5;
        double vz = Math.sin(totalTime * 0.5);
        setVelocity(vx, vy, vz);

        // Change rotation speed (angular velocity) based on sin and cos
        double avx = Math.cos(totalTime * 2.0) * 2.0;
        double avy = Math.sin(totalTime) * 1.5;
        double avz = Math.cos(totalTime * 0.7);
        setAngularVelocity(avx, avy, avz);

        // Call super to update position and rotation
        super.update(deltaTime);
    }
}
