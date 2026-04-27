#version 120

uniform sampler2D fontTexture;
uniform float smoothing; // e.g. 0.1 / (actual_font_size / 57.0) or similar

varying vec2 vTexCoord;
varying vec4 vColor;

void main() {
    float distance = texture2D(fontTexture, vTexCoord).a;
    float alpha = smoothstep(0.5 - smoothing, 0.5 + smoothing, distance);
    gl_FragColor = vec4(vColor.rgb, vColor.a * alpha);
}
