uniform sampler2D sandTex;
uniform sampler2D grassTex;
uniform sampler2D rockTex;
uniform sampler2D snowTex;

varying vec2  vTexCoord;
varying float vBlendT;
varying vec3  vLighting;
varying vec3  vBiomeWeights; // R=Tundra, G=Desert, B=Forest, (Steppe if all 0)

void main() {
    float t = clamp(vBlendT, 0.0, 1.0);

    // Default height-based textures (used for Steppe and mixed with others)
    vec4 sand  = texture2D(sandTex,  vTexCoord);
    vec4 grass = texture2D(grassTex, vTexCoord);
    vec4 rock  = texture2D(rockTex,  vTexCoord);
    vec4 snow  = texture2D(snowTex,  vTexCoord);

    vec4 steppeColor;
    if (t < 0.12) {
        steppeColor = mix(sand, grass, t / 0.12);
    } else if (t < 0.50) {
        steppeColor = mix(grass, rock, (t - 0.12) / 0.38);
    } else if (t < 0.78) {
        steppeColor = mix(rock, snow, (t - 0.50) / 0.28);
    } else {
        steppeColor = snow;
    }

    // Biome-specific variations
    // Tundra/Cold: snow at low blendT (plains), transitioning to rock at higher blendT (slopes)
    vec4 tundraColor = mix(snow, rock, clamp(t * 2.0, 0.0, 1.0)); 
    vec4 desertColor = mix(sand, rock, clamp(t * 0.5, 0.0, 1.0)); // Hot/Dry: mostly sand
    vec4 forestColor = mix(grass, rock, clamp(t, 0.0, 1.0));      // Wet: mostly grass

    // Blend biomes
    vec4 texColor = steppeColor;
    texColor = mix(texColor, tundraColor, vBiomeWeights.r);
    texColor = mix(texColor, desertColor, vBiomeWeights.g);
    texColor = mix(texColor, forestColor, vBiomeWeights.b);

    vec3 litColor = texColor.rgb * vLighting;

    // Apply linear fog matching Java3D's LinearFog node
    float fogFactor = clamp((gl_Fog.end - gl_FogFragCoord) / (gl_Fog.end - gl_Fog.start), 0.0, 1.0);
    vec3 finalColor = mix(gl_Fog.color.rgb, litColor, fogFactor);

    gl_FragColor = vec4(finalColor, 1.0);
}
