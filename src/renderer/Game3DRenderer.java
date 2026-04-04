package renderer;

import hud.CommandHud;
import hud.HudCanvas;
import objects.BaseObject;
import objects.Brick;
import objects.Cube;
import objects.MeshObject;
import physics.AABB;
import terrain.MapGeneratorLegacy;
import terrain.MapGenerator;

import world.*;

import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.universe.SimpleUniverse;
import com.sun.j3d.utils.universe.ViewingPlatform;
import physics.TerrainHeightProvider;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.awt.*;
import java.awt.event.*;

/**
 * Renderer class for displaying 3D scenes using Java3D
 */
public class Game3DRenderer {
    private HudCanvas canvas;
    private SimpleUniverse universe;
    private World world;
    private ViewingPlatform viewingPlatform;
    private TransformGroup viewTransformGroup;
    private double fov = Math.PI / 3.0; // 60 degrees default
    private double renderDistance = 10.0;
    private LinearFog fog;

    // 3rd-person orbit camera
    private double camOrbitRadius = 5.0;
    private static final double CAM_ZOOM_SPEED = 4.0;
    private static final double CAM_MIN_RADIUS = 1.0;
    private static final double CAM_MAX_RADIUS = 20.0;
    private final Set<Integer> zoomKeys = new HashSet<>();

    /**
     * Create a renderer for the given world
     * @param world The world to render
     */
    public Game3DRenderer(World world) {
        this.world = world;
        initializeRenderer();
    }

