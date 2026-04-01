package objects;

import javax.media.j3d.*;
import javax.vecmath.*;

/**
 * A continuous height-displaced terrain mesh.
 * <p>
 * Vertices form a regular grid in X/Z, each displaced vertically by a height value.
 * The result is a smooth, wavy "sheet of paper" surface rather than a blocky grid.
 * Per-vertex colors and computed smooth normals are baked in at construction time.
 */
public class TerrainMesh extends BaseObject {

    private final float[] heights;   // row-major: heights[r * cols + c]
    private final Color3f[] colors;  // matching per-vertex colors
    private final int rows;
    private final int cols;
    private final float cellSize;    // spacing between vertices in X and Z

    public TerrainMesh(float[] heights, Color3f[] colors, int rows, int cols, float cellSize) {
        super();
        this.heights  = heights;
        this.colors   = colors;
        this.rows     = rows;
        this.cols     = cols;
        this.cellSize = cellSize;
    }

    // ------------------------------------------------------------------
    // BaseObject contract
    // ------------------------------------------------------------------

    @Override
    protected Shape3D createGeometry() {
        int vertCount  = rows * cols;
        int indexCount = (rows - 1) * (cols - 1) * 6; // 2 triangles × 3 indices per cell

        // Build vertex positions
        Point3f[] positions = new Point3f[vertCount];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x = (c - cols / 2f) * cellSize;
                float z = (r - rows / 2f) * cellSize;
                float y = heights[r * cols + c];
                positions[r * cols + c] = new Point3f(x, y, z);
            }
        }

        // Compute smooth (averaged) per-vertex normals
        Vector3f[] normals = computeNormals(positions);

        // Build triangle index list — each quad cell becomes 2 triangles
        //   tl --- tr
        //   |  \   |
        //   bl --- br
        //   tri1: tl, bl, tr
        //   tri2: tr, bl, br
        int[] indices = new int[indexCount];
        int idx = 0;
        for (int r = 0; r < rows - 1; r++) {
            for (int c = 0; c < cols - 1; c++) {
                int tl = r       * cols + c;
                int tr = r       * cols + (c + 1);
                int bl = (r + 1) * cols + c;
                int br = (r + 1) * cols + (c + 1);

                indices[idx++] = tl; indices[idx++] = bl; indices[idx++] = tr;
                indices[idx++] = tr; indices[idx++] = bl; indices[idx++] = br;
            }
        }

        IndexedTriangleArray geom = new IndexedTriangleArray(
                vertCount,
                GeometryArray.COORDINATES | GeometryArray.NORMALS | GeometryArray.COLOR_3,
                indexCount
        );
        geom.setCoordinates(0, positions);
        geom.setNormals(0, normals);
        geom.setColors(0, colors);
        geom.setCoordinateIndices(0, indices);
        geom.setNormalIndices(0, indices);
        geom.setColorIndices(0, indices);

        return new Shape3D(geom);
    }

    // ------------------------------------------------------------------
    // Normal computation
    // ------------------------------------------------------------------

    /**
     * For each vertex, accumulate the un-normalised face normals of all
     * triangles that share it, then normalise the sum.  This gives smooth
     * (Gouraud-shaded) normals across the whole mesh.
     */
    private Vector3f[] computeNormals(Point3f[] positions) {
        Vector3f[] normals = new Vector3f[positions.length];
        for (int i = 0; i < normals.length; i++) normals[i] = new Vector3f(0f, 0f, 0f);

        for (int r = 0; r < rows - 1; r++) {
            for (int c = 0; c < cols - 1; c++) {
                int tl = r       * cols + c;
                int tr = r       * cols + (c + 1);
                int bl = (r + 1) * cols + c;
                int br = (r + 1) * cols + (c + 1);

                Vector3f n1 = faceNormal(positions[tl], positions[bl], positions[tr]);
                normals[tl].add(n1); normals[bl].add(n1); normals[tr].add(n1);

                Vector3f n2 = faceNormal(positions[tr], positions[bl], positions[br]);
                normals[tr].add(n2); normals[bl].add(n2); normals[br].add(n2);
            }
        }

        for (Vector3f n : normals) {
            if (n.lengthSquared() > 0f) n.normalize();
            else                        n.set(0f, 1f, 0f); // flat fallback
        }
        return normals;
    }

    /** Cross product of two edge vectors gives the face normal. */
    private static Vector3f faceNormal(Point3f a, Point3f b, Point3f c) {
        float abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        float acx = c.x - a.x, acy = c.y - a.y, acz = c.z - a.z;
        return new Vector3f(
                aby * acz - abz * acy,
                abz * acx - abx * acz,
                abx * acy - aby * acx
        );
    }
}
