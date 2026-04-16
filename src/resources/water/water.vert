uniform float time;

varying vec2  vTC1;
varying vec2  vTC2;
varying vec3  vEyeNormal;
varying vec3  vEyePos;
varying vec3  vSpecDir;   // eye-space direction toward primary light

void main() {
    vec2 uv = gl_MultiTexCoord0.st;

    // No more normal-map UV scrolling for now
    vTC1 = uv * 5.0;
    vTC2 = uv * 4.0;

    vec4 eyePos4 = gl_ModelViewMatrix * gl_Vertex;
    vEyePos      = eyePos4.xyz;
    vEyeNormal   = normalize(gl_NormalMatrix * gl_Normal);

    // Primary directional light direction in eye space, for per-fragment specular
    vSpecDir = normalize(vec3(gl_LightSource[0].position));

    gl_FogFragCoord = abs(eyePos4.z);
    gl_Position     = gl_ModelViewProjectionMatrix * gl_Vertex;

    // Eye-space position for user clip plane (GL_CLIP_PLANE0) used during RTT passes.
    gl_ClipVertex   = eyePos4;
}
