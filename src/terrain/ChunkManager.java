package terrain;

import entity.EntityPhysics;
import entity.Guy;
import objects.MeshObject;
import objects.StreetLamp;
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

            // Path generation for villages: check current and adjacent chunks
            for (int dxChunk = -1; dxChunk <= 1; dxChunk++) {
                for (int dzChunk = -1; dzChunk <= 1; dzChunk++) {
                    int neighborX = coord.x + dxChunk;
                    int neighborZ = coord.z + dzChunk;

                    long vSeed = ((long) neighborX * 0xABCDEF01L) ^ ((long) neighborZ * 0x12345678L) ^ 0x42L;
                    Random vRng = new Random(vSeed);

                    float neighborWx0 = neighborX * CHUNK_SIZE_WORLD;
                    float neighborWz0 = neighborZ * CHUNK_SIZE_WORLD;
                    float ncx = neighborWx0 + (n - 1) * CELL_SIZE * 0.5f;
                    float ncz = neighborWz0 + (n - 1) * CELL_SIZE * 0.5f;

                    if (generator.getBiomeAt(ncx, ncz).equals("Steppe") && vRng.nextFloat() < VILLAGE_PROBABILITY) {
                        float vcx = ncx + (vRng.nextFloat() - 0.5f) * 10f;
                        float vcz = ncz + (vRng.nextFloat() - 0.5f) * 10f;

                        for (int r = 0; r < n; r++) {
                            float wz = wz0 + r * CELL_SIZE;
                            for (int c = 0; c < n; c++) {
                                float wx = wx0 + c * CELL_SIZE;
                                float dx = wx - vcx;
                                float dz = wz - vcz;
                                float distSq = dx * dx + dz * dz;
                                float rInnerSq = (VILLAGE_RADIUS - 2.0f) * (VILLAGE_RADIUS - 2.0f);
                                float rOuterSq = (VILLAGE_RADIUS + 2.0f) * (VILLAGE_RADIUS + 2.0f);

                                if (distSq < rOuterSq) {
                                    float pathWeight = 1.0f;
                                    if (distSq > rInnerSq) {
                                        float dist = (float) Math.sqrt(distSq);
                                        pathWeight = 1.0f - (dist - (VILLAGE_RADIUS - 2.0f)) / 4.0f;
                                    }
                                    // Signal path by boosting B weight above 1.5 in the shader
                                    // Use Math.max to handle overlapping villages from different chunks
                                    int colorIdx = (r * n + c) * 4 + 2;
                                    float currentWeight = colors[colorIdx] > 1.5f ? colors[colorIdx] - 1.5f : 0f;
                                    colors[colorIdx] = 1.5f + Math.max(currentWeight, pathWeight);
                                }
                            }
                        }
                    }
                }
            }

            // All chunks share a single cached ShaderAppearance.  After the first chunk
            // makes it live, Java3D only performs a reference-count bump for subsequent
            // chunks — no per-chunk shader/texture initialisation, no render-thread lock.
            TerrainMesh mesh = new TerrainMesh(heights, colors, n, n, CELL_SIZE, wx0, wz0);
            mesh.setAppearance(generator.createChunkAppearance());
            mesh.getBranchGroup(); // triggers geometry construction and caches it

            List<WaterTile>   water = buildWaterTiles(wx0, wz0, n, heights);
            List<MeshObject>  trees = buildTrees(coord, wx0, wz0, n, heights, riverVals, generator);
            List<MeshObject>  bushes = buildBushes(coord, wx0, wz0, n, heights, riverVals, generator);
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
            for (MeshObject b : bushes) chunkSceneBG.addChild(b.getBranchGroup());

            VillageData village = buildVillage(coord, wx0, wz0, n, heights, generator);
            for (MeshObject s : village.shacks) {
                s.getBranchGroup(); // trigger OBJ load on background thread
                chunkSceneBG.addChild(s.getBranchGroup());
                trees.add(s); // Register shacks for collision tracking via the trees list
            }
            for (StreetLamp lamp : village.lamps) {
                lamp.addToGroup(chunkSceneBG);
                trees.add(lamp.getModel());
            }

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

    private static final String BUSH_PATH    = "resources/models/Bush/bush.obj";
    private static final float  BUSH_DENSITY_FOREST = 0.06f;
    private static final float  BUSH_DENSITY_MEADOW = 0.018f;
    private static final float  BUSH_DENSITY_STEPPE = 0.008f;
    private static final double BUSH_SCALE_MIN = 1.5;
    private static final double BUSH_SCALE_MAX = 2.2;

    // -----------------------------------------------------------------------
    // Village constants
    // -----------------------------------------------------------------------

    private static final String SHACK_PATH         = "resources/models/Shack/shack.obj";
    /** Fraction of steppe chunks that spawn a village (~1 in 12). */
    private static final float  VILLAGE_PROBABILITY = 0.06f;
    /** Radius around village center within which shacks are scattered. */
    private static final float  VILLAGE_RADIUS      = 34.0f;
    private static final int    SHACK_MIN           = 7;
    private static final int    SHACK_MAX           = 12;
    private static final double SHACK_SCALE_MIN     = 1.0;
    private static final double SHACK_SCALE_MAX     = 4.8;
    private static final int  LAMP_MIN    = 5;
    private static final int  LAMP_MAX    = 10;

    private static List<MeshObject> buildTrees(ChunkCoord coord,
                                                float wx0, float wz0, int n,
                                                float[] heights, float[] riverVals,
                                                MapGenerator generator) {
        // Deterministic seed per chunk so re-generation produces the same trees.
        long seed = ((long) coord.x * 73856093L) ^ ((long) coord.z * 19349663L) ^ 77L;
        Random rng = new Random(seed);

        List<MeshObject> trees = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                float wx = wx0 + c * CELL_SIZE;
                float wz = wz0 + r * CELL_SIZE;

                String biome = generator.getBiomeAt(wx, wz);
                float density = 0;
                if (biome.equals("Forest")) {
                    density = 0.045f;
                } else if (biome.equals("Steppe")) {
                    density = 0.005f;
                } else if (biome.equals("Mountain")) {
                    density = 0.004f;
                } else if (biome.equals("Tundra")) {
                    density = 0.001f;
                }

                if (density == 0 || rng.nextFloat() > density) continue;

                float h = heights[r * n + c];
                if (h < 0.1f) continue;
                if (riverVals[r * n + c] < RIVER_WIDTH) continue;

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

    private static List<MeshObject> buildBushes(ChunkCoord coord,
                                                float wx0, float wz0, int n,
                                                float[] heights, float[] riverVals,
                                                MapGenerator generator) {
        long seed = ((long) coord.x * 3949663L) ^ ((long) coord.z * 73856093L) ^ 123L;
        Random rng = new Random(seed);

        List<MeshObject> bushes = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                float wx = wx0 + c * CELL_SIZE;
                float wz = wz0 + r * CELL_SIZE;

                String biome = generator.getBiomeAt(wx, wz);
                float density = 0;
                if (biome.equals("Forest")) {
                    density = BUSH_DENSITY_FOREST;
                } else if (biome.equals("Steppe")) { // Meadow
                    density = BUSH_DENSITY_MEADOW;
                } else if (biome.equals("Tundra")) {
                    density = BUSH_DENSITY_STEPPE * 0.5f;
                }

                if (density == 0 || rng.nextFloat() > density) continue;

                float h = heights[r * n + c];
                if (h < 0.1f) continue;
                if (riverVals[r * n + c] < RIVER_WIDTH) continue;

                double scale = BUSH_SCALE_MIN + (BUSH_SCALE_MAX - BUSH_SCALE_MIN) * rng.nextDouble();

                MeshObject bush = new MeshObject(BUSH_PATH, true);
                bush.setCollidable(false);
                bush.setScale(scale);
                bush.setPosition(wx, h, wz);
                bush.setRotationEuler(0, rng.nextFloat() * Math.PI * 2, 0);
                bushes.add(bush);
            }
        }
        return bushes;
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
    // Internal: village generation
    // -----------------------------------------------------------------------

    private static VillageData buildVillage(ChunkCoord coord,
                                             float wx0, float wz0, int n,
                                             float[] heights, MapGenerator generator) {
        long seed = ((long) coord.x * 0xABCDEF01L) ^ ((long) coord.z * 0x12345678L) ^ 0x42L;
        Random rng = new Random(seed);

        // Only spawn villages in steppe biome (checked at chunk centre).
        float cx = wx0 + (n - 1) * CELL_SIZE * 0.5f;
        float cz = wz0 + (n - 1) * CELL_SIZE * 0.5f;
        if (!generator.getBiomeAt(cx, cz).equals("Steppe")) return VillageData.empty();
        if (rng.nextFloat() >= VILLAGE_PROBABILITY)         return VillageData.empty();

        // Village centre: near chunk centre with a small random offset.
        float vcx = cx + (rng.nextFloat() - 0.5f) * 10f;
        float vcz = cz + (rng.nextFloat() - 0.5f) * 10f;

        // Shacks scattered around the centre.
        int shackCount = SHACK_MIN + rng.nextInt(SHACK_MAX - SHACK_MIN + 1);
        List<MeshObject> shacks = new ArrayList<>(shackCount);
        for (int i = 0; i < shackCount; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist  = 2.0 + rng.nextDouble() * (VILLAGE_RADIUS - 2.0);
            double scale = SHACK_SCALE_MAX;
            double yaw   = rng.nextInt(4) * (Math.PI / 2);

            float sx = vcx + (float)(Math.sin(angle) * dist);
            float sz = vcz + (float)(Math.cos(angle) * dist);
            float h  = sampleHeight(sx, sz, wx0, wz0, n, heights);
            if (h < 0.5f) continue; // skip river/ocean spots

            MeshObject shack = new MeshObject(SHACK_PATH, true);
            shack.setCollidable(true);
            shack.setLocalAABB(AABB.offset(0.55f, 0.6f, 0.84f, 0f, -0.2f, -0.1f));
            shack.setScale(scale);
            shack.setPosition(sx, h, sz);
            shack.setRotationEuler(0, yaw, 0);
            shacks.add(shack);
        }

        // Street lamps interspersed in the village area.
        int lampCount = LAMP_MIN + rng.nextInt(LAMP_MAX-LAMP_MIN); // 2–4 lamps
        List<StreetLamp> lamps = new ArrayList<>(lampCount);
        for (int i = 0; i < lampCount; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist  = 1.5 + rng.nextDouble() * VILLAGE_RADIUS;
            float lx = vcx + (float)(Math.sin(angle) * dist);
            float lz = vcz + (float)(Math.cos(angle) * dist);
            float h  = sampleHeight(lx, lz, wx0, wz0, n, heights);
            if (h < 0.5f) continue;
            lamps.add(new StreetLamp(lx, h, lz));
        }

        return new VillageData(shacks, lamps);
    }

    /** Nearest-neighbour height lookup clamped to chunk bounds. */
    private static float sampleHeight(float wx, float wz,
                                       float wx0, float wz0, int n, float[] heights) {
        int c = Math.max(0, Math.min(n - 1, Math.round((wx - wx0) / CELL_SIZE)));
        int r = Math.max(0, Math.min(n - 1, Math.round((wz - wz0) / CELL_SIZE)));
        return heights[r * n + c];
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
    private List<Guy> buildGuys(ChunkCoord coord,
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
            double gx = wx0 + c * CELL_SIZE;
            double gz = wz0 + r * CELL_SIZE;

            if (h < 0.5f) continue; // skip underwater / very low spots
            // No trees or bushes on water (which no longer has its own biome name)
            float riverVal = generator.getRiverValAt((float) gx, (float) gz);
            if (riverVal < 0.15f) continue;
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

    private static final class VillageData {
        final List<MeshObject> shacks;
        final List<StreetLamp> lamps;
        VillageData(List<MeshObject> shacks, List<StreetLamp> lamps) {
            this.shacks = shacks;
            this.lamps  = lamps;
        }
        static VillageData empty() {
            return new VillageData(Collections.emptyList(), Collections.emptyList());
        }
    }

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
