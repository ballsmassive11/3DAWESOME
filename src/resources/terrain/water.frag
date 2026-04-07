uniform sampler2D waterNormalTex;

varying vec2  vTC1;
varying vec2  vTC2;
varying vec3  vEyeNormal;
varying vec3  vEyePos;
varying vec3  vLighting;
varying vec3  vSpecDir;

void main() {
    // Sample two scrolling normal map layers and combine
    vec3 n1 = texture2D(waterNormalTex, vTC1).rgb * 2.0 - 1.0;
    vec3 n2 = texture2D(waterNormalTex, vTC2).rgb * 2.0 - 1.0;
    vec3 bump = normalize(n1 + n2);

    // Perturb eye-space normal using the bump's XZ components.
    // For a flat horizontal surface the surface tangents map to eye-space X and Z.
    vec3 N = normalize(vEyeNormal + vec3(bump.x, bump.z, 0.0) * 0.45);
    vec3 E = normalize(-vEyePos);

    // Per-fragment specular from the primary directional light
    vec3 H    = normalize(vSpecDir + E);
    float NdotL = max(dot(N, vSpecDir), 0.0);
    float spec  = (NdotL > 0.0) ? pow(max(dot(N, H), 0.0), gl_FrontMaterial.shininess) : 0.0;
    vec3 specColor = spec * gl_FrontMaterial.specular.rgb * gl_LightSource[0].specular.rgb;

    // Base color + lighting
    vec3 baseColor  = vec3(0.0, 0.12, 0.55);
    vec3 litColor   = baseColor * vLighting + specColor;

    // Fog
    float fogFactor  = clamp((gl_Fog.end - gl_FogFragCoord) / (gl_Fog.end - gl_Fog.start), 0.0, 1.0);
    vec3  finalColor = mix(gl_Fog.color.rgb, litColor, fogFactor);

    gl_FragColor = vec4(finalColor, 0.35);
}
