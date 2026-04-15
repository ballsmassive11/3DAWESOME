# 3DAWESOME

A Java3D-based 3D game engine with procedurally generated terrain, physics, and an in-game command console.

## Features

- **Procedural terrain** — OpenSimplex2 noise with domain warping; biome shader blends sand, grass, rock, and snow by height
- **Animated water** — transparent water plane with normal-mapped surface distortion
- **OBJ model loading** — MTL material support with model caching
- **3rd-person orbit camera** — pitch/yaw orbits around the player; pulls in when occluded by terrain
- **Entity & physics system** — gravity, jumping, slope limiting, step-up, and capsule vs AABB collision
- **Day/night cycle** — smooth 120 s cycle; ambient/directional lighting, fog, and skybox update continuously
- **Distance fog** — fades geometry into the sky colour over the back half of the render distance
- **HUD** — real-time FPS, position, orientation, object/triangle counts
- **In-game command console** — press `T` to open; spawn objects, adjust settings, and more at runtime

## Controls

| Key | Action |
|-----|--------|
| `W` / `S` | Move forward / backward |
| `A` / `D` | Strafe left / right |
| `Space` | Jump / ascend (flying) |
| `Shift` | Descend (flying) |
| `← / →` | Yaw left / right |
| `↑ / ↓` | Pitch up / down |
| `I` / `O` | Zoom in / out |
| `T` | Open command console |
| `Esc` | Quit |

## Commands

Press `T` to open the console. Common commands:

| Command | Description |
|---------|-------------|
| `fly` | Toggle flight mode |
| `fog on\|off` | Toggle distance fog |
| `fov <degrees>` | Set field of view |
| `rdist <dist>` | Set render distance |
| `genmap [key=value]` | Regenerate terrain (`seed size height threshold type=biome\|hills`) |
| `delmap` | Delete the current map |
| `hitbox on\|off` | Toggle AABB wireframes |
| `spawn cube\|brick\|mesh` | Spawn an object in front of the player |
| `time day\|night\|noon\|dawn\|dusk` | Jump to a time preset |
| `time pause\|resume` | Pause or resume the day/night cycle |
| `time speed <seconds>` | Set full cycle duration (default 120) |
| `help` / `cmds` | List all commands |

## Requirements

- Java 8+
- Java3D 1.5.1

## Building

Open in IntelliJ IDEA and run `main.Main`, or with Maven:

```bash
mvn compile exec:java -Dexec.mainClass=main.Main
```

thanks to ThinMatrix and his OpenGL 3D Tutorial Series
