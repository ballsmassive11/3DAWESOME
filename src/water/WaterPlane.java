package water;

import world.World;

import javax.media.j3d.*;
import javax.vecmath.*;

public class WaterPlane {

    public static final float WATER_SURFACE_Y = -0.1f;

    public static void create(World world, int gridSize, float zOffset) {
        QuadArray quad = new QuadArray(4, QuadArray.COORDINATES | QuadArray.NORMALS);

        float halfSize = gridSize / 2f;
        quad.setCoordinate(0, new Point3f(-halfSize, WATER_SURFACE_Y,  halfSize + zOffset));
        quad.setCoordinate(1, new Point3f( halfSize, WATER_SURFACE_Y,  halfSize + zOffset));
        quad.setCoordinate(2, new Point3f( halfSize, WATER_SURFACE_Y, -halfSize + zOffset));
        quad.setCoordinate(3, new Point3f(-halfSize, WATER_SURFACE_Y, -halfSize + zOffset));

        Vector3f normal = new Vector3f(0, 1, 0);
        for (int i = 0; i < 4; i++) quad.setNormal(i, normal);

        Appearance appearance = new Appearance();

        ColoringAttributes ca = new ColoringAttributes(0.1f, 0.35f, 0.8f, ColoringAttributes.SHADE_FLAT);
        appearance.setColoringAttributes(ca);

        TransparencyAttributes ta = new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.4f);
        appearance.setTransparencyAttributes(ta);

        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        appearance.setPolygonAttributes(pa);

        Shape3D shape = new Shape3D(quad, appearance);

        BranchGroup bg = new BranchGroup();
        bg.addChild(shape);
        world.addNode(bg);
    }
}
