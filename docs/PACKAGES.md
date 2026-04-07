# Package & Directory Guide

A reference for what belongs where. When adding new code or assets, put them in the package whose responsibility matches.

---

## Source packages (`src/`)

### `main`
The entry point only. `Main.java` wires everything together at startup: creates the `World`, runs terrain generation, places initial objects, builds the `Game3DRenderer`, and shows the `JFrame`. Nothing else should live here.

### `world`
Global game state. This package owns the scene graph container (`World`), the camera view state (`Camera`), and scene-wide lighting (`Lighting`). Code here knows about the overall simulation — what objects exist, what the player is, how lights are configured — but does not implement gameplay logic itself.

- **Add here:** new scene-level state, global toggles (e.g. hitbox visibility), lighting presets.
- **Do not add here:** rendering code, physics algorithms, or UI.

### `entity`
Anything that moves and interacts with the world as an actor. `Entity` is the abstract base: it holds a position, yaw, an `EntityPhysics` instance, and an optional visible model. `Player` is the user-controlled entity. `EntityPhysics` is the shared physics engine (gravity, jump, slope, collision) that all entities use.

- **Add here:** new actor types (NPCs, enemies, vehicles) by extending `Entity`.
- **Do not add here:** static world objects (those go in `objects`), or UI/input code.

### `renderer`
The Java3D rendering layer and per-frame game loop. `Game3DRenderer` sets up the Java3D universe, drives the orbit camera, handles occlusion, zoom, and keyboard input, and owns the day/night cycle. `WorldUpdateBehavior` is the Java3D `Behavior` that fires every frame to advance the simulation and sync the HUD. `CommandHandler` parses and executes in-game console commands. `DayNightCycle` tracks time and updates lights, fog, and the skybox. The `skybox/` sub-package contains the cubemap skybox implementation.

- **Add here:** new rendering effects, post-processing, new command implementations, time-of-day logic.
- **Do not add here:** game logic, physics, or UI layout.

### `hud`
All in-game overlay UI. `HudCanvas` is a `Canvas3D` subclass that draws the stats panel and model spawner directly via `postRender()`. `CommandHud` renders the text input bar and scrolling output history. `CommandEvent` is the event object passed when the player submits a command.

- **Add here:** new HUD panels, overlays, or on-screen controls.
- **Do not add here:** command logic (that goes in `renderer/CommandHandler`), world state.

### `objects`
All discrete 3D objects that can exist in the scene. `BaseObject` is the abstract base: position, rotation, velocity, AABB, and polygon count. Concrete primitives (`Cube`, `Brick`, `OscillatingCube`) and the OBJ loader (`MeshObject`) live here. `TerrainMesh` is the geometry container for procedurally generated ground.

- **Add here:** new primitive shapes, animated objects, any object that gets placed in the world via `world.addObject()`.
- **Do not add here:** the terrain generation algorithm itself (that goes in `terrain`), or entity actors (those go in `entity`).

### `terrain`
Procedural terrain generation algorithms. `MapGenerator` produces a smooth height-mapped mesh using OpenSimplex2 noise with domain warping and a GLSL biome shader. `MapGeneratorLegacy` produces the older brick-based terrain. Both generators produce objects and register them with the `World`.

- **Add here:** new generation algorithms, biome systems, erosion passes.
- **Do not add here:** the mesh geometry itself (`TerrainMesh` lives in `objects`), or GLSL shaders (those go in `resources/terrain/`).

### `physics`
Low-level collision and spatial data structures. `AABB` is an immutable axis-aligned bounding box with translate/overlap helpers. `Pillbox` is the vertical capsule shape used for entity colliders. `TerrainHeightProvider` is the interface terrain implements so physics code can query ground height without depending on a specific terrain type.

- **Add here:** new collision shapes, broadphase structures, spatial queries.
- **Do not add here:** per-entity physics integration (that belongs in `entity/EntityPhysics`).

### `util`
Standalone utility code with no dependencies on the rest of the project. Currently holds `FastNoiseLite` (MIT-licensed noise library by Jordan Peck).

- **Add here:** self-contained helpers (math utilities, data structures, third-party libraries) that are used by more than one other package.
- **Do not add here:** anything that imports game-specific code.

---

## Resources (`src/resources/`)

### `resources/models/<ModelName>/`
One subdirectory per OBJ model. Each folder contains the `.obj` file, its `.mtl` material file, and any texture images the MTL references. The in-game spawner scans this directory to build its model list, so dropping a new model folder here makes it immediately available via `spawn mesh <name>`.

### `resources/skyboxes/<SkyboxName>/`
One subdirectory per cubemap skybox. Each folder contains six face images (`front`, `back`, `left`, `right`, `top`, `bottom` as `.png`). Add new skybox sets here for the day/night cycle or other sky conditions.

### `resources/terrain/`
GLSL shaders and textures used by the terrain and water systems.

| File | Purpose |
|---|---|
| `terrain.vert` / `terrain.frag` | Biome-blended terrain shader (sand/grass/rock/snow) |
| `water.vert` / `water.frag` | Animated water surface shader |
| `sand.jpg`, `grass.jpg`, `rock.jpg`, `snow.jpg` | Biome diffuse textures sampled in the terrain shader |
| `waternormal.jpg` | Normal map for water surface distortion |

Add new terrain shaders or biome textures here.
