uniform float time;
uniform float tileCenterX;
uniform float tileCenterZ;

varying vec2 vTC;
varying vec3 vEyeNormal;
varying vec3 vEyePos;
varying vec3 vSpecDir;
varying vec3 vDirSpecColor;

// Scan all 8 light slots for the directional light (w == 0).
// Java3D assigns different street-lamp subsets per object, so the sun
// can land at any index — we must not assume index 0.
vec3 findDirLightDir() {
    if (gl_LightSource[0].position.w < 0.5) return normalize(vec3(gl_LightSource[0].position));
    if (gl_LightSource[1].position.w < 0.5) return normalize(vec3(gl_LightSource[1].position));
    if (gl_LightSource[2].position.w < 0.5) return normalize(vec3(gl_LightSource[2].position));
    if (gl_LightSource[3].position.w < 0.5) return normalize(vec3(gl_LightSource[3].position));
    if (gl_LightSource[4].position.w < 0.5) return normalize(vec3(gl_LightSource[4].position));
    if (gl_LightSource[5].position.w < 0.5) return normalize(vec3(gl_LightSource[5].position));
    if (gl_LightSource[6].position.w < 0.5) return normalize(vec3(gl_LightSource[6].position));
    if (gl_LightSource[7].position.w < 0.5) return normalize(vec3(gl_LightSource[7].position));
    return vec3(0.0, -1.0, 0.0); // fallback: straight down
}

vec3 findDirLightSpecColor() {
    if (gl_LightSource[0].position.w < 0.5) return gl_LightSource[0].specular.rgb;
    if (gl_LightSource[1].position.w < 0.5) return gl_LightSource[1].specular.rgb;
    if (gl_LightSource[2].position.w < 0.5) return gl_LightSource[2].specular.rgb;
    if (gl_LightSource[3].position.w < 0.5) return gl_LightSource[3].specular.rgb;
    if (gl_LightSource[4].position.w < 0.5) return gl_LightSource[4].specular.rgb;
    if (gl_LightSource[5].position.w < 0.5) return gl_LightSource[5].specular.rgb;
    if (gl_LightSource[6].position.w < 0.5) return gl_LightSource[6].specular.rgb;
    if (gl_LightSource[7].position.w < 0.5) return gl_LightSource[7].specular.rgb;
    return vec3(1.0);
}

void main() {
    // World-space XZ → continuous UV across all tiles.
    // 37.5 WU per texture repeat (= default TILE_SIZE 150 / tileRepeat 4).
    // Because this uses absolute world coords, adjacent tiles always produce
    // matching UV values at their shared edges, eliminating visible seams.
    vec2 worldXZ = vec2(tileCenterX, tileCenterZ) + gl_Vertex.xz;
    vTC = worldXZ / 37.5;

    vec4 eyePos4 = gl_ModelViewMatrix * gl_Vertex;
    vEyePos      = eyePos4.xyz;
    vEyeNormal   = normalize(gl_NormalMatrix * gl_Normal);

    vSpecDir      = findDirLightDir();
    vDirSpecColor = findDirLightSpecColor();

    gl_FogFragCoord = abs(eyePos4.z);
    gl_Position     = gl_ModelViewProjectionMatrix * gl_Vertex;
}
