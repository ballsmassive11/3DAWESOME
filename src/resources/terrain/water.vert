uniform float time;

varying vec2  vTC1;
varying vec2  vTC2;
varying vec3  vEyeNormal;
varying vec3  vEyePos;
varying vec3  vLighting;    // ambient + diffuse (no specular — done per-fragment)
varying vec3  vSpecDir;     // eye-space light 0 direction for per-fragment specular

void main() {
    vec2 uv = gl_MultiTexCoord0.st;
    vTC1 = uv * 5.0 + vec2( time * 0.04,  time * 0.025);
    vTC2 = uv * 4.0 + vec2(-time * 0.025, time * 0.04 );

    vec4 eyePos4 = gl_ModelViewMatrix * gl_Vertex;
    vEyePos    = eyePos4.xyz;
    vEyeNormal = normalize(gl_NormalMatrix * gl_Normal);

    // --- Per-vertex ambient + diffuse (matches terrain.vert pattern) ---
    vec3 totalLight = gl_FrontMaterial.ambient.rgb * gl_LightModel.ambient.rgb;

    for (int i = 0; i < gl_MaxLights; i++) {
        vec3  L;
        float atten = 1.0;
        if (gl_LightSource[i].position.w < 0.5) {
            L = normalize(vec3(gl_LightSource[i].position));
        } else {
            vec3  lvec = gl_LightSource[i].position.xyz - vEyePos;
            float dist = length(lvec);
            L = normalize(lvec);
            atten = 1.0 / (gl_LightSource[i].constantAttenuation
                         + gl_LightSource[i].linearAttenuation    * dist
                         + gl_LightSource[i].quadraticAttenuation * dist * dist);
        }
        float NdotL = max(dot(vEyeNormal, L), 0.0);
        totalLight += atten * NdotL
                    * gl_FrontMaterial.diffuse.rgb
                    * gl_LightSource[i].diffuse.rgb;
    }
    vLighting = clamp(totalLight, 0.0, 1.5);

    // Pass primary (directional) light direction for per-fragment specular
    vSpecDir = normalize(vec3(gl_LightSource[0].position));

    gl_FogFragCoord = abs(eyePos4.z);
    gl_Position     = gl_ModelViewProjectionMatrix * gl_Vertex;
}
