# Terrain Generation

The project features a procedural terrain system driven by the `MapGenerator` class (`src/terrain/MapGenerator.java`).

## MapGenerator

The `MapGenerator` implements the `TerrainHeightProvider` interface, allowing other systems (like physics) to query the height of the ground at any given (x, z) coordinate.

### Height Generation
It uses Perlin or Simplex noise to generate height maps. The generation is split into two main layers:
- **Biomes**: Large-scale variations determining the general landscape (e.g., plains vs. mountains).
- **Hills**: Smaller-scale detail and rolling hills.

### Features
- **Seeding**: Supports seed-based generation for reproducible worlds.
- **Procedural Coloring**: Terrain vertices are colored based on their height and biome (e.g., sandy beaches, grassy plains, snowy peaks).
- **Object Scattering**: Automatically spawns decorative objects like streetlamps at appropriate locations on the generated terrain.

## Physics Integration
Because `MapGenerator` implements `TerrainHeightProvider`, the `EntityPhysics` system can use it to:
- Clamp entities to the ground.
- Calculate slope-based movement.
- Determine if an entity is "on ground".

## Rendering the Terrain
The generator produces a large Java3D `Shape3D` object (usually a `TriangleStripArray` or `IndexedTriangleArray`) that is added to the `World`. The appearance is managed by a `ShaderAppearance` that can handle advanced effects like multi-texturing or custom shading.
