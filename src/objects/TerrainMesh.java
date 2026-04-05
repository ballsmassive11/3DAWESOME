package objects;

import javax.media.j3d.*;
import javax.vecmath.*;

/**
 * A continuous height-displaced terrain mesh.
 * <p>
 * Vertices form a regular grid in X/Z, each displaced vertically by a height value.
 * The result is a smooth, wavy "sheet of paper" surface rather than a blocky grid.
 * Per-vertex colors and computed smooth normals are baked in at construction time.
 * <p>
 * The subregion constructor lets the terrain be split into smaller chunks so that
 * Java3D's per-object 8-light limit applies to a small local area instead of the
 * whole map, giving point lights a much better chance of illuminating nearby terrain.
 */
public class TerrainMesh extends BaseObject {

    private final float[]   heights;  // local chunk heights, row-major
    private final Color4f[] colors;   // local chunk colors
    private final int rows;           // chunk row count
    private final int cols;           // chunk column count
    private final float cellSize;

    // World-space origin offset: positions are (r0+r - totalRows/2) * cellSize etc.
    private final int   r0, c0;
    private final float totalRows, totalCols; // stored as float to avoid repeated casts

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    /** Full-grid constructor (original behaviour, unchanged). */
    public TerrainMesh(float[] heights, Color4f[] colors, int rows, int cols, float cellSize) {
        this(heights, colors, rows, cols, 0, 0, rows, cols, cellSize);
    }

    /**
     * Subregion (chunk) constructor.
     * Builds geometry for the rectangle {@code [r0, r0+chunkRows) × [c0, c0+chunkCols)}
     * within a full grid of {@code totalRows × totalCols}, preserving the same world-space
     * vertex positions as if the full terrain were built in one piece.
     */
    public TerrainMesh(float[] heights, Color4f[] colors,
                       int totalRows, int totalCols,
                       int r0, int c0, int chunkRows, int chunkCols,
                       float cellSize) {
        super();
        this.r0        = r0;
        this.c0        = c0;
        this.rows      = chunkRows;
        this.cols      = chunkCols;
        this.cellSize  = cellSize;
        this.totalRows = totalRows;
        this.totalCols = totalCols;

        // Copy the subregion so this chunk owns its own data
        this.heights = new float  [chunkRows * chunkCols];
        this.colors  = new Color4f[chunkRows * chunkCols];
        for (int r = 0; r < chunkRows; r++) {
            for (int c = 0; c < chunkCols; c++) {
                this.heights[r * chunkCols + c] = heights[(r0 + r) * totalCols + (c0 + c)];
                this.colors [r * chunkCols + c] = colors [(r0 + r) * totalCols + (c0 + c)];
            }
        }
    }

    // ------------------------------------------------------------------
    // BaseObject contract
    // ------------------------------------------------------------------

    @Override
    protected Shape3D createGeometry() {
        int vertCount  = rows * cols;
        int indexCount = (rows - 1) * (cols - 1) * 6; // 2 triangles × 3 indices per cell

        // Build vertex positions using full-grid centering so chunks tile seamlessly
        Point3f[] positions = new Point3f[vertCount];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x = (c0 + c - totalCols / 2f) * cellSize;
                float z = (r0 + r - totalRows / 2f) * cellSize;
                float y = heights[r * cols + c];
                positions[r * cols + c] = new Point3f(x, y, z);
            }
        }

        Vector3f[] normals = computeNormals(positions);

        //   tl --- tr
        //   |  \   |
        //   bl --- br
        //   tri1: tl, bl, tr   tri2: tr, bl, br
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

        final float texTileSize = 4.0f;
        TexCoord2f[] texCoords = new TexCoord2f[vertCount];
        for (int i = 0; i < vertCount; i++) {
            texCoords[i] = new TexCoord2f(
                    positions[i].x / texTileSize,
                    positions[i].z / texTileSize);
        }

        IndexedTriangleArray geom = new IndexedTriangleArray(
                vertCount,
                GeometryArray.COORDINATES | GeometryArray.NORMALS | GeometryArray.COLOR_4
                        | GeometryArray.TEXTURE_COORDINATE_2,
                indexCount
        );
        geom.setCoordinates(0, positions);
        geom.setNormals(0, normals);
        geom.setColors(0, colors);
        geom.setTextureCoordinates(0, 0, texCoords);
        geom.setCoordinateIndices(0, indices);
        geom.setNormalIndices(0, indices);
        geom.setColorIndices(0, indices);
        geom.setTextureCoordinateIndices(0, 0, indices);

        return new Shape3D(geom);
    }

    // ------------------------------------------------------------------
    // Normal computation
    // ------------------------------------------------------------------

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
            else                        n.set(0f, 1f, 0f);
        }
        return normals;
    }

    private static Vector3f faceNormal(Point3f a, Point3f b, Point3f c) {
        float abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        float acx = c.x - a.x, acy = c.y - a.y, acz = c.z - a.z;
        return new Vector3f(
                aby * acz - abz * acy,
                abz * acx - abx * acz,
                abx * acy - aby * acx);
    }
}
