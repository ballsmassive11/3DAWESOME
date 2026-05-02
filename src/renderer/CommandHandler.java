package renderer;

import entity.EntityPhysics;
import gui.commands.CommandHud;
import objects.BaseObject;
import objects.Brick;
import objects.Cube;
import objects.MeshObject;
import physics.AABB;
import scripting.ScriptRunner;
import terrain.MapGenerator;
import world.Camera;
import world.GameSettings;
import world.World;

import javax.vecmath.Color3f;
import javax.vecmath.Vector3d;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all in-game console commands. Keeps command logic out of the renderer.
 */
public class CommandHandler {
    private final World world;
    private final Game3DRenderer renderer;
    private ScriptRunner scriptRunner;

    public CommandHandler(World world, Game3DRenderer renderer) {
        this.world = world;
        this.renderer = renderer;
    }

    private ScriptRunner scripts() {
        if (scriptRunner == null) scriptRunner = new ScriptRunner(world, renderer.getGuiCanvas(), renderer);
        return scriptRunner;
    }

    public void handle(String text) {
        CommandHud hud = renderer.getCommandHud();
        String[] parts = text.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();

        if (cmd.equals("fly")) {
            boolean nowFlying = !world.getPlayer().getPhysics().isFlying();
            world.getPlayer().getPhysics().setFlying(nowFlying);
            hud.logOutput("Flight " + (nowFlying ? "ON  (Space=up, Shift=down)" : "OFF"));

        } else if (cmd.equals("fog") && parts.length == 2) {
            handleFog(hud, parts[1].trim());

        } else if (cmd.equals("fov") && parts.length == 2) {
            try {
                double degrees = Double.parseDouble(parts[1]);
                degrees = Math.max(10.0, Math.min(170.0, degrees));
                double rads = Math.toRadians(degrees);
                renderer.setFov(rads);
                GameSettings.fov = rads;
                GameSettings.save();
                hud.logOutput("FOV set to " + (int) degrees + "°");
            } catch (NumberFormatException ignored) {
                hud.logOutput("Invalid value: " + parts[1]);
            }

        } else if (cmd.equals("rdist") && parts.length == 2) {
            try {
                double distance = Double.parseDouble(parts[1]);
                distance = Math.max(0.1, distance);
                renderer.setRenderDistance(distance);
                GameSettings.renderDistance = distance;
                GameSettings.save();
                hud.logOutput("Render distance set to " + distance);
            } catch (NumberFormatException ignored) {
                hud.logOutput("Invalid value: " + parts[1]);
            }

        } else if (cmd.equals("genmap")) {
            generateMap(hud, parts.length == 2 ? parts[1] : "");

        } else if (cmd.equals("delmap")) {
            renderer.notifySceneChanging();
            world.clearObjects();
            renderer.notifySceneReady();
            hud.logOutput("Map deleted.");

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

        } else if (cmd.equals("time")) {
            handleTime(hud, parts.length == 2 ? parts[1].trim() : "");

        } else if (cmd.equals("shiftlock")) {
            String arg = parts.length == 2 ? parts[1].trim().toLowerCase() : "";
            if (arg.equals("on")) {
                world.getPlayer().setShiftLockEnabled(true);
                renderer.updateShiftLockCursor();
                hud.logOutput("Shift lock ON — mouse centered, player faces camera direction.");
            } else if (arg.equals("off")) {
                world.getPlayer().setShiftLockEnabled(false);
                renderer.updateShiftLockCursor();
                hud.logOutput("Shift lock OFF — player faces movement direction.");
            } else {
                boolean now = !world.getPlayer().isShiftLockEnabled();
                world.getPlayer().setShiftLockEnabled(now);
                renderer.updateShiftLockCursor();
                hud.logOutput("Shift lock " + (now ? "ON" : "OFF"));
            }

        } else if (cmd.equals("jumpheight") && parts.length == 2) {
            try {
                float h = Float.parseFloat(parts[1]);
                world.getPlayer().getPhysics().setJumpSpeed(h);
                hud.logOutput("Jump height (speed) set to " + h);
            } catch (NumberFormatException e) {
                hud.logOutput("Invalid value: " + parts[1]);
            }

        } else if (cmd.equals("camoffset") && parts.length >= 2) {
            try {
                Camera cam = world.getCamera();
                if (parts.length == 2) {
                    // Usage: camoffset y
                    double y = Double.parseDouble(parts[1]);
                    cam.setOffset(0, y, 0);
                    hud.logOutput("Camera offset set to (0, " + y + ", 0)");
                } else if (parts.length == 4) {
                    // Usage: camoffset x y z
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    cam.setOffset(x, y, z);
                    hud.logOutput("Camera offset set to (" + x + ", " + y + ", " + z + ")");
                } else {
                    hud.logOutput("Usage: camoffset <y> OR camoffset <x> <y> <z>");
                }
            } catch (NumberFormatException e) {
                hud.logOutput("Invalid numeric values for camoffset");
            }

        } else if (cmd.equals("movespeed") && parts.length == 2) {
            try {
                double s = Double.parseDouble(parts[1]);
                world.getPlayer().getCamera().setMoveSpeed(s);
                world.getPlayer().getPhysics().setFlySpeed((float) s * 1.6f); // Affect fly speed too
                hud.logOutput("Movement speed set to " + s);
            } catch (NumberFormatException e) {
                hud.logOutput("Invalid value: " + parts[1]);
            }

        } else if (cmd.equals("mousesens") && parts.length == 2) {
            try {
                double s = Double.parseDouble(parts[1]);
                world.getCamera().setMouseSensitivity(s);
                GameSettings.mouseSensitivity = s;
                GameSettings.save();
                hud.logOutput("Mouse sensitivity set to " + s);
            } catch (NumberFormatException e) {
                hud.logOutput("Invalid value: " + parts[1]);
            }

        } else if (cmd.equals("quality")) { // changes game quality
            if (parts.length < 2) {
                hud.logOutput("Quality: " + GameSettings.quality + "/10");
            } else {
                try {
                    int level = Integer.parseInt(parts[1]);
                    level = Math.max(1, Math.min(10, level));
                    GameSettings.quality = level;
                    GameSettings.save();
                    hud.logOutput("Quality set to " + level + "/10");
                } catch (NumberFormatException ignored) {
                    hud.logOutput("Usage: quality <1-10>");
                }
            }

        } else if (cmd.equals("fun")) {
            hud.logOutput("say less");
            List<BaseObject> objects = world.getObjects();
            for (BaseObject obj : objects) {
                int speed = 50;
                Vector3d vec = new Vector3d(
                        (Math.random() - 0.5) * speed,
                        (Math.random() - 0.5) * speed,
                        (Math.random() - 0.5) * speed);
                obj.setAngularVelocity(vec);
            }

        } else if (cmd.equals("script") && parts.length == 2) {
            String err = scripts().runFile(parts[1].trim());
            hud.logOutput(err == null ? "script ok" : "script error: " + err);

        } else if (cmd.equals("aa") && parts.length == 2) {
            String arg = parts[1].trim().toLowerCase();
            if (arg.equals("on")) {
                renderer.setAntialiasing(true);
                GameSettings.antialiasing = true;
                GameSettings.save();
                hud.logOutput("Anti-aliasing ON.");
            } else if (arg.equals("off")) {
                renderer.setAntialiasing(false);
                GameSettings.antialiasing = false;
                GameSettings.save();
                hud.logOutput("Anti-aliasing OFF.");
            } else {
                hud.logOutput("Usage: aa on|off");
            }

        } else if (cmd.equals("reloadsettings")) {
            GameSettings.load();
            renderer.setFov(GameSettings.fov);
            renderer.setRenderDistance(GameSettings.renderDistance);
            world.getCamera().setMouseSensitivity(GameSettings.mouseSensitivity);
            hud.logOutput("Settings reloaded from file.");

        } else if (cmd.equals("cmds") || cmd.equals("help")) {
            hud.logOutput("fly                     - Toggle flight (Space=up, Shift=down)");
            hud.logOutput("shiftlock [on|off]      - Toggle shift lock (mouse centered, player rotation follows camera)");
            hud.logOutput("aa on|off               - Toggle full-scene anti-aliasing (MSAA)");
            hud.logOutput("fog on|off              - Toggle distance fog");
            hud.logOutput("fog <margin 0.01-1.0>   - Set fog transition width (fraction of rdist)");
            hud.logOutput("fog near <dist>         - Set fog start in world units");
            hud.logOutput("fog color <r> <g> <b>   - Set fog color (0-1 or 0-255)");
            hud.logOutput("fov <degrees>           - Set field of view (10-170)");
            hud.logOutput("rdist <distance>        - Set render distance");
            hud.logOutput("genmap [key=val ...]    - Regenerate terrain");
            hud.logOutput("  params: seed size height cellsize");
            hud.logOutput("delmap                  - Delete the current terrain");
            hud.logOutput("hitbox on|off           - Toggle AABB hitbox wireframes");
            hud.logOutput("spawn cube [key=val ...] - Spawn a cube   (size x y z collide)");
            hud.logOutput("spawn brick [key=val ...]- Spawn a brick  (w h d x y z collide)");
            hud.logOutput("spawn mesh <name> [params] - Spawn a model by name  (x y z aabbx aabby aabbz collide)");
            hud.logOutput("spawn mesh path=<file>  - Spawn a model by explicit path");
            hud.logOutput("  models: " + listAvailableModels());
            hud.logOutput("time day|night|noon|mid - Jump to a time of day");
            hud.logOutput("time <0.0-1.0>          - Set time (0=midnight 0.25=dawn 0.5=noon 0.75=dusk)");
            hud.logOutput("time pause|resume        - Pause or resume the day/night cycle");
            hud.logOutput("time speed <secs>       - Set cycle duration in seconds (default 120)");
            hud.logOutput("movespeed <val>             - Set movement speed");
            hud.logOutput("mousesens <val>             - Set mouse sensitivity");
            hud.logOutput("camoffset <y> | <x y z> - Set camera look-at offset");
            hud.logOutput("jumpheight <val>        - Set jump power");
            hud.logOutput("reloadsettings          - Reload settings from game.properties");
            hud.logOutput("quality <1-10>          - Set game quality");
            hud.logOutput("script <path>           - Run a Jython mod script (exposes `game` API)");
            hud.logOutput("fun                     - would recommend turning render distance down");
            hud.logOutput("cmds / help             - Show this message");

        } else {
            hud.logOutput("Unknown command: " + cmd + "  (type 'cmds / help' for list)");
        }
    }

