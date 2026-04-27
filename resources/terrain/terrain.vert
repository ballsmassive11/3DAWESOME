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

    // --- Directional light (sun) ---
    // Java3D assigns light slots per-object based on influencing bounds, so the
    // directional light can land at any index (not necessarily 0).  Scan for it
    // and process it exactly once; iterating naively over gl_MaxLights would pick
    // up stale point-light data left in unused slots from previous renders and
    // produce view-angle-dependent shading artefacts.
    for (int i = 0; i < 8; i++) {
        if (gl_LightSource[i].position.w < 0.5) {
            vec3  L     = normalize(vec3(gl_LightSource[i].position));
            float NdotL = max(dot(n, L), 0.0);
            vec3  H     = normalize(L + E);
            float spec  = (NdotL > 0.0) ? pow(max(dot(n, H), 0.0), gl_FrontMaterial.shininess) : 0.0;
            totalLight += NdotL * gl_FrontMaterial.diffuse.rgb  * gl_LightSource[i].diffuse.rgb
                        + spec  * gl_FrontMaterial.specular.rgb * gl_LightSource[i].specular.rgb;
            break; // process only the first directional light
        }
    }

    // --- Point lights (street lamps) ---
    // Only accumulate a slot if its attenuation at this vertex is non-negligible.
    // This filters out stale slots whose eye-space positions were baked in a
    // previous frame under a different view matrix (the real source of the
    // view-angle-dependent shading artefact).
    for (int i = 0; i < 8; i++) {
        if (gl_LightSource[i].position.w >= 0.5) {
            vec3  lvec  = gl_LightSource[i].position.xyz - eyePos.xyz;
            float dist  = length(lvec);
            float atten = 1.0 / (gl_LightSource[i].constantAttenuation
                                + gl_LightSource[i].linearAttenuation    * dist
                                + gl_LightSource[i].quadraticAttenuation * dist * dist);
            if (atten < 0.01) continue; // stale / too far away — skip
            vec3  L     = normalize(lvec);
            float NdotL = max(dot(n, L), 0.0);
            vec3  H     = normalize(L + E);
            float spec  = (NdotL > 0.0) ? pow(max(dot(n, H), 0.0), gl_FrontMaterial.shininess) : 0.0;
            totalLight += atten * (NdotL * gl_FrontMaterial.diffuse.rgb  * gl_LightSource[i].diffuse.rgb
                                 + spec  * gl_FrontMaterial.specular.rgb * gl_LightSource[i].specular.rgb);
        }
    }

    vLighting       = clamp(totalLight, 0.0, 1.5);
    gl_FogFragCoord = abs(eyePos.z);
    gl_Position     = gl_ModelViewProjectionMatrix * gl_Vertex;

    // Required for user clip planes (GL_CLIP_PLANE0) to work with GLSL shaders.
    // Without this, clip planes compare against gl_Position (clip space) rather
    // than eye space, giving wrong results.
    gl_ClipVertex   = eyePos;
}
