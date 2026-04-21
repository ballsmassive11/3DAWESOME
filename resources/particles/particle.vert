// Particle vertex shader
// Passes UV (from atlas), RGBA vertex color, and fog depth to the fragment stage.
// Billboard geometry is built on the CPU each frame in ParticleRenderer.

varying vec2 vTexCoord;
varying vec4 vColor;

void main() {
    vTexCoord = gl_MultiTexCoord0.st;
    vColor    = gl_Color;

    vec4 eyePos    = gl_ModelViewMatrix * gl_Vertex;
    gl_FogFragCoord = abs(eyePos.z);
    gl_Position    = gl_ModelViewProjectionMatrix * gl_Vertex;

    // Required for user clip planes (e.g. water RTT) to work correctly with GLSL.
    gl_ClipVertex  = eyePos;
}
