# MeshObject

`MeshObject` is a `BaseObject` subclass that loads a 3D model from a Wavefront OBJ file and places it in the scene graph.

## Constructors

```java
// Load an OBJ file; apply the default grey material from BaseObject
MeshObject obj = new MeshObject("src/resources/CyberTruck/Cybertruck.obj");

// Load an OBJ file and use the materials defined in the accompanying .mtl file
MeshObject obj = new MeshObject("src/resources/Challenger/Challenger.obj", true);
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `modelPath` | `String` | Path to the `.obj` file (relative to the project root or absolute) |
| `useModelMaterials` | `boolean` | `true` → honour the `.mtl` file; `false` → override with `BaseObject`'s grey material |

## How loading works

1. **Read the OBJ file line-by-line** and apply two preprocessing fixes required by `com.sun.j3d.loaders.objectfile.ObjectFile`:
   - Lines beginning with `o ` (named objects) are stripped — the loader does not handle them.
   - Each `usemtl <name>` line gets a `g <name>` group line prepended so Java3D can associate geometry with a material.

2. **Write a temporary file** next to the original (prefixed `_tmp_`) so that relative MTL/texture paths in the OBJ file still resolve correctly.

3. **Load via `ObjectFile`** with the `RESIZE` flag, which auto-scales the model to fit within a unit cube.

4. **Apply appearance** — if `useModelMaterials` is `false`, `applyAppearanceToGroup` walks every `Shape3D` node in the loaded scene and replaces its appearance with the one from `BaseObject`.

5. **Count polygons** — `countPolygons` recurses the scene graph and tallies triangles (supports `TriangleArray`, `QuadArray`, strip/fan variants and their indexed equivalents).

6. **Clean up** — the temporary file is always deleted in the `finally` block.

## Adding to the scene

```java
MeshObject car = new MeshObject("src/resources/CyberTruck/Cybertruck.obj", true);
car.setPosition(10.0, 0.5, -5.0);
car.setScale(2.0);                              // uniform scale
car.setRotationEuler(0, Math.PI / 2, 0);        // 90° yaw

world.addObject(car);                           // world calls getBranchGroup() internally
```

`getBranchGroup()` attaches the loaded scene group under the `TransformGroup` (which carries position, rotation, and scale) and returns the `BranchGroup` ready for the Java3D scene graph.

## Transform methods (inherited from BaseObject)

| Method | Description |
|--------|-------------|
| `setPosition(x, y, z)` | Absolute world position |
| `translate(dx, dy, dz)` | Relative offset |
| `setScale(s)` | Uniform scale |
| `setScale(x, y, z)` | Per-axis scale |
| `setRotation(Quat4d)` | Quaternion rotation (no gimbal lock) |
| `setRotation(axis, angle)` | Axis-angle rotation |
| `setRotationEuler(pitch, yaw, roll)` | Euler angles converted to quaternion internally |
| `rotate(axis, angle)` | Incremental rotation |
| `setVelocity(x, y, z)` | Constant linear velocity (units/second) |
| `setAngularVelocity(x, y, z)` | Constant angular velocity (radians/second per axis) |

## OBJ/MTL requirements

- The `.mtl` file must live in the same directory as the `.obj` file.
- Texture images referenced by the `.mtl` file must also be in the same directory.
- Object names (`o`) in the OBJ file are ignored; use material groups (`usemtl`) to separate surfaces.

## Notes

- `RESIZE` rescales the model so its longest axis fits within `[-1, 1]`. Scale up with `setScale` after loading if needed.
- `getBranchGroup()` can only be called once per instance — Java3D nodes cannot be shared between branch groups. If re-adding to the scene is needed, construct a new `MeshObject`.
