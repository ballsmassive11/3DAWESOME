# 3DAWESOME

A Java3D-powered game engine with procedural terrain, physics, and a real-time console.

## Features

- **Procedural World** — OpenSimplex2 terrain with height-based biomes.
- **Dynamic Physics** — Capsule vs AABB collisions, gravity, and smooth movement.
- **Atmospheric Rendering** — Day/night cycle, distance fog, and skyboxes.
- **Extensible Objects** — OBJ loading with caching and material support.
- **Live Console** — Runtime object spawning and setting adjustments.

## Documentation

Detailed guides are available in the [docs](./docs) directory:
- [Architecture](./docs/ARCHITECTURE.md) — System overview.
- [World System](./docs/WORLD_SYSTEM.md) — Objects and scene graph.
- [Renderer](./docs/RENDERER.md) — Java3D integration and camera.
- [Entities](./docs/OBJECTS_AND_ENTITIES.md) — Physics and players.
- [Terrain](./docs/TERRAIN_GENERATION.md) — Procedural generation.

## Controls

| Key | Action |
|-----|--------|
| `W/A/S/D` | Movement |
| `Space/Shift` | Jump / Fly Up/Down |
| `Arrows` | Look (Yaw/Pitch) |
| `I / O` | Zoom |
| `/` | Open Console |
| `Esc` | Quit |

## Quick Start

Run with Maven:
```bash
mvn compile exec:java -Dexec.mainClass=main.Main
```

---
*Inspired by ThinMatrix's OpenGL series.*
