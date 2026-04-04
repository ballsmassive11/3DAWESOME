# 3DAWESOME

A Java3D-based 3D world with procedurally generated organic terrain, animated water, OBJ model loading, a 3rd-person camera, entity/physics system, and an in-game command console.

## Features

- **Organic procedural terrain** — OpenSimplex2 FBm noise with domain warping for twisty, natural-looking landmasses
- **Animated water** — transparent water plane with sinusoidal bobbing; seabed sand slopes down from the shoreline
- **Multi-texture terrain shading** — GLSL shader blends sand → grass → rock → snow based on height
- **OBJ model loading** — supports MTL materials; models are cached and cloned for efficient re-use
- **3rd-person orbit camera** — camera sits on a sphere around the player; pitch and yaw orbit the player so they stay centred in frame at all angles
- **Camera occlusion** — camera pulls in toward the player when terrain or objects block the line of sight
- **Zoom** — `I` / `O` smoothly adjusts the camera orbit radius in real time
- **Entity system** — `Entity` abstract base class with `EntityPhysics`; Player and future NPCs all share the same physics pipeline
- **Walking physics** — gravity, jumping, slope limiting, step-up, and pillbox capsule vs AABB collision
- **Flight mode** — toggle with `fly` command; Space/Shift for vertical movement
- **Distance fog** — geometry fades into the sky colour over the back half of the render distance
- **HUD overlay** — real-time FPS, player position/orientation, scene object and triangle counts, flying status
- **Object spawner** — top-left panel lists all models in `src/resources/`; click to spawn in front of the player
- **In-game command console** — press `` ` `` to open a text input bar; submit commands to change settings at runtime
- **Ambient + directional lighting**
- **Fullscreen rendering** via Java3D Canvas3D

## Controls

| Key       | Action |
|-----------|--------|
| `W` / `S` | Move forward / backward |
| `A` / `D` | Strafe left / right |
| `Space`   | Jump (on ground) / ascend (flying) |
| `Shift`   | Descend (flying only) |
| `←` / `→` | Yaw left / right |
| `↑` / `↓` | Pitch up / down (orbits camera around player) |
| `I` / `O` | Zoom camera in / out |
| `` T ``   | Open command console |
| `Esc`     | Quit |

### Command Console

Press `` T `` to open the input bar. Type a command and press `Enter` to submit, or `Esc` to cancel.

| Command | Description |
|---|---|
| `fly` | Toggle flight mode (Space = ascend, Shift = descend) |
| `fog on\|off` | Toggle distance fog |
| `fov <degrees>` | Set field of view (10–170°) |
| `rdist <distance>` | Set render distance (also adjusts fog) |
| `genmap [key=value]` | Regenerate mesh terrain — params: `seed size height threshold cellsize` |
| `genmapl [key=value]` | Regenerate terrain (legacy brick mode) — params: `seed size height threshold blockwidth` |
| `delmap` | Delete the current terrain |
| `hitbox on\|off` | Toggle AABB wireframe hitboxes |
| `spawn cube\|brick\|mesh [key=value]` | Spawn an object in front of the player |
| `fun` | dont start this |
| `help` / `cmds` | List all commands |

## HUD Stats

Displayed in the top-right corner each frame:

| Stat | Description |
|---|---|
| FPS | Frames per second |
| X / Y / Z | Player world position |
| Yaw / Pitch | Camera orientation (degrees) |
| Objs | Total objects in the scene |
| Tris | Total polygon (triangle) count |
| Seed | World generation seed |
| `** FLYING **` | Shown in blue when flight mode is active |

## Project Structure

```
src/
├── main/
│   └── Main.java                   — entry point; builds world, generates terrain, creates JFrame
├── entity/
│   ├── Entity.java                 — abstract base for all moving actors; position, yaw, EntityPhysics, model
│   ├── EntityPhysics.java          — gravity, jumping, slope limiting, step-up, pillbox vs AABB collision
│   └── Player.java                 — user-controlled entity; owns Camera for input, drives physics each frame
├── renderer/
│   ├── Game3DRenderer.java         — Java3D universe, orbit camera, occlusion, zoom, keyboard input, commands
│   └── WorldUpdateBehavior.java    — per-frame Behavior; drives world.update() and HUD sync
├── hud/
│   ├── HudCanvas.java              — Canvas3D subclass; draws stats overlay and spawner via postRender()
│   ├── CommandHud.java             — in-game text input bar with history panel
│   └── CommandEvent.java           — event object wrapping submitted command text
├── world/
│   ├── World.java                  — scene graph container; holds objects, entities, player, lighting
│   ├── Camera.java                 — view/input state; yaw, pitch, WASD input; shares position with Player entity
│   └── Lighting.java               — ambient + directional light setup
├── terrain/
│   ├── MapGenerator.java           — mesh terrain with GLSL biome shader (sand/grass/rock/snow)
│   └── MapGeneratorLegacy.java     — legacy brick-based terrain generator
├── objects/
│   ├── BaseObject.java             — abstract base; position, quaternion rotation, velocity, AABB, polygon counting
│   ├── Brick.java                  — non-uniform box primitive
│   ├── Cube.java                   — uniform box primitive
│   ├── MeshObject.java             — OBJ loader with model cache and Blender compatibility preprocessing
│   └── TerrainMesh.java            — procedural height-mapped mesh with per-vertex biome blend weights
├── physics/
│   ├── AABB.java                   — immutable axis-aligned bounding box; translate/overlap helpers
│   ├── Pillbox.java                — vertical capsule shape used for entity collision
│   └── TerrainHeightProvider.java  — interface for querying ground height at any (x, z)
└── util/
    └── FastNoiseLite.java          — embedded noise library (MIT, Jordan Peck)
