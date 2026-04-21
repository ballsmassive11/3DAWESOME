// Particle fragment shader
// Samples the sprite from the texture atlas, multiplies by the vertex RGBA tint,
// and applies linear fog consistent with the rest of the scene.

uniform sampler2D atlasTex;

varying vec2 vTexCoord;
varying vec4 vColor;

void main() {
    vec4 sprite = texture2D(atlasTex, vTexCoord);

    // Multiply atlas sprite RGBA by particle tint color (vertex color carries interpolated RGBA).
    // If the atlas sprite is solid white (fallback), this reduces to pure vertex color.
    vec4 color = sprite * vColor;

    // Discard fully transparent fragments to avoid writing to the depth buffer for invisible pixels.
    if (color.a < 0.004) discard;

    // Match the scene's LinearFog
    float fogFactor  = clamp((gl_Fog.end - gl_FogFragCoord) / (gl_Fog.end - gl_Fog.start), 0.0, 1.0);
    vec3  finalColor = mix(gl_Fog.color.rgb, color.rgb, fogFactor);

    gl_FragColor = vec4(finalColor, color.a);
}
