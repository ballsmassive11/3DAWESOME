varying vec2  vTexCoord;
varying float vBlendT;
varying vec3  vLighting;

void main() {
    vTexCoord = gl_MultiTexCoord0.st;
    vBlendT   = gl_Color.a;

    vec4 eyePos = gl_ModelViewMatrix * gl_Vertex;
    vec3 n = normalize(gl_NormalMatrix * gl_Normal);
    vec3 E = normalize(-eyePos.xyz);

    // Start with scene ambient
    vec3 totalLight = gl_FrontMaterial.ambient.rgb * gl_LightModel.ambient.rgb;

    // Accumulate contributions from all active lights (directional + point)
    for (int i = 0; i < gl_MaxLights; i++) {
        vec3  L;
        float atten = 1.0;

        if (gl_LightSource[i].position.w < 0.5) {
            // Directional light: position.w == 0, xyz encodes direction in eye space
            L = normalize(vec3(gl_LightSource[i].position));
        } else {
            // Point light: position.w == 1, xyz is position in eye space
            vec3  lvec = gl_LightSource[i].position.xyz - eyePos.xyz;
            float dist = length(lvec);
            L = normalize(lvec);
            atten = 1.0 / (gl_LightSource[i].constantAttenuation
                         + gl_LightSource[i].linearAttenuation    * dist
                         + gl_LightSource[i].quadraticAttenuation * dist * dist);
        }

        float NdotL   = max(dot(n, L), 0.0);
        vec3  H       = normalize(L + E);
        float spec    = (NdotL > 0.0) ? pow(max(dot(n, H), 0.0), gl_FrontMaterial.shininess) : 0.0;

        totalLight += atten * (NdotL * gl_FrontMaterial.diffuse.rgb  * gl_LightSource[i].diffuse.rgb
                             + spec  * gl_FrontMaterial.specular.rgb * gl_LightSource[i].specular.rgb);
    }

    vLighting       = clamp(totalLight, 0.0, 1.5);
    gl_FogFragCoord = abs(eyePos.z);
    gl_Position     = gl_ModelViewProjectionMatrix * gl_Vertex;

    // Required for user clip planes (GL_CLIP_PLANE0) to work with GLSL shaders.
    // Without this, clip planes compare against gl_Position (clip space) rather
    // than eye space, giving wrong results.
    gl_ClipVertex   = eyePos;
}
