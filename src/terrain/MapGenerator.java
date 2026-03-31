package terrain;

import objects.Brick;
import util.FastNoiseLite;
import world.World;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.media.j3d.TransparencyAttributes;
import javax.vecmath.Color3f;

public class MapGenerator {
    private final FastNoiseLite noise;
    private final FastNoiseLite warpNoise;
    private int gridSize = 160;
    private float spacing = 1.0f;
    private float threshold = 0.05f;
    private float heightScale = 16.0f; //
    private float zOffset = -10.0f;
    private float blockWidth = 0.8f;

    public MapGenerator() {
        // Domain warp: displaces sample coordinates to produce twisty, organic shapes
        warpNoise = new FastNoiseLite();
        warpNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warpNoise.SetDomainWarpType(FastNoiseLite.DomainWarpType.OpenSimplex2Reduced);
        warpNoise.SetDomainWarpAmp(28f);
        warpNoise.SetFrequency(0.022f);
        warpNoise.SetFractalType(FastNoiseLite.FractalType.DomainWarpIndependent);
        warpNoise.SetFractalOctaves(4);

        // Main terrain: FBm layering for natural fractal detail
        noise = new FastNoiseLite();
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.038f);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFractalOctaves(5);
        noise.SetFractalLacunarity(2.0f);
        noise.SetFractalGain(0.4f);
    }

    public void generate(World world) {
        for (float ix = -gridSize / 2f; ix < gridSize / 2f; ix += blockWidth) {
            for (float iy = -gridSize / 2f; iy < gridSize / 2f; iy += blockWidth) {
                float nx = ix * spacing;
                float ny = iy * spacing;

                // Warp the coordinates before sampling — this is what makes it twisty
                FastNoiseLite.Vector2 coord = new FastNoiseLite.Vector2(nx, ny);
                warpNoise.DomainWarp(coord);

                float noiseVal = noise.GetNoise(coord.x, coord.y); // [-1, 1]

                float height;
                Color3f diffuse;
                if (noiseVal < threshold) {
                    // Below water level — sand slopes downward away from shore
                    float depth = Math.min((threshold - noiseVal) / (threshold + 1.0f), 1.0f);
                    height = -(float) Math.pow(depth, 1.5) * 4.0f;
                    diffuse = lerp(new Color3f(0.75f, 0.68f, 0.42f), new Color3f(0.30f, 0.26f, 0.10f), depth);
                } else {
                    // Normalize relative to threshold so t=0 is shore, t=1 is peak
                    float t = (noiseVal - threshold) / (1.0f - threshold);
                    height = (float) Math.pow(t, 2.5) * heightScale;
                    diffuse = terrainColor(t);
                }

                Brick brick = new Brick(blockWidth, 20f, blockWidth);
                brick.setPosition(nx, height, ny + zOffset);

                Color3f ambient = new Color3f(diffuse.x * 0.4f, diffuse.y * 0.4f, diffuse.z * 0.4f);
                Material material = new Material();
                material.setAmbientColor(ambient);
                material.setDiffuseColor(diffuse);
                material.setSpecularColor(new Color3f(0.12f, 0.12f, 0.12f));
                material.setShininess(25.0f);

                Appearance appearance = brick.getAppearance();
                appearance.setMaterial(material);
                brick.setAppearance(appearance);

                world.addObject(brick);
            }
        }

        Brick water = new Brick(gridSize, 40f, gridSize);
        water.setPosition(0, -35.1f, zOffset);

        Appearance appearance = water.getAppearance();
        Material material = new Material();
        material.setAmbientColor(new Color3f(0.0f, 0.0f, 0.5f));
        material.setDiffuseColor(new Color3f(0.0f, 0.0f, 0.5f));
        material.setSpecularColor(new Color3f(0.0f, 0.0f, 0.5f));
        material.setShininess(25.0f);

        appearance.setMaterial(material);
        appearance.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.5f));

        world.addObject(water);
        world.setWaterHandler(new WaterHandler(water, -10.2));
    }

    /** Height-based color gradient: sand → grass → forest → rock → snow */
    private Color3f terrainColor(float t) {
        if (t < 0.12f) {
            return lerp(new Color3f(0.78f, 0.71f, 0.44f), new Color3f(0.55f, 0.76f, 0.28f), t / 0.12f);
        } else if (t < 0.50f) {
            return lerp(new Color3f(0.55f, 0.76f, 0.28f), new Color3f(0.18f, 0.50f, 0.12f), (t - 0.12f) / 0.38f);
        } else if (t < 0.78f) {
            return lerp(new Color3f(0.18f, 0.50f, 0.12f), new Color3f(0.54f, 0.41f, 0.36f), (t - 0.50f) / 0.28f);
        } else {
            float blend = Math.min((t - 0.78f) / 0.22f, 1.0f);
            return lerp(new Color3f(0.44f, 0.41f, 0.36f), new Color3f(0.82f, 0.82f, 0.85f), blend);
        }
    }

    private static Color3f lerp(Color3f a, Color3f b, float t) {
        return new Color3f(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t
        );
    }

    public void setBlockWidth(float blockWidth) { this.blockWidth = blockWidth; }
    public void setSeed(int seed) {
        noise.SetSeed(seed);
        warpNoise.SetSeed(seed);
    }
    public void setGridSize(int gridSize) { this.gridSize = gridSize; }
    public void setSpacing(float spacing) { this.spacing = spacing; }
    public void setThreshold(float threshold) { this.threshold = threshold; }
    public void setHeightScale(float heightScale) { this.heightScale = heightScale; }
    public void setZOffset(float zOffset) { this.zOffset = zOffset; }
}
