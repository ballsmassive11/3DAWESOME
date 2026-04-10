# 3DAWESOME

A Java3D-based 3D world with procedurally generated organic terrain, animated water, OBJ model loading, a 3rd-person orbit camera, entity/physics system, day/night cycle, and an in-game command console.

## Features

- **Organic procedural terrain** — OpenSimplex2 FBm noise with domain warping for twisty, natural-looking landmasses
- **Terrain types** — `biome` mode (sand → grass → rock → snow) and `hills` mode; switched via `genmap type=`
- **Animated water** — transparent water plane with sinusoidal bobbing; seabed sand slopes down from the shoreline
- **Multi-texture terrain shading** — GLSL shader blends sand → grass → rock → snow based on height
- **OBJ model loading** — supports MTL materials; models are cached and cloned for efficient re-use
- **3rd-person orbit camera** — camera sits on a sphere around the player; pitch and yaw orbit the player so they stay centred in frame at all angles
- **Camera occlusion** — camera pulls in toward the player when terrain or objects block the line of sight
- **Zoom** — `I` / `O` smoothly adjusts the camera orbit radius in real time
- **Entity system** — `Entity` abstract base class with `EntityPhysics`; Player and future NPCs all share the same physics pipeline
- **Walking physics** — gravity, jumping, slope limiting, step-up, and pillbox capsule vs AABB collision
- **Flight mode** — toggle with `fly` command; Space/Shift for vertical movement
- **Day/night cycle** — smooth 120-second cycle; ambient/directional lighting, fog colour, and skybox all update continuously; dawn/dusk tinted with Gaussian colour peaks
- **Skybox** — cubemap skybox swaps between a cloudy day sky and a night sky as time progresses
- **Distance fog** — geometry fades into the sky colour over the back half of the render distance; colour, start distance, and transition width are all tunable at runtime
- **HUD overlay** — real-time FPS, player position/orientation, scene object and triangle counts, flying status
- **Object spawner** — top-left panel lists all models in `src/resources/models/`; click to spawn in front of the player
- **In-game command console** — press `T` to open a text input bar; submit commands to change settings at runtime
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
| `T`       | Open command console |
| `Esc`     | Quit |

### Command Console

Press `T` to open the input bar. Type a command and press `Enter` to submit, or `Esc` to cancel.

| Command | Description                                                                               |
|---|-------------------------------------------------------------------------------------------|
| `fly` | Toggle flight mode (Space = ascend, Shift = descend)                                      |
| `fog on\|off` | Toggle distance fog                                                                       |
| `fog <margin 0.01–1.0>` | Set fog transition width as a fraction of render distance                                 |
| `fog near <dist>` | Set fog start in world units                                                              |
| `fog color <r> <g> <b>` | Set fog color (0–1 or 0–255)                                                              |
| `fov <degrees>` | Set field of view (10–170°)                                                               |
| `rdist <distance>` | Set render distance (also adjusts fog)                                                    |
| `genmap [key=value]` | Regenerate mesh terrain — params: `seed size height threshold cellsize type=biome\|hills` |
| `genmapl [key=value]` | Regenerate terrain (legacy brick mode) — params: `seed size height threshold blockwidth`  |
| `delmap` | Delete the current map                                                                    |
| `hitbox on\|off` | Toggle AABB wireframe hitboxes                                                            |
| `spawn cube\|brick\|mesh [key=value]` | Spawn an object in front of the player                                                    |
| `time day\|night\|noon\|dawn\|dusk` | Jump to a preset time of day                                                              |
| `time <0.0–1.0>` | Set time directly (0=midnight, 0.25=dawn, 0.5=noon, 0.75=dusk)                            |
| `time pause\|resume` | Pause or resume the day/night cycle                                                       |
| `time speed <seconds>` | Set full cycle duration in seconds (default 120)                                          |
| `fun` | don't start this                                                                          |
| `help` / `cmds` | List all commands                                                                         |

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
│   ├── WorldUpdateBehavior.java    — per-frame Behavior; drives world.update() and HUD sync
│   ├── CommandHandler.java         — parses and executes all in-game console commands
│   ├── DayNightCycle.java          — tracks time of day; drives ambient/directional light, fog, and skybox
│   └── skybox/
│       └── Skybox.java             — cubemap skybox; swaps day/night geometry and overlay transparencies
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
│   ├── OscillatingCube.java        — cube with a sinusoidal bobbing animation
│   ├── MeshObject.java             — OBJ loader with model cache and Blender compatibility preprocessing
│   └── TerrainMesh.java            — procedural height-mapped mesh with per-vertex biome blend weights
├── physics/
│   ├── AABB.java                   — immutable axis-aligned bounding box; translate/overlap helpers
│   ├── Pillbox.java                — vertical capsule shape used for entity collision
│   └── TerrainHeightProvider.java  — interface for querying ground height at any (x, z)
├── util/
│   └── FastNoiseLite.java          — embedded noise library (MIT, Jordan Peck)
└── resources/
    ├── models/
    │   ├── Suzanne/                — Blender monkey head (no MTL)
    │   ├── Boat/                   — wooden boat with MTL textures
    │   ├── Rock/                   — rock mesh with MTL texture
    │   ├── Ruger/                  — gun model with PBR textures
    │   └── StreetLamp/             — street lamp with MTL textures
    ├── skyboxes/
    │   ├── cloudy_sky/             — daytime cubemap (6× PNG faces)
    │   ├── night2/                 — night cubemap used by the day/night cycle
    │   ├── night1/                 — alternate night cubemap
    │   └── Default_Sky/            — default fallback cubemap
    └── terrain/
        ├── terrain.vert/.frag      — GLSL biome shader (blends sand/grass/rock/snow by height)
        ├── water.vert/.frag        — GLSL animated water shader
        ├── sand.jpg / grass.jpg    — biome diffuse textures
        ├── rock.jpg / snow.jpg     — biome diffuse textures
        └── waternormal.jpg         — normal map for water surface distortion
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
g.setModel(new MeshObject("src/resources/models/Goblin/goblin.obj"));
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

## Day/Night Cycle

Time runs from 0 (midnight) → 0.25 (dawn) → 0.5 (noon) → 0.75 (dusk) → 1 (midnight). Each frame:

- Ambient and directional light colours are interpolated along a sinusoidal brightness curve, with Gaussian warm peaks at dawn and dusk.
- Linear fog colour tracks the sky colour so the horizon blends naturally.
- The cubemap skybox swaps between a cloudy day texture and a night texture as brightness crosses the threshold.

Use `time speed <seconds>` to control how long a full cycle takes (default 120 s), or `time pause` to freeze the clock.

## Requirements

- Java 8+
- Java3D 1.5.1 (configured via IntelliJ project libraries)

## Building

Open in IntelliJ IDEA and run `main.Main`, or with Maven:

```bash
mvn compile exec:java -Dexec.mainClass=main.Main
```

thanks to ThinMatrix and his OpenGL 3D Tutorial Series, wouldn't have been possible without it
