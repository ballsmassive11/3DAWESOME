# Project Architecture

The **3DAWESOME** project is a 3D game engine and world simulator built using **Java3D**. It follows a modular design where the world state, rendering logic, and physical entities are decoupled.

## Core Components

### 1. World System (`src/world/`)
The `World` class is the central hub of the application. It acts as a container for all game elements, including:
- **Objects**: Static or simple rotating 3D models (`BaseObject`, `MeshObject`).
- **Entities**: Dynamic, physics-driven objects like the `Player`.
- **Lights**: Ambient and point lights that illuminate the scene.
- **Terrain**: Procedurally generated landscapes.

### 2. Rendering Engine (`src/renderer/`)
The `Game3DRenderer` handles the visualization of the `World`. It leverages Java3D's scene graph architecture.
- **Scene Graph**: It converts the objects in the `World` into a Java3D `BranchGroup` hierarchy.
- **Day/Night Cycle**: Includes a `DayNightCycle` component that changes lighting and skybox textures over time.
- **HUD**: A 2D overlay for displaying debug information (FPS, coordinates, etc.).

### 3. Object & Entity System (`src/objects/`, `src/entity/`)
- **BaseObject**: The foundation for all 3D objects. Handles position, rotation, scale, and basic appearance.
- **MeshObject**: A specialized `BaseObject` that loads 3D models from `.obj` files.
- **Entity**: An abstract class for objects that interact with physics (gravity, collision).
- **Player**: A specialized `Entity` controlled by the user via keyboard/mouse input.

### 4. Terrain Generation (`src/terrain/`)
Terrain is generated procedurally using the `MapGenerator` class. It uses noise functions to create realistic heights and biomes, which are then mesh-rendered in the world.

## Execution Flow
1. **Initialization**: `Main` creates the `World` and `MapGenerator`.
2. **Setup**: Objects and the Player are added to the `World`.
3. **Rendering**: `Game3DRenderer` is initialized with the `World` and starts the Java3D rendering loop.
4. **Update Loop**: The `World.update()` method is called every frame to advance physics and entity logic.
