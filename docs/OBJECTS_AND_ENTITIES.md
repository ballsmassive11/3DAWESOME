# Objects and Entities

This project distinguishes between static/decorative `BaseObject`s and dynamic/physical `Entity`s.

## BaseObject System (`src/objects/`)

`BaseObject` is the parent class for any individual 3D item in the world.
- **Transformations**: Manages `position`, `rotation`, and `scale` using Java3D `TransformGroup`s.
- **Appearance**: Handles colors, materials, and textures via the Java3D `Appearance` class.
- **Bounding Boxes**: Provides `AABB` (Axis-Aligned Bounding Box) for collision detection.
- **Subclasses**:
    - `Cube`: A simple geometric cube.
    - `MeshObject`: Loads complex geometry from `.obj` files. It includes a `modelCache` to avoid reloading the same file multiple times.

## Entity System (`src/entity/`)

`Entity` is an abstract class for objects that "live" in the world and have complex movement or behavior.
- **Physics**: Every entity has an `EntityPhysics` component that handles:
    - **Gravity**: Falling and landing on terrain.
    - **Jumping**: Applying upward force.
    - **Collision**: Resolving overlaps with world-space AABBs.
- **Player**: The primary implementation of `Entity`.
    - Controlled by the `Camera` (which handles WASD input).
    - Features "Shift-Lock" logic to toggle between free camera and character-locked rotation.
    - Uses a spring-damper system for smooth character rotation.

## When to use which?
- Use **BaseObject/MeshObject** for scenery, props, and static obstacles.
- Use **Entity** (or a subclass) for anything that moves, falls, or is controlled by AI/Player.
