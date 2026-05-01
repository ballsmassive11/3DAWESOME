package terrain;

import entity.EntityPhysics;
import entity.Guy;
import objects.MeshObject;
import objects.TerrainMesh;
import particles.ParticleEmitter;
import physics.AABB;
import util.ProgressReporter;
import water.WaterTile;
import world.World;

import javax.media.j3d.BoundingBox;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Node;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages dynamic loading and unloading of terrain chunks around the player.
 *
 * <p>Each chunk covers {@value #CHUNK_SIZE_WORLD} × {@value #CHUNK_SIZE_WORLD} world
 * units and is generated in a background thread pool, then added to the scene on the
 * next {@link #update} call from the main render/update loop.
 *
 * <p>Adjacent chunks share border vertex positions, so the continuous noise sampling
 * ensures seamless terrain with no visible seams.
 */
public class ChunkManager {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Vertices per chunk side (= CHUNK_CELLS + 1 for shared borders). */
    public static final int   CHUNK_VERTS      = 33;
    /** World units per cell. */
    public static final float CELL_SIZE        = 1.6f;
    /** World units spanned by one chunk (32 cells × 0.8 = 25.6). */
    public static final float CHUNK_SIZE_WORLD = (CHUNK_VERTS - 1) * CELL_SIZE;

    /** Load chunks within this Manhattan/Chebyshev chunk-distance of the player. */
    private static final int RENDER_RADIUS = 7;
    /** Unload chunks farther than this Chebyshev distance. */
    private static final int UNLOAD_RADIUS = 9;

    /** World border expressed as a chunk count in each axis from the origin. */
    public static final int   BORDER_CHUNKS = 20;
    /** World radius in world units at which the border sits (≈ 512 WU). */
    public static final float BORDER_RADIUS = BORDER_CHUNKS * CHUNK_SIZE_WORLD;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final MapGenerator generator;
    private final World        world;

    /** Chunks currently live in the scene graph. */
    private final Map<ChunkCoord, ChunkEntry>       loaded    = new ConcurrentHashMap<>();
    /** Chunks whose background generation is in flight. */
    private final Set<ChunkCoord>                   queued    = ConcurrentHashMap.newKeySet();
    /** Chunks that have been generated and are waiting to be added to the scene. */
    private final ConcurrentLinkedQueue<PendingChunk> pending = new ConcurrentLinkedQueue<>();

    /**
     * Persists guy positions across load/unload cycles.  Entries are created on first
     * chunk generation and never removed until {@link #clearAll} is called.
     */
    private final Map<ChunkCoord, List<Guy>> chunkGuys = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
            r -> {
                Thread t = new Thread(r, "ChunkGen");
                t.setDaemon(true);
                // Below-normal priority so chunk generation doesn't compete with
                // the render thread for CPU time, avoiding frame-rate stutters.
                t.setPriority(Thread.NORM_PRIORITY - 2);
                return t;
            });

    /** Last known player chunk position. Updated when player crosses a chunk boundary. */
    private int lastPCX = Integer.MAX_VALUE;
    private int lastPCZ = Integer.MAX_VALUE;


    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public ChunkManager(MapGenerator generator, World world) {
        this.generator = generator;
        this.world     = world;
    }

    // -----------------------------------------------------------------------
    // Per-frame update (called from World.update)
    // -----------------------------------------------------------------------

    /**
     * Drains any pending chunks onto the scene, then evaluates whether new chunks
     * should be queued or distant ones unloaded.  Must be called from the render thread.
     */
    public void update(Vector3d playerPos) {
        drainPendingOneChunk();

        int pcx = worldToChunk(playerPos.x);
        int pcz = worldToChunk(playerPos.z);

        if (pcx == lastPCX && pcz == lastPCZ) return;
        lastPCX = pcx;
        lastPCZ = pcz;

        // Queue new chunks within the render radius (circular area).
        int rr = RENDER_RADIUS;
        for (int dz = -rr; dz <= rr; dz++) {
            for (int dx = -rr; dx <= rr; dx++) {
                if (dx * dx + dz * dz > rr * rr) continue;
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (Math.abs(cx) > BORDER_CHUNKS || Math.abs(cz) > BORDER_CHUNKS) continue;
                ChunkCoord coord = new ChunkCoord(cx, cz);
                if (!loaded.containsKey(coord) && queued.add(coord)) {
                    executor.submit(() -> buildChunkAsync(coord));
                }
            }
        }

        // Unload chunks beyond the unload radius.
        List<ChunkCoord> toUnload = new ArrayList<>();
        for (ChunkCoord c : loaded.keySet()) {
            if (Math.max(Math.abs(c.x - pcx), Math.abs(c.z - pcz)) > UNLOAD_RADIUS) {
                toUnload.add(c);
            }
        }
        for (ChunkCoord c : toUnload) {
            ChunkEntry entry = loaded.remove(c);
            if (entry != null) entry.removeFromWorld(world);
        }
    }

    // -----------------------------------------------------------------------
    // Synchronous preload (called during world generation / loading screen)
    // -----------------------------------------------------------------------

    /**
     * Synchronously generates all chunks within a circular radius around {@code center}
     * and adds them to the scene.  Intended for use on the loading-screen background thread.
     *
     * @param center   World position to load around (typically the player spawn point).
     * @param radius   Chunk radius to preload.
     * @param reporter Optional progress reporter (may be null).
     */
    public void preload(Vector3d center, int radius, ProgressReporter reporter) {
        int pcx = worldToChunk(center.x);
        int pcz = worldToChunk(center.z);

        List<ChunkCoord> toLoad = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (Math.abs(cx) > BORDER_CHUNKS || Math.abs(cz) > BORDER_CHUNKS) continue;
                ChunkCoord coord = new ChunkCoord(cx, cz);
                if (!loaded.containsKey(coord)) toLoad.add(coord);
            }
        }

        if (toLoad.isEmpty()) return;

        // Mark all as queued so the async system doesn't double-queue them.
        queued.addAll(toLoad);

        // Generate in parallel (the noise functions are pure and thread-safe).
        int total   = toLoad.size();
        int[] done  = {0};
        toLoad.parallelStream().forEach(coord -> {
            buildChunkAsync(coord);
            int n;
            synchronized (done) { n = ++done[0]; }
            if (reporter != null) {
                reporter.report(0.1f + (n / (float) total) * 0.65f,
                        "Loading chunks (" + n + "/" + total + ")...");
            }
        });

        // Add all pending chunks to scene on the calling thread.
        drainPending();

        lastPCX = pcx;
        lastPCZ = pcz;
    }

    // -----------------------------------------------------------------------
    // Internal: background chunk generation
    // -----------------------------------------------------------------------

    private void buildChunkAsync(ChunkCoord coord) {
        try {
            int   n   = CHUNK_VERTS;
            float wx0 = coord.x * CHUNK_SIZE_WORLD;
            float wz0 = coord.z * CHUNK_SIZE_WORLD;

            float[] heights   = new float[n * n];
            float[] colors    = new float[n * n * 4];
            float[] riverVals = new float[n * n];

            generator.buildChunkData(wx0, wz0, n, CELL_SIZE, heights, colors, riverVals);

            // All chunks share a single cached ShaderAppearance.  After the first chunk
            // makes it live, Java3D only performs a reference-count bump for subsequent
            // chunks — no per-chunk shader/texture initialisation, no render-thread lock.
            TerrainMesh mesh = new TerrainMesh(heights, colors, n, n, CELL_SIZE, wx0, wz0);
            mesh.setAppearance(generator.createChunkAppearance());
            mesh.getBranchGroup(); // triggers geometry construction and caches it

            List<WaterTile>   water = buildWaterTiles(wx0, wz0, n, heights);
            List<MeshObject>  trees = buildTrees(coord, wx0, wz0, n, heights, riverVals);
            List<ParticleEmitter> emitters = buildLeafEmitters(trees);

            // Guys persist across load/unload cycles; only create them on first visit.
            List<Guy> guys = chunkGuys.computeIfAbsent(coord,
                    k -> buildGuys(k, wx0, wz0, n, heights));

            // Pack terrain mesh + all trees into a single BranchGroup so the live scene
            // graph only needs one addChild call per chunk instead of 1+N_trees.
            // This is done here (off the render thread) so getBranchGroup() geometry
            // construction and the sub-tree assembly are already complete when we hand
            // the group to the main thread.
            BranchGroup chunkSceneBG = new BranchGroup();
            chunkSceneBG.setCapability(BranchGroup.ALLOW_DETACH);
            chunkSceneBG.setCapability(Node.ALLOW_AUTO_COMPUTE_BOUNDS_WRITE);
            chunkSceneBG.setCapability(Node.ALLOW_BOUNDS_WRITE);
            chunkSceneBG.addChild(mesh.getBranchGroup());
            for (MeshObject t : trees) chunkSceneBG.addChild(t.getBranchGroup());

            // Pre-set explicit bounds so Java3D does not need to traverse every vertex
            // in the subtree when this group is addChild'd to the live scene graph.
            // Bounds are computed here on the background thread instead.
            float chunkW = (n - 1) * CELL_SIZE;
            float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
            for (float h : heights) { if (h < minH) minH = h; if (h > maxH) maxH = h; }
            chunkSceneBG.setBoundsAutoCompute(false);
            chunkSceneBG.setBounds(new BoundingBox(
                    new Point3d(wx0,          minH,                       wz0),
                    new Point3d(wx0 + chunkW, maxH + TREE_SCALE_MAX * 2.0, wz0 + chunkW)));

            pending.add(new PendingChunk(coord, chunkSceneBG, water, trees, emitters, guys));
        } catch (Exception ex) {
            System.err.println("Chunk generation failed for " + coord + ": " + ex.getMessage());
        } finally {
            queued.remove(coord);
        }
    }

    // -----------------------------------------------------------------------
    // Internal: drain pending queue
    // -----------------------------------------------------------------------

    /**
     * Drains at most one pending chunk to the world per call.  Each chunk's terrain and
     * trees were pre-packed into a single BranchGroup off the render thread, so this
     * is at most two {@code addChild} calls on the live scene graph (chunk group + water),
     * keeping per-frame renderer sync cost minimal.
     */
    private void drainPendingOneChunk() {
        PendingChunk p = pending.poll();
        if (p == null) return;
        if (loaded.containsKey(p.coord)) return; // superseded
        ChunkEntry entry = new ChunkEntry(p);
        loaded.put(p.coord, entry);
        entry.addToWorld(world);
    }

    /** Drains all pending chunks at once (used during synchronous preload). */
    private void drainPending() {
        PendingChunk p;
        while ((p = pending.poll()) != null) {
            if (loaded.containsKey(p.coord)) continue;
            ChunkEntry entry = new ChunkEntry(p);
            loaded.put(p.coord, entry);
            entry.addToWorld(world);
        }
    }

    // -----------------------------------------------------------------------
    // Internal: water tiling
    // -----------------------------------------------------------------------

    private static List<WaterTile> buildWaterTiles(float wx0, float wz0, int n, float[] heights) {
        // Only tile water if the chunk contains any below-water vertices.
        boolean hasWater = false;
        for (float h : heights) {
            if (h < 0f) { hasWater = true; break; }
        }
        if (!hasWater) return Collections.emptyList();

        // One tile per chunk, sized to exactly cover it.  A tiny overlap (+0.02 WU per
        // side) closes the 1-pixel gap that would otherwise appear at shared edges.
        float chunkSize = (n - 1) * CELL_SIZE;
        float overlap = 0.02f;
        float tileSize = chunkSize + overlap * 2f;
        float cx = wx0 + chunkSize * 0.5f;
        float cz = wz0 + chunkSize * 0.5f;
        return Collections.singletonList(new WaterTile(cx, cz, tileSize));
    }

    // -----------------------------------------------------------------------
    // Internal: tree decoration
    // -----------------------------------------------------------------------

    private static final String TREE_PATH    = "resources/models/Tree/Lowpoly_tree_sample.obj";
    private static final float  TREE_DENSITY = 0.012f;
    private static final float  RIVER_WIDTH  = 0.15f;
    private static final double TREE_SCALE_MIN = 4.5;
    private static final double TREE_SCALE_MAX = 7.5;

    private static List<MeshObject> buildTrees(ChunkCoord coord,
                                                float wx0, float wz0, int n,
                                                float[] heights, float[] riverVals) {
        // Deterministic seed per chunk so re-generation produces the same trees.
        long seed = ((long) coord.x * 73856093L) ^ ((long) coord.z * 19349663L) ^ 77L;
        Random rng = new Random(seed);

        List<MeshObject> trees = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (rng.nextFloat() > TREE_DENSITY) continue;
                float h = heights[r * n + c];
                if (h < 0.1f) continue;
                if (riverVals[r * n + c] < RIVER_WIDTH) continue;

                float wx = wx0 + c * CELL_SIZE;
                float wz = wz0 + r * CELL_SIZE;
                double scale = TREE_SCALE_MIN + (TREE_SCALE_MAX - TREE_SCALE_MIN) * rng.nextDouble();

                MeshObject tree = new MeshObject(TREE_PATH, true);
                tree.setCollidable(true);
                tree.setScale(scale);
                tree.setPosition(wx, h, wz);

                float    radius = 0.08f;
                Vector3d piv    = tree.getPivot();
                tree.setLocalAABB(new AABB(
                        (float) piv.x - radius, (float) piv.y, (float) piv.z - radius,
                        (float) piv.x + radius, 0f, (float) piv.z + radius));
                tree.setRotationEuler(0, rng.nextFloat() * Math.PI * 2, 0);
                trees.add(tree);
            }
        }
        return trees;
    }

    private static List<ParticleEmitter> buildLeafEmitters(List<MeshObject> trees) {
        List<ParticleEmitter> emitters = new ArrayList<>(trees.size());
        for (MeshObject tree : trees) {
            Vector3d pos   = tree.getPosition();
            double   scale = tree.getScale().x; // uniform scale stored as x
            double   canopyY      = pos.y + scale * 1.8;
            float    canopyRadius = (float) (scale * 0.55f);
            emitters.add(new ParticleEmitter(pos.x, canopyY - 0.3, pos.z)
                    .setSpawnMode(ParticleEmitter.SpawnMode.BRICK)
                    .setBrickSize(canopyRadius * 2, canopyRadius * 0.4f, canopyRadius * 2)
                    .setEmissionRate(1.0)
                    .setPitch(-Math.PI / 2)
                    .setSpread(0.6)
                    .setSpeed(0.5)
                    .setStartColor(new javax.vecmath.Color4f(1f, 1f, 1f, 0.9f))
                    .setEndColor(new javax.vecmath.Color4f(1f, 1f, 1f, 0f))
                    .setStartSize((float) (scale * 0.12f))
                    .setEndSize((float) (scale * 0.06f))
                    .setLifetime(10.0f)
                    .setGravityScale(0.04f)
                    .setRotationSpeed((float) (Math.random() * 120f - 60f))
                    .setAtlasPath("resources/particles/leaf.png"));
        }
        return emitters;
    }

    // -----------------------------------------------------------------------
    // Internal: guy spawning
    // -----------------------------------------------------------------------

    /** Max guys spawned per chunk on first generation. */
    private static final int GUY_MAX_PER_CHUNK = 2;

    /**
     * Builds 0–{@value #GUY_MAX_PER_CHUNK} guys for a newly-generated chunk.
     * Only called once per chunk coord; subsequent loads reuse the returned list.
     */
    private static List<Guy> buildGuys(ChunkCoord coord,
                                       float wx0, float wz0, int n, float[] heights) {
        long seed = ((long) coord.x * 0xDEADBEEFL) ^ ((long) coord.z * 0xCAFEBABEL) ^ 0xF00DL;
        Random rng = new Random(seed);

        int count = rng.nextInt(GUY_MAX_PER_CHUNK + 1); // 0 to GUY_MAX_PER_CHUNK inclusive
        List<Guy> guys = new ArrayList<>(count);
        int tries = 0;
        while (guys.size() < count && tries < count * 8) {
            tries++;
            int r = 2 + rng.nextInt(n - 4);
            int c = 2 + rng.nextInt(n - 4);
            float h = heights[r * n + c];
            if (h < 0.5f) continue; // skip underwater / very low spots
            double gx = wx0 + c * CELL_SIZE;
            double gz = wz0 + r * CELL_SIZE;
            guys.add(new Guy(seed ^ tries, gx, gz, h));
        }
        return guys;
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    /**
     * Removes all loaded chunks from the world and resets state.
     * Call before replacing this manager (e.g., on {@code genmap}).
     */
    public void clearAll() {
        for (ChunkEntry entry : loaded.values()) {
            entry.removeFromWorld(world);
        }
        loaded.clear();
        queued.clear();
        pending.clear();
        chunkGuys.clear();
        lastPCX = Integer.MAX_VALUE;
        lastPCZ = Integer.MAX_VALUE;
    }

    /**
     * Shuts down the background thread pool.  The manager is unusable after this call.
     */
    public void shutdown() {
        executor.shutdownNow();
        clearAll();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Converts a world coordinate to a chunk index (floor division). */
    private static int worldToChunk(double w) {
        return (int) Math.floor(w / CHUNK_SIZE_WORLD);
    }

    // -----------------------------------------------------------------------
    // Inner data classes
    // -----------------------------------------------------------------------

    private static final class PendingChunk {
        final ChunkCoord            coord;
        final BranchGroup           chunkSceneBG; // terrain mesh + all trees pre-packed
        final List<WaterTile>       water;
        final List<MeshObject>      trees;
        final List<ParticleEmitter> emitters;
        final List<Guy>             guys;

        PendingChunk(ChunkCoord coord, BranchGroup chunkSceneBG,
                     List<WaterTile> water, List<MeshObject> trees,
                     List<ParticleEmitter> emitters, List<Guy> guys) {
            this.coord        = coord;
            this.chunkSceneBG = chunkSceneBG;
            this.water        = water;
            this.trees        = trees;
            this.emitters     = emitters;
            this.guys         = guys;
        }
    }

    private static final class ChunkEntry {
        final BranchGroup           chunkSceneBG;
        final List<WaterTile>       water;
        final List<MeshObject>      trees;
        final List<ParticleEmitter> emitters;
        final List<Guy>             guys;

        ChunkEntry(PendingChunk p) {
            this.chunkSceneBG = p.chunkSceneBG;
            this.water        = p.water;
            this.trees        = p.trees;
            this.emitters     = p.emitters;
            this.guys         = p.guys;
        }

        void addToWorld(World world) {
            // One addChild for the entire terrain + tree geometry.
            world.addSceneGroup(chunkSceneBG);
            // Water tiles need to go in the waterGroup for render ordering.
            for (WaterTile w : water)          world.addObject(w);
            // Trees are already in the scene via chunkSceneBG; only register for collision.
            for (MeshObject t : trees)         world.registerForCollision(t);
            for (ParticleEmitter e : emitters) world.addEmitter(e);
            // Guys: register in world on first ever load; re-attach model on subsequent loads.
            for (Guy g : guys) {
                g.setActive(true);
                if (!g.isInWorld()) {
                    world.addEntity(g);   // adds to entities list + attaches model
                    g.markInWorld();
                    g.setPlayerPosition(world.getPlayer().getPosition());
                } else {
                    world.addObject(g.getModel()); // re-attach previously detached model
                }
            }
        }

        void removeFromWorld(World world) {
            // Detaching chunkSceneBG removes terrain + all trees in one call.
            chunkSceneBG.detach();
            for (WaterTile w : water)          world.removeObject(w);
            for (MeshObject t : trees)         world.unregisterFromCollision(t);
            for (ParticleEmitter e : emitters) world.removeEmitter(e);
            // Deactivate guys and remove their models; their positions are preserved.
            for (Guy g : guys) {
                g.setActive(false);
                if (g.isInWorld()) world.removeObject(g.getModel());
            }
        }
    }
}
