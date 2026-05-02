uniform sampler2D sandTex;
uniform sampler2D grassTex;
uniform sampler2D rockTex;
uniform sampler2D snowTex;
uniform sampler2D pathTex;

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
    vec4 path  = texture2D(pathTex,  vTexCoord);

    vec4 steppeColor;
    if (t < 0.12) {
        steppeColor = grass;
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
    vec4 desertColor = mix(sand, rock, clamp(t * 0.2, 0.0, 1.0)); // Hot/Dry: mostly sand
    vec4 forestColor = mix(grass, rock, clamp(t, 0.0, 1.0));      // Wet: mostly grass

    // Blend biomes
    vec4 texColor = steppeColor;
    texColor = mix(texColor, tundraColor, vBiomeWeights.r);
    texColor = mix(texColor, desertColor, vBiomeWeights.g);
    texColor = mix(texColor, forestColor, vBiomeWeights.b);

    // Draw path: use high G weight to signal path if R and B are low and G is very high (e.g. > 1.0)
    // Actually, I can just use a specific threshold. 
    // If G > 1.5, it's a path.
    if (vBiomeWeights.g > 1.5) {
        float pathWeight = clamp(vBiomeWeights.g - 1.5, 0.0, 1.0);
        texColor = mix(texColor, path, pathWeight);
    }

    vec3 litColor = texColor.rgb * vLighting;

    // Apply linear fog matching Java3D's LinearFog node
    float fogFactor = clamp((gl_Fog.end - gl_FogFragCoord) / (gl_Fog.end - gl_Fog.start), 0.0, 1.0);
    vec3 finalColor = mix(gl_Fog.color.rgb, litColor, fogFactor);

    gl_FragColor = vec4(finalColor, 1.0);
}