    /**
     * Initialize the Java3D rendering pipeline
     */
    private void initializeRenderer() {
        // Create a canvas for 3D rendering
        GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();
        canvas = new HudCanvas(config, world);
        canvas.setSize(800, 600);
        canvas.setFocusable(true);
        canvas.getCommandHud().addListener(event -> handleCommand(event.getText()));
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                CommandHud cmdHud = canvas.getCommandHud();

                // Let the command bar intercept the toggle key first
                if (cmdHud.handleToggle(e.getKeyCode())) return;

                // While the command bar is open, route all input there
                if (cmdHud.isActive()) {
                    cmdHud.keyPressed(e.getKeyCode());
                    return;
                }

                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
                int kc = e.getKeyCode();
                if (kc == KeyEvent.VK_I || kc == KeyEvent.VK_O) {
                    zoomKeys.add(kc);
                    return;
                }
                world.getCamera().keyPressed(kc);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                CommandHud cmdHud = canvas.getCommandHud();
                if (cmdHud.isActive()) {
                    cmdHud.keyTyped(e.getKeyChar());
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                zoomKeys.remove(e.getKeyCode());
                // Don't release camera keys while command bar is open so no
                // movement bleeds through when the bar closes.
                if (!canvas.getCommandHud().isActive()) {
                    world.getCamera().keyReleased(e.getKeyCode());
                }
            }
        });

        // Create the universe
        universe = new SimpleUniverse(canvas);
        universe.getViewer().getView().setFieldOfView(fov);
        universe.getViewer().getView().setBackClipDistance(renderDistance);   // render distance
        universe.getViewer().getView().setFrontClipDistance(0.05);

        // Setup the viewing platform
        viewingPlatform = universe.getViewingPlatform();
        viewTransformGroup = viewingPlatform.getViewPlatformTransform();

        // Set default camera position
        syncCamera();

        // Setup the scene
        setupScene();
    }

    /**
     * Called every frame with the elapsed time so zoom keys can update the radius.
     * Pass deltaTime = 0 when calling outside the game loop.
     */
    public void syncCamera(double deltaTime) {
        // --- Zoom (I = closer, O = farther) ---
        if (zoomKeys.contains(KeyEvent.VK_I))
            camOrbitRadius = Math.max(CAM_MIN_RADIUS, camOrbitRadius - CAM_ZOOM_SPEED * deltaTime);
        if (zoomKeys.contains(KeyEvent.VK_O))
            camOrbitRadius = Math.min(CAM_MAX_RADIUS, camOrbitRadius + CAM_ZOOM_SPEED * deltaTime);

        Camera cam = world.getCamera();
        double yaw   = cam.getYaw();
        double pitch = cam.getPitch();
        Vector3d lookAt = cam.getPosition(); // player eye / physics position

        // Orbit camera: camera sits on a sphere of radius r around lookAt.
        // Derivation — look direction D = (-sin(yaw)·cos(pitch), sin(pitch), -cos(yaw)·cos(pitch))
        // Camera pos = lookAt - D·r  →  camXYZ below.
        double sinY = Math.sin(yaw),  cosY = Math.cos(yaw);
        double sinP = Math.sin(pitch), cosP = Math.cos(pitch);

        double idealR = camOrbitRadius;
        double idealX = lookAt.x + sinY * cosP * idealR;
        double idealY = lookAt.y - sinP * idealR;
        double idealZ = lookAt.z + cosY * cosP * idealR;

        // Pull camera in if the ray to its ideal position is blocked
        double r = idealR * occlusionT(lookAt, idealX, idealY, idealZ);

        double camX = lookAt.x + sinY * cosP * r;
        double camY = lookAt.y - sinP * r;
        double camZ = lookAt.z + cosY * cosP * r;

        Transform3D transform = new Transform3D();
        Transform3D rotation  = new Transform3D();

        // RotY(yaw) * RotX(pitch) — camera faces the player
        transform.rotX(pitch);
        rotation.rotY(yaw);
        transform.mul(rotation, transform);

        transform.setTranslation(new Vector3d(camX, camY, camZ));
        viewTransformGroup.setTransform(transform);
    }

    /** Convenience overload for calls outside the game loop (e.g. initial setup). */
    public void syncCamera() { syncCamera(0); }

    /**
     * Casts a ray from {@code from} toward the ideal camera position and returns
     * the largest t ∈ (0, 1] such that the camera at {@code from + t*(ideal-from)}
     * is not inside terrain or any collidable AABB.  Returns 1.0 if nothing blocks.
     *
     * A small margin is subtracted so the camera sits just in front of surfaces.
     */
    private double occlusionT(Vector3d from, double idealX, double idealY, double idealZ) {
        double dx = idealX - from.x;
        double dy = idealY - from.y;
        double dz = idealZ - from.z;
        double minT = 1.0;

        // --- Terrain check: step along the ray, find first sample below ground ---
        TerrainHeightProvider terrain = world.getTerrainProvider();
        if (terrain != null) {
            int steps = 12;
            for (int i = 1; i <= steps; i++) {
                double t  = (double) i / steps;
                double px = from.x + dx * t;
                double py = from.y + dy * t;
                double pz = from.z + dz * t;
                if (py < terrain.getHeightAt((float) px, (float) pz)) {
                    minT = Math.min(minT, Math.max(0.05, t - 1.0 / steps));
                    break;
                }
            }
        }

        // --- AABB check: slab ray–box intersection for each collidable object ---
        for (BaseObject obj : world.getObjects()) {
            AABB box = obj.getWorldAABB();
            if (box == null) continue;

            double tEnter = 0.05; // ignore hits very close to the player
            double tExit  = minT;

            // X slab
            if (Math.abs(dx) < 1e-9) {
                if (from.x < box.minX || from.x > box.maxX) continue;
            } else {
                double t1 = (box.minX - from.x) / dx;
                double t2 = (box.maxX - from.x) / dx;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tEnter = Math.max(tEnter, t1);
                tExit  = Math.min(tExit,  t2);
                if (tEnter > tExit) continue;
            }

            // Y slab
            if (Math.abs(dy) < 1e-9) {
                if (from.y < box.minY || from.y > box.maxY) continue;
            } else {
                double t1 = (box.minY - from.y) / dy;
                double t2 = (box.maxY - from.y) / dy;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tEnter = Math.max(tEnter, t1);
                tExit  = Math.min(tExit,  t2);
                if (tEnter > tExit) continue;
            }

            // Z slab
            if (Math.abs(dz) < 1e-9) {
                if (from.z < box.minZ || from.z > box.maxZ) continue;
            } else {
                double t1 = (box.minZ - from.z) / dz;
                double t2 = (box.maxZ - from.z) / dz;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tEnter = Math.max(tEnter, t1);
                tExit  = Math.min(tExit,  t2);
                if (tEnter > tExit) continue;
            }

            // Ray hits this box before the current closest obstruction
            minT = Math.min(minT, Math.max(0.05, tEnter - 0.02));
        }

        return minT;
    }

    /**
     * Setup the scene with background and objects
     */
    private void setupScene() {
        // Create background
        Background background = new Background(world.getBackgroundColor());
        background.setApplicationBounds(new BoundingSphere(new Point3d(0, 0, 0), 100.0));

        BranchGroup sceneBG = world.getSceneBranchGroup();
        sceneBG.addChild(background);

        // Fog: fades geometry to sky color over the back half of the render distance
        fog = new LinearFog(world.getBackgroundColor(),
                            renderDistance * 0.5, renderDistance);
        fog.setCapability(LinearFog.ALLOW_COLOR_WRITE);
        fog.setCapability(LinearFog.ALLOW_DISTANCE_WRITE);
        fog.setInfluencingBounds(new BoundingSphere(new Point3d(0, 0, 0), Double.MAX_VALUE));
        sceneBG.addChild(fog);

        // Add world update behavior and pass renderer for camera sync
        WorldUpdateBehavior behavior = new WorldUpdateBehavior(world, this);
        sceneBG.addChild(behavior);

        // Compile for optimization
        sceneBG.compile();

        // Add to universe
        universe.addBranchGraph(sceneBG);
    }

    /**
     * Set the camera position and look-at point
     * @param cameraPos Position of the camera
     * @param lookAt Point the camera looks at
     */
    public void setCamera(Vector3d cameraPos, Point3d lookAt) {
        world.getCamera().setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
        // We don't have an easy way to set lookAt in Camera yet, but we can at least set position
        syncCamera();
    }

    public void updateHud(double fps, double x, double y, double z, double yaw, double pitch, int objects, int polygons, int seed, boolean flying) {
        canvas.updateStats(fps, x, y, z, yaw, pitch, objects, polygons, seed, flying);
    }

    public void setRenderDistance(double distance) {
        renderDistance = distance;
        universe.getViewer().getView().setBackClipDistance(renderDistance);
        if (fog != null) {
            fog.setFrontDistance(renderDistance * 0.5);
            fog.setBackDistance(renderDistance);
        }
    }

    /**
     * Get the Canvas3D for embedding in a frame or panel
     */
    public Canvas3D getCanvas() {
        return canvas;
    }

    /**
     * Get the universe for advanced manipulation
     */
    public SimpleUniverse getUniverse() {
        return universe;
    }

    public double getFov() {
        return fov;
    }

    public void setFov(double fovRadians) {
        this.fov = fovRadians;
        universe.getViewer().getView().setFieldOfView(fovRadians);
    }

    /** Handles text submitted via the CommandHud. */
    private void handleCommand(String text) {
        CommandHud hud = canvas.getCommandHud();
        String[] parts = text.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();

        if (cmd.equals("fly")) {
            boolean nowFlying = !world.getPlayer().getPhysics().isFlying();
            world.getPlayer().getPhysics().setFlying(nowFlying);
            hud.logOutput("Flight " + (nowFlying ? "ON  (Space=up, Shift=down)" : "OFF"));

        } else if (cmd.equals("fog") && parts.length == 2) {
            String arg = parts[1].trim().toLowerCase();
            if (arg.equals("on")) {
                fog.setFrontDistance(renderDistance * 0.5);
                fog.setBackDistance(renderDistance);
                hud.logOutput("Fog on.");
            } else if (arg.equals("off")) {
                fog.setFrontDistance(1e10);
                fog.setBackDistance(1e10);
                hud.logOutput("Fog off.");
            } else {
                hud.logOutput("Usage: fog on|off");
            }

        } else if (cmd.equals("fov") && parts.length == 2) {
            try {
                double degrees = Double.parseDouble(parts[1]);
                degrees = Math.max(10.0, Math.min(170.0, degrees));
                setFov(Math.toRadians(degrees));
                hud.logOutput("FOV set to " + (int) degrees + "°");
            } catch (NumberFormatException ignored) {
                hud.logOutput("Invalid value: " + parts[1]);
            }
        } else if (cmd.equals("rdist") && parts.length == 2) {
            try {
                double distance = Double.parseDouble(parts[1]);
                distance = Math.max(0.1, distance);
                setRenderDistance(distance);
                hud.logOutput("Render distance set to " + distance);
            } catch (NumberFormatException ignored) {
                hud.logOutput("Invalid value: " + parts[1]);
            }

        } else if (cmd.equals("fun")) {
            hud.logOutput("say less");

            List<BaseObject> objects = world.getObjects();
            for (BaseObject obj : objects) {
                int speed = 50;
                Vector3d Vec = new Vector3d((float)(Math.random()-0.5)*speed, (float)(Math.random()-0.5)*speed, (float)(Math.random()-0.5)*speed);
                obj.setAngularVelocity(Vec);

            }

        } else if (cmd.equals("genmap")) {
            String params = parts.length == 2 ? parts[1] : "";
            generateMap(hud, params);

        } else if (cmd.equals("genmapl")) {
            String params = parts.length == 2 ? parts[1] : "";
            generateMapLegacy(hud, params);

        } else if (cmd.equals("delmap")) {
            deleteMap(hud);

        } else if (cmd.equals("spawn")) {
            spawnObject(hud, parts.length == 2 ? parts[1] : "");

        } else if (cmd.equals("hitbox") && parts.length == 2) {
            String arg = parts[1].trim().toLowerCase();
            if (arg.equals("on")) {
                world.setHitboxVisible(true);
                hud.logOutput("Hitboxes visible.");
            } else if (arg.equals("off")) {
                world.setHitboxVisible(false);
                hud.logOutput("Hitboxes hidden.");
            } else {
                hud.logOutput("Usage: hitbox on|off");
            }

        } else if (cmd.equals("cmds") || cmd.equals("help")) {
            hud.logOutput("fly                     - Toggle flight (Space=up, Shift=down)");
            hud.logOutput("fog on|off              - Toggle distance fog");
            hud.logOutput("fov <degrees>           - Set field of view (10-170)");
            hud.logOutput("rdist <distance>        - Set render distance");
            hud.logOutput("genmap [key=val ...]    - Regenerate mesh terrain");
            hud.logOutput("  params: seed size height threshold cellsize");
            hud.logOutput("genmapl [key=val ...]   - Regenerate terrain (legacy brick mode)");
            hud.logOutput("  params: seed size height threshold blockwidth");
            hud.logOutput("delmap                  - Delete the current terrain");
            hud.logOutput("hitbox on|off           - Toggle AABB hitbox wireframes");
            hud.logOutput("spawn cube [key=val ...] - Spawn a cube   (size x y z collide)");
            hud.logOutput("spawn brick [key=val ...]- Spawn a brick  (w h d x y z collide)");
            hud.logOutput("spawn mesh [key=val ...] - Spawn a mesh   (path x y z aabbx aabby aabbz collide)");
            hud.logOutput("fun                     - would recommend turning render distance down");
            hud.logOutput("cmds / help             - Show this message");
        } else {
            hud.logOutput("Unknown command: " + cmd + "  (type 'cmds / help' for list)");
        }
    }

    /**
     * Regenerates the terrain using the new mesh-based MapGenerator.
     * Supported params: seed, size, height, threshold, cellsize
     * Example: genmap seed=42 size=120 height=24 threshold=0.1 cellsize=0.6
     */
    private void generateMap(CommandHud hud, String params) {
        int   seed      = (int) System.currentTimeMillis();
        int   size      = 160;
        float height    = 16.0f;
        float threshold = -0.1f;
        float cellSize  = 0.8f;

        for (String token : params.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            String[] kv = token.split("=", 2);
            if (kv.length != 2) { hud.logOutput("Skipping bad param: " + token); continue; }
            String key = kv[0].toLowerCase();
            String val = kv[1];
            try {
                switch (key) {
                    case "seed":      seed      = Integer.parseInt(val);  break;
                    case "size":      size      = Integer.parseInt(val);  break;
                    case "height":    height    = Float.parseFloat(val);  break;
                    case "threshold": threshold = Float.parseFloat(val);  break;
                    case "cellsize":  cellSize  = Float.parseFloat(val);  break;
                    default: hud.logOutput("Unknown param: " + key); break;
                }
            } catch (NumberFormatException e) {
                hud.logOutput("Invalid value for " + key + ": " + val);
                return;
            }
        }

        final int fSeed = seed, fSize = size;
        final float fHeight = height, fThreshold = threshold, fCellSize = cellSize;

        hud.logOutput("Generating mesh map (seed=" + fSeed + " size=" + fSize
                + " height=" + fHeight + " threshold=" + fThreshold + ") ...");

        Thread t = new Thread(() -> {
            canvas.stopRenderer();
            try {
                world.clearObjects();

                MapGenerator gen = new MapGenerator();
                gen.setSeed(fSeed);
                gen.setGridSize(fSize);
                gen.setHeightScale(fHeight);
                gen.setThreshold(fThreshold);
                gen.setCellSize(fCellSize);
                gen.generate(world);

                world.setSeed(fSeed);
            } finally {
                canvas.startRenderer();
            }
            hud.logOutput("Map ready.");
        }, "genmap-thread");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Regenerates the terrain using the legacy brick-based MapGeneratorLegacy.
     * Supported params: seed, size, height, threshold, blockwidth
     * Example: genmapL seed=42 size=120 height=24 threshold=0.1 blockwidth=0.6
     */
    private void generateMapLegacy(CommandHud hud, String params) {
        int   seed       = (int) System.currentTimeMillis();
        int   size       = 160;
        float height     = 16.0f;
        float threshold  = 0.05f;
        float blockWidth = 0.8f;

        for (String token : params.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            String[] kv = token.split("=", 2);
            if (kv.length != 2) { hud.logOutput("Skipping bad param: " + token); continue; }
            String key = kv[0].toLowerCase();
            String val = kv[1];
            try {
                switch (key) {
                    case "seed":       seed       = Integer.parseInt(val);  break;
                    case "size":       size       = Integer.parseInt(val);  break;
                    case "height":     height     = Float.parseFloat(val);  break;
                    case "threshold":  threshold  = Float.parseFloat(val);  break;
                    case "blockwidth": blockWidth = Float.parseFloat(val);  break;
                    default: hud.logOutput("Unknown param: " + key); break;
                }
            } catch (NumberFormatException e) {
                hud.logOutput("Invalid value for " + key + ": " + val);
                return;
            }
        }

        final int fSeed = seed, fSize = size;
        final float fHeight = height, fThreshold = threshold, fBlockWidth = blockWidth;

        hud.logOutput("Generating legacy map (seed=" + fSeed + " size=" + fSize
                + " height=" + fHeight + " threshold=" + fThreshold + ") ...");

        Thread t = new Thread(() -> {
            canvas.stopRenderer();
            try {
                world.clearObjects();

                MapGeneratorLegacy gen = new MapGeneratorLegacy();
                gen.setSeed(fSeed);
                gen.setGridSize(fSize);
                gen.setHeightScale(fHeight);
                gen.setThreshold(fThreshold);
                gen.setBlockWidth(fBlockWidth);
                gen.generate(world);

                world.setSeed(fSeed);
            } finally {
                canvas.startRenderer();
            }
            hud.logOutput("Map ready.");
        }, "genmapl-thread");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Parses and dispatches a spawn command.
     *
     * spawn cube  [size=1]  [x] [y] [z]  [collide=true]
     * spawn brick [w=1] [h=1] [d=1]  [x] [y] [z]  [collide=true]
     * spawn mesh  path=<file>  [x] [y] [z]  [aabbx] [aabby] [aabbz]  [collide=true]
     *
     * Default position is 3 units in front of the player at foot level.
     * For mesh objects, aabbx/aabby/aabbz set the AABB half-extents (opt).
     * collide=false disables AABB collision for the spawned object.
     */
    private void spawnObject(CommandHud hud, String args) {
        String[] tokens = args.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            hud.logOutput("Usage: spawn cube|brick|mesh [params]");
            return;
        }

        String type = tokens[0].toLowerCase();

        // Default spawn position: 3 units in front of player at foot level
        Camera cam = world.getCamera();
        double defaultX = cam.getPosition().x + Math.sin(cam.getYaw()) * 3.0;
        double defaultY = cam.getPosition().y - entity.EntityPhysics.EYE_HEIGHT;
        double defaultZ = cam.getPosition().z - Math.cos(cam.getYaw()) * 3.0;

        Map<String, String> kv = parseKVTokens(hud, tokens, 1);

        switch (type) {
            case "cube": {
                float size    = getFloat(kv, "size", 1.0f);
                double x      = getDouble(kv, "x", defaultX);
                double y      = getDouble(kv, "y", defaultY + size / 2.0);
                double z      = getDouble(kv, "z", defaultZ);
                boolean col   = getBool(kv, "collide", true);

                Cube cube = new Cube(size);
                cube.setCollidable(col);
                cube.setPosition(x, y, z);
                world.addObject(cube);
                hud.logOutput("Spawned cube (size=" + size + ", collide=" + col + ") at ("
                        + fmt(x) + ", " + fmt(y) + ", " + fmt(z) + ")");
                break;
            }
            case "brick": {
                float w     = getFloat(kv, "w", 1.0f);
                float h     = getFloat(kv, "h", 1.0f);
                float d     = getFloat(kv, "d", 1.0f);
                double x    = getDouble(kv, "x", defaultX);
                double y    = getDouble(kv, "y", defaultY + h / 2.0);
                double z    = getDouble(kv, "z", defaultZ);
                boolean col = getBool(kv, "collide", true);

                Brick brick = new Brick(w, h, d);
                brick.setCollidable(col);
                brick.setPosition(x, y, z);
                world.addObject(brick);
                hud.logOutput("Spawned brick (" + w + "x" + h + "x" + d + ", collide=" + col + ") at ("
                        + fmt(x) + ", " + fmt(y) + ", " + fmt(z) + ")");
                break;
            }
            case "mesh": {
                if (!kv.containsKey("path")) {
                    hud.logOutput("spawn mesh requires path=<file>");
                    return;
                }
                String path = kv.get("path");
                double x    = getDouble(kv, "x", defaultX);
                double y    = getDouble(kv, "y", defaultY);
                double z    = getDouble(kv, "z", defaultZ);

                // Optional AABB half-extents
                AABB aabb = null;
                if (kv.containsKey("aabbx") && kv.containsKey("aabby") && kv.containsKey("aabbz")) {
                    try {
                        float ax = Float.parseFloat(kv.get("aabbx"));
                        float ay = Float.parseFloat(kv.get("aabby"));
                        float az = Float.parseFloat(kv.get("aabbz"));
                        aabb = new AABB(ax, ay, az);
                    } catch (NumberFormatException e) {
                        hud.logOutput("Invalid aabb values — ignoring.");
                    }
                }

                boolean col = getBool(kv, "collide", true);

                final double fx = x, fy = y, fz = z;
                final AABB   fAABB = aabb;
                final String fPath = path;
                final boolean fCol = col;

                // File loading may be slow — run on a background thread
                Thread t = new Thread(() -> {
                    canvas.stopRenderer();
                    try {
                        MeshObject mesh = new MeshObject(fPath);
                        if (fAABB != null) mesh.setLocalAABB(fAABB);
                        mesh.setCollidable(fCol);
                        mesh.setPosition(fx, fy, fz);
                        world.addObject(mesh);
                    } finally {
                        canvas.startRenderer();
                    }
                    hud.logOutput("Spawned mesh (" + fPath + ", collide=" + fCol + ") at ("
                            + fmt(fx) + ", " + fmt(fy) + ", " + fmt(fz) + ")"
                            + (fAABB != null ? " with AABB" : ""));
                }, "spawn-mesh-thread");
                t.setDaemon(true);
                t.start();
                hud.logOutput("Loading " + path + " ...");
                break;
            }
            default:
                hud.logOutput("Unknown type: " + type + "  (cube|brick|mesh)");
        }
    }

    /** Parses key=value tokens starting at index {@code start} into a map. */
    private Map<String, String> parseKVTokens(CommandHud hud, String[] tokens, int start) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = start; i < tokens.length; i++) {
            if (tokens[i].isEmpty()) continue;
            String[] pair = tokens[i].split("=", 2);
            if (pair.length != 2) { hud.logOutput("Skipping bad param: " + tokens[i]); continue; }
            map.put(pair[0].toLowerCase(), pair[1]);
        }
        return map;
    }

    private float  getFloat (Map<String,String> kv, String key, float  def) {
        if (!kv.containsKey(key)) return def;
        try { return Float.parseFloat(kv.get(key)); } catch (NumberFormatException e) { return def; }
    }
    private double getDouble(Map<String,String> kv, String key, double def) {
        if (!kv.containsKey(key)) return def;
        try { return Double.parseDouble(kv.get(key)); } catch (NumberFormatException e) { return def; }
    }
    private boolean getBool(Map<String,String> kv, String key, boolean def) {
        if (!kv.containsKey(key)) return def;
        return !kv.get(key).equalsIgnoreCase("false");
    }
    private String fmt(double v) { return String.format("%.1f", v); }

    private void deleteMap(CommandHud hud) {
        world.clearObjects();
        hud.logOutput("Map deleted.");
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (universe != null) {
            universe.cleanup();
        }
    }
}
