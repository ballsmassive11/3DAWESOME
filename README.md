# 3DAWESOME

A Java3D-based 3D world renderer with procedurally generated organic terrain, animated water, OBJ model loading, a first-person camera, and an in-game command console.

## Features

- **Organic procedural terrain** — OpenSimplex2 FBm noise with domain warping for twisty, natural-looking landmasses
- **Animated water** — transparent water plane with sinusoidal bobbing; seabed sand slopes down from the shoreline
- **Height-based terrain coloring** — sand → grass → forest green → rocky gray → snow peaks
- **OBJ model loading** — supports MTL materials and PBR texture maps (diffuse, metallic, roughness, normal, AO)
- **First-person camera with physics** — WASD movement, arrow key rotation, gravity, jumping, slope limiting, and step-up; toggle flight mode to freely ascend/descend
- **Distance fog** — geometry fades into the sky color over the back half of the render distance, eliminating hard pop-in at the clip plane
- **HUD overlay** — real-time FPS, camera position/orientation, scene object count, and total triangle count
- **Object spawner** — top-left panel lists all models in `src/resources/`; click to spawn in front of the camera
- **In-game command console** — press `T` to open a text input bar; submit commands to change settings at runtime; history panel shows previous commands and responses
- **Ambient + directional lighting**
- **Fullscreen rendering** via Java3D Canvas3D

## Controls

| Key | Action |
|-----|--------|
| `W` / `S` | Move forward / backward |
| `A` / `D` | Strafe left / right |
| `Space` | Jump (on ground) / ascend (flying) |
| `Shift` | Descend (flying only) |
| `←` / `→` | Yaw left / right |
| `↑` / `↓` | Pitch up / down |
| `T` | Open command console |
| `Esc` | Quit |

### Command Console

Press `T` to open the input bar. Type a command and press `Enter` to submit, or `Esc` to cancel. Previous commands and their responses are shown in the history panel above the input bar.

| Command               | Description                                                                              |
|-----------------------|------------------------------------------------------------------------------------------|
| `fly`                 | Toggle flight mode (Space = ascend, Shift = descend)                                     |
| `fog on\|off`         | Toggle distance fog                                                                      |
| `fov <degrees>`       | Set field of view (clamped 10–170°)                                                      |
| `rdist <distance>`    | Set render distance (also adjusts fog distances)                                         |
| `genmap [key=value]`  | Regenerate mesh terrain<br/>Params: `seed size height threshold cellsize`                |
| `genmapl [key=value]` | Regenerate terrain (legacy brick mode)<br/>Params: `seed size height threshold blockwidth` |
| `delmap`              | Delete the current terrain                                                               |
| `hitbox on\|off`      | Toggle AABB wireframe hitboxes                                                           |
| `spawn cube\|brick\|mesh [key=value]` | Spawn an object in front of the camera                               |
| `fun`                 | dont start this                                                                          |
| `help` / `cmds`       | List all commands                                                                        |

## HUD Stats

Displayed in the top-right corner each frame:

| Stat | Description |
|------|-------------|
| FPS | Frames per second |
| X / Y / Z | Camera world position |
| Yaw / Pitch | Camera orientation (degrees) |
| Objs | Total objects in the scene |
| Tris | Total polygon (triangle) count |
| Seed | World generation seed |
| `** FLYING **` | Shown in blue when flight mode is active |

## Project Structure

```
src/
├── main/
│   └── Main.java                   — entry point; builds world, loads models, creates JFrame
├── renderer/
│   ├── Game3DRenderer.java         — Java3D universe, ViewingPlatform, keyboard input, command handling
│   └── WorldUpdateBehavior.java    — per-frame Behavior; drives world.update() and HUD sync
├── hud/
│   ├── HudCanvas.java              — Canvas3D subclass; draws stats overlay and spawner via postRender()
│   ├── HudPanel.java               — legacy Swing HUD overlay (unused)
│   ├── CommandHud.java             — in-game text input bar with history panel; fires CommandEvents
│   └── CommandEvent.java           — event object wrapping submitted command text
├── world/
│   ├── World.java                  — scene graph container; holds objects, camera, lighting, water
│   ├── Camera.java                 — flying first-person camera with key-state input
│   └── Lighting.java               — ambient + directional light setup
├── terrain/
│   ├── MapGenerator.java           — procedural terrain and water plane generation
│   └── WaterHandler.java           — sinusoidal water surface animation
├── objects/
│   ├── BaseObject.java             — abstract base; position, quaternion rotation, velocity, polygon counting
│   ├── Brick.java                  — non-uniform box primitive (used for terrain blocks)
│   ├── Cube.java                   — uniform box primitive
│   ├── OscillatingCube.java        — sine-wave animated cube (for testing)
│   └── ModelObject.java            — OBJ/MTL loader with Blender compatibility preprocessing
└── util/
    └── FastNoiseLite.java          — embedded noise library (MIT, v1.1.1, Jordan Peck)
```

## Terrain Generation

The terrain uses two `FastNoiseLite` instances:

1. **Warp noise** — displaces XY sample coordinates by up to ±28 units using `DomainWarpIndependent` (4 octaves). This is what produces the winding, organic coastlines.
2. **Main noise** — OpenSimplex2 FBm (5 octaves, lacunarity 2.0) sampled at the warped coordinates to produce the actual height values.

Blocks above the threshold rise from flat shore up through a `t²` height curve to mountain peaks. Blocks below the threshold form the sandy seabed, deepening with a `depth^1.5` curve away from the shoreline.

## Requirements

- Java 8+
- Java3D 1.5.1 (configured via IntelliJ project libraries)

## Building

Open in IntelliJ IDEA and run `main.Main`, or with Maven:

```bash
mvn compile exec:java -Dexec.mainClass=main.Main
```

thanks to ThinMatrix and his OpenGl 3D Tutorial Series, wouldnt have been possible without it
