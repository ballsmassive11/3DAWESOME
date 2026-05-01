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
    private final float[]   colors;   // local chunk colors (RGBA)
    private final int rows;           // chunk row count
    private final int cols;           // chunk column count
    private final float cellSize;

    // World-space origin offset: positions are (r0+r - totalRows/2) * cellSize etc.
    private final int   r0, c0;
    private final float totalRows, totalCols; // stored as float to avoid repeated casts

    // When true, vertex local positions start at 0 (chunk-origin mode).
    // The object's TransformGroup is positioned at worldOriginX/Z.
    private final boolean chunkOrigin;

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    /** Full-grid constructor (original behaviour, unchanged). */
    public TerrainMesh(float[] heights, float[] colors, int rows, int cols, float cellSize) {
        this(heights, colors, rows, cols, 0, 0, rows, cols, cellSize);
    }


    /**
     * Standalone chunk constructor.
     * <p>
     * Builds a {@code rows × cols} terrain mesh whose vertex (r, c) sits at
     * local position {@code (c * cellSize, heights[r*cols+c], r * cellSize)}.
     * The object's world position is set to {@code (worldOriginX, 0, worldOriginZ)}
     * so that its (0,0) vertex lands at that exact world coordinate.
     * <p>
     * Adjacent chunks created with matching {@code worldOriginX/Z} offsets share
     * the same border vertex positions and therefore tile seamlessly.
     */
    public TerrainMesh(float[] heights, float[] colors,
                       int rows, int cols,
                       float cellSize,
                       float worldOriginX, float worldOriginZ) {
        super();
        this.r0        = 0;
        this.c0        = 0;
        this.rows      = rows;
        this.cols      = cols;
        this.cellSize  = cellSize;
        this.totalRows = 0;   // signals chunk-origin mode (positions start at 0, not centred)
        this.totalCols = 0;
        this.chunkOrigin = true;

        this.heights = heights.clone();
        this.colors  = colors.clone();
        setPosition(worldOriginX, 0, worldOriginZ);
    }

    /**
     * Subregion (chunk) constructor.
     * Builds geometry for the rectangle {@code [r0, r0+chunkRows) × [c0, c0+chunkCols)}
     * within a full grid of {@code totalRows × totalCols}, preserving the same world-space
     * vertex positions as if the full terrain were built in one piece.
     */
    public TerrainMesh(float[] heights, float[] colors,
                       int totalRows, int totalCols,
                       int r0, int c0, int chunkRows, int chunkCols,
                       float cellSize) {
        super();
        this.r0          = r0;
        this.c0          = c0;
        this.rows        = chunkRows;
        this.cols        = chunkCols;
        this.cellSize    = cellSize;
        this.totalRows   = totalRows;
        this.totalCols   = totalCols;
        this.chunkOrigin = false;

        // Copy the subregion so this chunk owns its own data
        this.heights = new float[chunkRows * chunkCols];
        this.colors  = new float[chunkRows * chunkCols * 4];
        for (int r = 0; r < chunkRows; r++) {
            for (int c = 0; c < chunkCols; c++) {
                int localIdx = r * chunkCols + c;
                int worldIdx = (r0 + r) * totalCols + (c0 + c);
                this.heights[localIdx] = heights[worldIdx];
                this.colors [localIdx * 4    ] = colors [worldIdx * 4    ];
                this.colors [localIdx * 4 + 1] = colors [worldIdx * 4 + 1];
                this.colors [localIdx * 4 + 2] = colors [worldIdx * 4 + 2];
                this.colors [localIdx * 4 + 3] = colors [worldIdx * 4 + 3];
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

        // Use flat float[] to avoid allocating a Point3f/Vector3f/TexCoord2f per vertex
        float[] posArr = new float[vertCount * 3];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int vi = (r * cols + c) * 3;
                if (chunkOrigin) {
                    // Chunk-origin mode: local positions start at (0,0).
                    // TransformGroup handles world placement via setPosition(worldOriginX, 0, worldOriginZ).
                    posArr[vi    ] = c * cellSize;
                    posArr[vi + 1] = heights[r * cols + c];
                    posArr[vi + 2] = r * cellSize;
                } else {
                    posArr[vi    ] = (c0 + c - totalCols / 2f) * cellSize;
                    posArr[vi + 1] = heights[r * cols + c];
                    posArr[vi + 2] = (r0 + r - totalRows / 2f) * cellSize;
                }
            }
        }

        float[] normArr = computeNormals(posArr);

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

        final float invTile = 1.0f / 4.0f;
        float[] texArr = new float[vertCount * 2];
        for (int i = 0; i < vertCount; i++) {
            texArr[i * 2    ] = posArr[i * 3    ] * invTile; // x / tileSize
            texArr[i * 2 + 1] = posArr[i * 3 + 2] * invTile; // z / tileSize
        }

        IndexedTriangleArray geom = new IndexedTriangleArray(
                vertCount,
                GeometryArray.COORDINATES | GeometryArray.NORMALS | GeometryArray.COLOR_4
                        | GeometryArray.TEXTURE_COORDINATE_2 | GeometryArray.BY_REFERENCE,
                indexCount
        );
        geom.setCoordRefFloat(posArr);
        geom.setNormalRefFloat(normArr);
        geom.setColorRefFloat(colors);
        geom.setTexCoordRefFloat(0, texArr);
        geom.setCoordinateIndices(0, indices);
        geom.setNormalIndices(0, indices);
        geom.setColorIndices(0, indices);
        geom.setTextureCoordinateIndices(0, 0, indices);

        return new Shape3D(geom);
    }

    // ------------------------------------------------------------------
    // Normal computation — uses flat float[] to avoid per-triangle allocation
    // ------------------------------------------------------------------

    private float[] computeNormals(float[] posArr) {
        int vertCount = rows * cols;
        float[] normArr = new float[vertCount * 3]; // zero-initialized

        for (int r = 0; r < rows - 1; r++) {
            for (int c = 0; c < cols - 1; c++) {
                int tl = r       * cols + c;
                int tr = r       * cols + (c + 1);
                int bl = (r + 1) * cols + c;
                int br = (r + 1) * cols + (c + 1);

                // Inline face normal for tri1: tl, bl, tr (avoids new Vector3f per call)
                int ai = tl * 3, bi = bl * 3, ci = tr * 3;
                float abx = posArr[bi]-posArr[ai], aby = posArr[bi+1]-posArr[ai+1], abz = posArr[bi+2]-posArr[ai+2];
                float acx = posArr[ci]-posArr[ai], acy = posArr[ci+1]-posArr[ai+1], acz = posArr[ci+2]-posArr[ai+2];
                float n1x = aby*acz - abz*acy, n1y = abz*acx - abx*acz, n1z = abx*acy - aby*acx;
                normArr[ai]  +=n1x; normArr[ai+1]+=n1y; normArr[ai+2]+=n1z;
                normArr[bi]  +=n1x; normArr[bi+1]+=n1y; normArr[bi+2]+=n1z;
                normArr[ci]  +=n1x; normArr[ci+1]+=n1y; normArr[ci+2]+=n1z;

                // Inline face normal for tri2: tr, bl, br
                ai = tr * 3; bi = bl * 3; ci = br * 3;
                abx = posArr[bi]-posArr[ai]; aby = posArr[bi+1]-posArr[ai+1]; abz = posArr[bi+2]-posArr[ai+2];
                acx = posArr[ci]-posArr[ai]; acy = posArr[ci+1]-posArr[ai+1]; acz = posArr[ci+2]-posArr[ai+2];
                float n2x = aby*acz - abz*acy, n2y = abz*acx - abx*acz, n2z = abx*acy - aby*acx;
                normArr[ai]  +=n2x; normArr[ai+1]+=n2y; normArr[ai+2]+=n2z;
                normArr[bi]  +=n2x; normArr[bi+1]+=n2y; normArr[bi+2]+=n2z;
                normArr[ci]  +=n2x; normArr[ci+1]+=n2y; normArr[ci+2]+=n2z;
            }
        }

        for (int i = 0; i < vertCount; i++) {
            int vi = i * 3;
            float nx = normArr[vi], ny = normArr[vi+1], nz = normArr[vi+2];
            float len2 = nx*nx + ny*ny + nz*nz;
            if (len2 > 0f) {
                float inv = 1f / (float) Math.sqrt(len2);
                normArr[vi] = nx*inv; normArr[vi+1] = ny*inv; normArr[vi+2] = nz*inv;
            } else {
                normArr[vi+1] = 1f; // default up
            }
        }
        return normArr;
    }
}
