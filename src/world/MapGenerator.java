package world;

import objects.Brick;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

public class MapGenerator {
    private final FastNoiseLite noise;
    private int gridSize = 60;
    private float spacing = 1.0f;
    private float threshold = -99990.0f;
    private float heightScale = 3.0f;
    private float zOffset = -10.0f;

    public MapGenerator() {
        noise = new FastNoiseLite();
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.1f);
    }

    public void generate(World world) {
        for (int ix = -gridSize / 2; ix < gridSize / 2; ix++) {
            for (int iy = -gridSize / 2; iy < gridSize / 2; iy++) {
                float nx = ix * spacing;
                float ny = iy * spacing;
                float noiseVal = noise.GetNoise(nx, ny); // range [-1, 1]

                if (noiseVal > threshold) {
                    float height = noiseVal * heightScale;

                    Brick brick = new Brick(1f, 3f, 1f);
                    brick.setPosition(nx, height, ny + zOffset);

                    float shade = 0.3f + (noiseVal + 1.0f) * 0.35f;

                    Appearance appearance = brick.getAppearance();
                    Material material = new Material();
                    material.setAmbientColor(new Color3f(0.2f, 0.6f, 0.1f));
                    material.setDiffuseColor(new Color3f(shade * 0.4f, shade * 0.8f, shade * 0.3f));
                    material.setSpecularColor(new Color3f(0.2f, 0.2f, 0.2f));
                    material.setShininess(50.0f);
                    appearance.setMaterial(material);
                    brick.setAppearance(appearance);

                    world.addObject(brick);
                }
            }
        }
    }

    public void setSeed(int seed) { noise.SetSeed(seed); }
    public void setGridSize(int gridSize) { this.gridSize = gridSize; }
    public void setSpacing(float spacing) { this.spacing = spacing; }
    public void setThreshold(float threshold) { this.threshold = threshold; }
    public void setHeightScale(float heightScale) { this.heightScale = heightScale; }
    public void setZOffset(float zOffset) { this.zOffset = zOffset; }
}
