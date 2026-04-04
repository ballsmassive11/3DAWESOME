uniform sampler2D sandTex;
uniform sampler2D grassTex;
uniform sampler2D rockTex;
uniform sampler2D snowTex;

varying vec2  vTexCoord;
varying float vBlendT;
varying vec3  vLighting;

void main() {
    float t = clamp(vBlendT, 0.0, 1.0);

    vec4 texColor;
    if (t < 0.12) {
        texColor = mix(texture2D(sandTex,  vTexCoord),
                       texture2D(grassTex, vTexCoord), t / 0.12);
    } else if (t < 0.50) {
        texColor = mix(texture2D(grassTex, vTexCoord),
                       texture2D(rockTex,  vTexCoord), (t - 0.12) / 0.38);
    } else if (t < 0.78) {
        texColor = mix(texture2D(rockTex,  vTexCoord),
                       texture2D(snowTex,  vTexCoord), (t - 0.50) / 0.28);
    } else {
        texColor = texture2D(snowTex, vTexCoord);
    }

    vec3 litColor = texColor.rgb * vLighting;

    // Apply linear fog matching Java3D's LinearFog node
    float fogFactor = clamp((gl_Fog.end - gl_FogFragCoord) / (gl_Fog.end - gl_Fog.start), 0.0, 1.0);
    vec3 finalColor = mix(gl_Fog.color.rgb, litColor, fogFactor);

    gl_FragColor = vec4(finalColor, 1.0);
}
