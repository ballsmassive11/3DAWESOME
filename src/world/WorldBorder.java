package world;

import objects.Cube;

import javax.media.j3d.*;
import javax.vecmath.*;

/**
 * An impassable boundary at a fixed distance from the world origin.
 *
 * <p>The border is a square of half-width {@link #radius} centred on (0, 0).
 * It has two components:
 * <ol>
 *   <li><b>Visual walls</b> — four tall, semi-transparent, emissive cuboids rendered
 *       at each edge.  They glow purple so the player can see the boundary from a
 *       distance before hitting it.</li>
 *   <li><b>Physics enforcement</b> — {@link #enforce(Vector3d)} hard-clamps the
 *       player's XZ position so they can never cross the line.</li>
 * </ol>
 */
public class WorldBorder {

    private final float radius;

    /**
     * Creates the border and immediately adds the visual walls to {@code world}.
     *
     * @param radius Half-width of the square border in world units.
     * @param world  The world to add the wall objects to.
     */
    public WorldBorder(float radius, World world) {
        this.radius = radius;
        createWalls(world);
    }

    // -----------------------------------------------------------------------
    // Physics enforcement
    // -----------------------------------------------------------------------

    /**
     * Clamps {@code position} so that its XZ coordinates stay inside the border.
     *
     * @return {@code true} if the position was modified (the player hit the wall).
     */
    public boolean enforce(Vector3d position) {
        boolean hit = false;
        if (position.x >  radius) { position.x =  radius; hit = true; }
        if (position.x < -radius) { position.x = -radius; hit = true; }
        if (position.z >  radius) { position.z =  radius; hit = true; }
        if (position.z < -radius) { position.z = -radius; hit = true; }
        return hit;
    }

    /** @return The half-width radius of this border in world units. */
    public float getRadius() { return radius; }

    // -----------------------------------------------------------------------
    // Visual walls
    // -----------------------------------------------------------------------

    private void createWalls(World world) {
        float r  = radius;
        float h  = 120f;  // wall height (covers terrain + generous headroom)
        float t  = 4f;    // wall thickness
        float cy = 40f;   // wall centre Y  (wall spans cy-h/2 … cy+h/2)

        // Length of north/south walls (covers the east/west wall thickness at corners)
        float lenNS = r * 2f + t * 2f;
        // Length of east/west walls
        float lenEW = r * 2f + t * 2f;

        Appearance app = buildWallAppearance();

        // North (+Z)
        addWall(world, app,  0,  cy,  r + t * 0.5f,  lenNS, h, t);
        // South (-Z)
        addWall(world, app,  0,  cy, -(r + t * 0.5f), lenNS, h, t);
        // East  (+X)
        addWall(world, app,  r + t * 0.5f, cy, 0, t, h, lenEW);
        // West  (-X)
        addWall(world, app, -(r + t * 0.5f), cy, 0, t, h, lenEW);
    }

    private static void addWall(World world, Appearance app,
                                 double x, double y, double z,
                                 double sx, double sy, double sz) {
        Cube wall = new Cube(1f);
        wall.setAppearance(app);
        wall.setCollidable(false);
        wall.setScale(sx, sy, sz);
        wall.setPosition(x, y, z);
        world.addObject(wall);
    }

    private static Appearance buildWallAppearance() {
        Appearance app = new Appearance();

        Material mat = new Material();
        mat.setLightingEnable(true);
        mat.setEmissiveColor(new Color3f(0.35f, 0.0f, 0.95f));   // bright purple glow
        mat.setDiffuseColor (new Color3f(0.1f,  0.0f, 0.5f));
        mat.setAmbientColor (new Color3f(0.1f,  0.0f, 0.5f));
        mat.setSpecularColor(new Color3f(0.8f,  0.5f, 1.0f));
        mat.setShininess(25f);
        app.setMaterial(mat);

        // Semi-transparent so the player can see through (and look foreboding)
        TransparencyAttributes ta = new TransparencyAttributes(
                TransparencyAttributes.BLENDED, 0.38f);
        ta.setCapability(TransparencyAttributes.ALLOW_VALUE_READ);
        app.setTransparencyAttributes(ta);

        return app;
    }
}