    private void handleTime(CommandHud hud, String argStr) {
        DayNightCycle cycle = renderer.getDayNightCycle();
        if (cycle == null) { hud.logOutput("Day/night cycle not active."); return; }

        String[] args = argStr.split("\\s+");
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "day":    cycle.setTimeOfDay(0.4);  hud.logOutput("Time set to day.");      break;
            case "noon":   cycle.setTimeOfDay(0.5);  hud.logOutput("Time set to noon.");     break;
            case "night":  cycle.setTimeOfDay(0.0);  hud.logOutput("Time set to midnight."); break;
            case "mid":    cycle.setTimeOfDay(0.0);  hud.logOutput("Time set to midnight."); break;
            case "dawn":   cycle.setTimeOfDay(0.25); hud.logOutput("Time set to dawn.");     break;
            case "dusk":   cycle.setTimeOfDay(0.75); hud.logOutput("Time set to dusk.");     break;
            case "pause":  cycle.setPaused(true);    hud.logOutput("Day/night cycle paused.");  break;
            case "resume": cycle.setPaused(false);   hud.logOutput("Day/night cycle resumed."); break;
            case "speed":
                if (args.length < 2) { hud.logOutput("Usage: time speed <seconds>"); return; }
                try {
                    double secs = Double.parseDouble(args[1]);
                    cycle.setCycleDuration(secs);
                    hud.logOutput("Cycle duration set to " + secs + "s.");
                } catch (NumberFormatException e) { hud.logOutput("Invalid value: " + args[1]); }
                break;
            default:
                try {
                    double t = Double.parseDouble(sub);
                    cycle.setTimeOfDay(t);
                    hud.logOutput(String.format("Time set to %.3f", cycle.getTimeOfDay()));
                } catch (NumberFormatException e) {
                    hud.logOutput("Usage: time day|night|noon|dawn|dusk|pause|resume|speed <s>|<0.0-1.0>");
                }
        }
    }

    private void handleFog(CommandHud hud, String argStr) {
        String[] fogArgs = argStr.split("\\s+");
        String sub = fogArgs[0].toLowerCase();

        if (sub.equals("on")) {
            renderer.setFogOn(true);
            hud.logOutput("Fog on.");
        } else if (sub.equals("off")) {
            renderer.setFogOn(false);
            hud.logOutput("Fog off.");
        } else if (sub.equals("color") && fogArgs.length == 4) {
            try {
                float r = Float.parseFloat(fogArgs[1]);
                float g = Float.parseFloat(fogArgs[2]);
                float b = Float.parseFloat(fogArgs[3]);
                if (r > 1f || g > 1f || b > 1f) { r /= 255f; g /= 255f; b /= 255f; }
                r = Math.max(0, Math.min(1, r));
                g = Math.max(0, Math.min(1, g));
                b = Math.max(0, Math.min(1, b));
                renderer.setFogColor(new Color3f(r, g, b));
                hud.logOutput(String.format("Fog color set to (%.2f, %.2f, %.2f)", r, g, b));
            } catch (NumberFormatException ignored) {
                hud.logOutput("Usage: fog color <r> <g> <b>  (0-1 or 0-255)");
            }
        } else if (sub.equals("near") && fogArgs.length == 2) {
            try {
                double nearDist = Double.parseDouble(fogArgs[1]);
                renderer.setFogNear(nearDist);
                double margin = renderer.getFogMargin();
                hud.logOutput("Fog starts at " + nearDist + " units  (margin=" + String.format("%.2f", margin) + ")");
            } catch (NumberFormatException ignored) {
                hud.logOutput("Usage: fog near <distance>");
            }
        } else {
            try {
                double margin = Double.parseDouble(sub);
                margin = Math.min(1.0, margin);
                renderer.setFogMargin(margin); // margin <= 0 disables fog inside setFogMargin
                if (margin <= 0) {
                    hud.logOutput("Fog off (margin=0).");
                } else {
                    hud.logOutput("Fog margin set to " + margin + " (fog starts at " + (int)((1 - margin) * 100) + "% of render distance)");
                }
            } catch (NumberFormatException ignored) {
                hud.logOutput("Usage: fog on|off|<margin>|color <r> <g> <b>|near <dist>");
            }
        }
    }

    private void generateMap(CommandHud hud, String params) {
        int   seed     = (int) System.currentTimeMillis();
        int   size     = 160;
        float height   = 16.0f;
        float cellSize = 0.8f;

        for (String token : params.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            String[] kv = token.split("=", 2);
            if (kv.length != 2) { hud.logOutput("Skipping bad param: " + token); continue; }
            String key = kv[0].toLowerCase();
            String val = kv[1];
            try {
                switch (key) {
                    case "seed":     seed     = Integer.parseInt(val); break;
                    case "size":     size     = Integer.parseInt(val); break;
                    case "height":   height   = Float.parseFloat(val); break;
                    case "cellsize": cellSize = Float.parseFloat(val); break;
                    default: hud.logOutput("Unknown param: " + key); break;
                }
            } catch (NumberFormatException e) {
                hud.logOutput("Invalid value for " + key + ": " + val);
                return;
            }
        }

        final int fSeed = seed, fSize = size;
        final float fHeight = height, fCellSize = cellSize;

        hud.logOutput("Generating map (seed=" + fSeed + " size=" + fSize + " height=" + fHeight + ") ...");

        Thread t = new Thread(() -> {
            renderer.stopRenderer();
            renderer.notifySceneChanging();
            try {
                world.clearObjects();
                MapGenerator gen = new MapGenerator();
                gen.setSeed(fSeed);
                gen.setGridSize(fSize);
                gen.setHeightScale(fHeight);
                gen.setCellSize(fCellSize);
                gen.generate(world);
                world.setSeed(fSeed);
            } finally {
                renderer.notifySceneReady();
                renderer.startRenderer();
            }
            hud.logOutput("Map ready.");
        }, "genmap-thread");
        t.setDaemon(true);
        t.start();
    }

    private void spawnObject(CommandHud hud, String args) {
        String[] tokens = args.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            hud.logOutput("Usage: spawn cube|brick|mesh [params]");
            return;
        }

        String type = tokens[0].toLowerCase();

        Camera cam = world.getCamera();
        double defaultX = cam.getPosition().x + Math.sin(cam.getYaw()) * 3.0;
        double defaultY = cam.getPosition().y - EntityPhysics.EYE_HEIGHT;
        double defaultZ = cam.getPosition().z - Math.cos(cam.getYaw()) * 3.0;

        switch (type) {
            case "cube": {
                Map<String, String> kv = parseKVTokens(hud, tokens, 1);
                float size  = getFloat(kv, "size", 1.0f);
                double x    = getDouble(kv, "x", defaultX);
                double y    = getDouble(kv, "y", defaultY + size / 2.0);
                double z    = getDouble(kv, "z", defaultZ);
                boolean col = getBool(kv, "collide", true);
                Cube cube = new Cube(size);
                cube.setCollidable(col);
                cube.setPosition(x, y, z);
                world.addObject(cube);
                hud.logOutput("Spawned cube (size=" + size + ", collide=" + col + ") at ("
                        + fmt(x) + ", " + fmt(y) + ", " + fmt(z) + ")");
                break;
            }
            case "brick": {
                Map<String, String> kv = parseKVTokens(hud, tokens, 1);
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
                // tokens[1] is either a bare model name ("boat") or a key=value pair ("path=...")
                boolean hasBareModelName = tokens.length >= 2 && !tokens[1].contains("=");
                Map<String, String> kv = parseKVTokens(hud, tokens, hasBareModelName ? 2 : 1);

                String path;
                if (hasBareModelName) {
                    path = resolveModelPath(tokens[1]);
                    if (path == null) {
                        hud.logOutput("Model not found: " + tokens[1]);
                        hud.logOutput("Available: " + listAvailableModels());
                        return;
                    }
                } else if (kv.containsKey("path")) {
                    path = kv.get("path");
                } else {
                    hud.logOutput("Usage: spawn mesh <name> [params]  or  spawn mesh path=<file>");
                    hud.logOutput("Available: " + listAvailableModels());
                    return;
                }

                double x    = getDouble(kv, "x", defaultX);
                double y    = getDouble(kv, "y", defaultY);
                double z    = getDouble(kv, "z", defaultZ);
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
                boolean useMtl = hasMtl(path);
                final double fx = x, fy = y, fz = z;
                final AABB fAABB = aabb;
                final String fPath = path;
                final boolean fCol = col;
                final boolean fUseMtl = useMtl;
                Thread t = new Thread(() -> {
                    renderer.stopRenderer();
                    renderer.notifySceneChanging();
                    try {
                        MeshObject mesh = new MeshObject(fPath, fUseMtl);
                        if (fAABB != null) mesh.setLocalAABB(fAABB);
                        mesh.setCollidable(fCol);
                        mesh.setPosition(fx, fy, fz);
                        world.addObject(mesh);
                    } finally {
                        renderer.notifySceneReady();
                        renderer.startRenderer();
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

    /** Returns true if a .mtl file exists in the same directory as the given .obj path. */
    private boolean hasMtl(String objPath) {
        File dir = new File(objPath).getParentFile();
        if (dir == null || !dir.isDirectory()) return false;
        File[] mtls = dir.listFiles(f -> f.getName().endsWith(".mtl"));
        return mtls != null && mtls.length > 0;
    }

    /** Finds the first .obj file inside src/resources/models/<name>/ (case-insensitive). */
    private String resolveModelPath(String name) {
        File modelsDir = new File("resources/models");
        if (!modelsDir.isDirectory()) return null;
        File[] dirs = modelsDir.listFiles(File::isDirectory);
        if (dirs == null) return null;
        for (File dir : dirs) {
            if (dir.getName().equalsIgnoreCase(name)) {
                File[] objs = dir.listFiles(f -> f.getName().endsWith(".obj"));
                if (objs != null && objs.length > 0) return objs[0].getPath();
            }
        }
        return null;
    }

    /** Returns a comma-separated list of model folder names in src/resources/models/. */
    private String listAvailableModels() {
        File modelsDir = new File("resources/models");
        if (!modelsDir.isDirectory()) return "(none)";
        File[] dirs = modelsDir.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (File dir : dirs) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(dir.getName());
        }
        return sb.toString();
    }

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

    private float   getFloat (Map<String,String> kv, String key, float   def) {
        if (!kv.containsKey(key)) return def;
        try { return Float.parseFloat(kv.get(key)); } catch (NumberFormatException e) { return def; }
    }
    private double  getDouble(Map<String,String> kv, String key, double  def) {
        if (!kv.containsKey(key)) return def;
        try { return Double.parseDouble(kv.get(key)); } catch (NumberFormatException e) { return def; }
    }
    private boolean getBool  (Map<String,String> kv, String key, boolean def) {
        if (!kv.containsKey(key)) return def;
        return !kv.get(key).equalsIgnoreCase("false");
    }
    private String fmt(double v) { return String.format("%.1f", v); }
}
