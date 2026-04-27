# World System

The `World` class (`src/world/World.java`) is the primary data structure holding the state of the 3D environment.

## Key Responsibilities

### 1. Object Management
The `World` maintains a list of `BaseObject` instances. These objects can be simple primitives (like `Cube`) or complex models (`MeshObject`).
- `addObject(BaseObject object)`: Adds an object to the world.
- `removeObject(BaseObject object)`: Removes an object.
- `getObjects()`: Returns all objects currently in the world.

### 2. Entity Management
Entities are dynamic objects (like the `Player`) that require per-frame physics updates.
- `addEntity(Entity entity)`: Adds a moving entity.
- `getEntities()`: Returns all active entities.

### 3. Scene Graph Integration
The `World` provides a `getSceneBranchGroup()` method which compiles all objects, entities, and lights into a single Java3D `BranchGroup`. This group is then attached to the renderer's `SimpleUniverse`.

### 4. Physics and Updates
The `update(double deltaTime)` method is the heart of the world's logic:
- It iterates through all `BaseObject`s and calls their `update` method (handling simple rotations or velocities).
- It iterates through all `Entity`s and calls their `update` method, passing in world collision data (AABBs).
- It manages player-specific logic, such as particle emission.

### 5. Lighting and Environment
- **Lighting**: Stores a `Lighting` object that manages ambient and directional lights.
- **Terrain**: Holds a reference to a `TerrainHeightProvider` (usually the `MapGenerator`) to allow entities to query ground height for physics.
- **Background**: Manages the background color of the scene.
