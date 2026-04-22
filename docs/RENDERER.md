# Renderer and Java3D

The project uses **Java3D** for rendering. The main interface for this is the `Game3DRenderer` class (`src/renderer/Game3DRenderer.java`).

## Java3D Scene Graph
Java3D uses a scene graph architecture. The `Game3DRenderer` sets up the `SimpleUniverse`, which includes:
- **Locale**: The origin of the 3D space.
- **ViewingPlatform**: Represents the camera's position and orientation.
- **BranchGroups**: Trees of 3D objects (Shapes, Lights, etc.) that are "compiled" and added to the universe.

## Key Renderer Features

### 1. Camera System
The renderer synchronizes the Java3D `View` with the `Player`'s `Camera` object.
- **Occlusion**: The `occlusionT` method performs basic ray-casting to ensure the camera doesn't clip through the ground when in 3rd-person view.
- **Smooth Following**: The camera uses interpolation to follow the player smoothly.

### 2. Day/Night Cycle
The renderer updates the scene's lighting and skybox based on a virtual time of day.
- Changes ambient light color and intensity.
- Swaps skybox textures (e.g., from `Default_Sky` to `night1`).
- Adjusts fog color and density to match the time.

### 3. GUI and HUD
The project uses a dual-canvas approach:
- **Main Canvas**: A `Canvas3D` for the 3D scene.
- **GUI Canvas**: A transparent overlay for 2D elements like the HUD, text, and commands.

### 4. Fog
Fog is used to hide the edges of the generated terrain and create atmosphere.
- `LinearFog` is used, with distance parameters that can be adjusted via the `Game3DRenderer` API.
