uniform float time;

varying vec2 vTC;
varying vec3 vEyeNormal;
varying vec3 vEyePos;
varying vec3 vSpecDir;

void main() {
    vTC = gl_MultiTexCoord0.st * 4.0;

    vec4 eyePos4 = gl_ModelViewMatrix * gl_Vertex;
    vEyePos      = eyePos4.xyz;
    vEyeNormal   = normalize(gl_NormalMatrix * gl_Normal);

    // Primary directional light direction in eye space
    vSpecDir = normalize(vec3(gl_LightSource[0].position));

    gl_FogFragCoord = abs(eyePos4.z);
    gl_Position     = gl_ModelViewProjectionMatrix * gl_Vertex;
}
