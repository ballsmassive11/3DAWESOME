# 3DAWESOME

A Java3D-based 3D world renderer with procedural terrain, model loading, and a first-person camera.

## Features

- **Procedural terrain** generated with OpenSimplex2 noise (FastNoiseLite)
- **OBJ model loading** with optional MTL texture support
- **First-person camera** with WASD + arrow key controls
- **HUD overlay** displaying real-time camera position, orientation, and FPS
- **Lighting** with ambient and directional lights
- **Fullscreen** rendering via Java3D Canvas3D

## Controls

| Key | Action |
|-----|--------|
| `W` / `S` | Move forward / backward |
| `A` / `D` | Strafe left / right |
| `Space` | Move up |
| `Shift` | Move down |
| `←` / `→` | Rotate (yaw) left / right |
| `↑` / `↓` | Look up / down |
| `Esc` | Quit |

## HUD Stats (top-right)

- **FPS** — frames per second
- **X / Y / Z** — camera world position
- **Yaw / Pitch** — camera orientation in degrees
- **Objs** — number of objects in the scene

## Project Structure

```
src/
├── main/Main.java              — entry point, sets up world and JFrame
├── renderer/
│   ├── Game3DRenderer.java     — Java3D rendering pipeline
│   ├── HudPanel.java           — transparent Swing overlay for stats
│   └── WorldUpdateBehavior.java— per-frame update behavior
├── world/
│   ├── World.java              — scene graph container
│   ├── Camera.java             — flying camera with key input
│   ├── Lighting.java           — ambient + directional lights
│   ├── MapGenerator.java       — procedural terrain generation
│   └── FastNoiseLite.java      — noise library
└── objects/
    ├── BaseObject.java         — base class for scene objects
    ├── Brick.java              — box primitive
    ├── ModelObject.java        — OBJ/MTL model loader
    └── ...
```

## Requirements

- Java 8+
- Java3D 1.5.1 (configured via Maven / project libraries)

## Building

Open in IntelliJ IDEA and run `main.Main`, or build with Maven:

```bash
mvn compile exec:java -Dexec.mainClass=main.Main
```