```

## Entity System

`Entity` is the abstract base for anything that moves and interacts with the world. To add a new entity type, extend it and implement `update()`:

```java
public class Goblin extends Entity {
    @Override
    public void update(double deltaTime, List<AABB> worldAABBs) {
        // 1. Determine movement (AI, scripting, etc.)
        position.x += ...;
        position.z += ...;
        // 2. Apply physics (gravity, collision)
        physics.update(deltaTime, position, false, worldAABBs, 0);
        // 3. Sync visible model
        syncModelTransform();
    }
}

// Register with the world:
Goblin g = new Goblin();
g.setModel(new MeshObject("src/resources/Goblin/goblin.obj"));
world.addEntity(g);  // adds entity + model to scene, sets terrain provider
```

## Camera

The camera is an orbit camera. It sits on a sphere of radius `r` (default 5, range 1–20) centred on the player's eye position. The orbit position is derived from yaw and pitch so the camera always points directly at the player:

```
camX = player.x + sin(yaw) * cos(pitch) * r
camY = player.y - sin(pitch) * r
camZ = player.z + cos(yaw) * cos(pitch) * r
```

Each frame, a ray is cast from the player to the ideal camera position. If terrain or an object blocks it, the radius is reduced so the camera sits just in front of the obstruction.

## Terrain Generation

Two `FastNoiseLite` instances:

1. **Warp noise** — displaces sample coordinates using `DomainWarpIndependent` (4 octaves), producing organic coastlines.
2. **Main noise** — OpenSimplex2 FBm (5 octaves) sampled at the warped coordinates.

Height uses a `t^2.5` curve above a configurable threshold. A GLSL fragment shader blends four biome textures (sand, grass, rock, snow) based on a blend weight baked into the vertex alpha channel.

## Requirements

- Java 8+
- Java3D 1.5.1 (configured via IntelliJ project libraries)

## Building

Open in IntelliJ IDEA and run `main.Main`, or with Maven:

```bash
mvn compile exec:java -Dexec.mainClass=main.Main
```

thanks to ThinMatrix and his OpenGL 3D Tutorial Series, wouldn't have been possible without it
