uniform sampler2D waterDuDvTex;   // unit 0
uniform sampler2D waterNormalTex; // unit 1
uniform float time;

varying vec2 vTC;
varying vec3 vEyeNormal;
varying vec3 vEyePos;
varying vec3 vSpecDir;
varying vec3 vDirSpecColor;

const float DISTORTION = 0.04;
const vec3 WATER_COLOR = vec3(0.2, 0.5, 0.7);

void main() {
    // ---- DuDv distortion -------------------------------------------------------
    vec2 dudvUV1 = vTC + vec2(time * 0.02, time * 0.01);
    vec2 dudvUV2 = vTC + vec2(time * -0.01, time * 0.02);
    vec2 dudv1 = texture2D(waterDuDvTex, dudvUV1).rg * 2.0 - 1.0;
    vec2 dudv2 = texture2D(waterDuDvTex, dudvUV2).rg * 2.0 - 1.0;
    vec2 dudv  = (dudv1 + dudv2) * 0.5 * DISTORTION;

    // ---- Normals (stronger influence so ripples are visible in bright light) ---
    vec2 normalUV = vTC + vec2(time * 0.01, -time * 0.01) + dudv;
    vec3 mapN = texture2D(waterNormalTex, normalUV).rgb * 2.0 - 1.0;
    vec3 N = normalize(vEyeNormal + vec3(mapN.x * 1.2, mapN.z * 0.8, mapN.y * 1.2));
    vec3 E = normalize(-vEyePos);

    // ---- Lighting --------------------------------------------------------------
    float diffuse = max(dot(N, vSpecDir), 0.0);
    // Raise the dark floor so backlit ripple faces don't go too black
    vec3 color = WATER_COLOR * (0.55 + 0.45 * diffuse);

    // Specular
    vec3 R = reflect(-vSpecDir, N);
    float spec = pow(max(dot(R, E), 0.0), gl_FrontMaterial.shininess);
    vec3 specColor = spec * gl_FrontMaterial.specular.rgb * vDirSpecColor;

    // Fresnel: at grazing angles the surface shimmers — makes ripples pop in daylight
    float fresnel = pow(1.0 - max(dot(N, E), 0.0), 4.0);
    vec3 fresnelGlint = vDirSpecColor * fresnel * 0.45;

    vec3 litColor = color + specColor + fresnelGlint;

    // ---- Fog -------------------------------------------------------------------
    float fogDist = length(vEyePos);
    float fogFactor = clamp((gl_Fog.end - fogDist) / (gl_Fog.end - gl_Fog.start), 0.0, 1.0);
    vec3 finalColor = mix(gl_Fog.color.rgb, litColor, fogFactor);

    gl_FragColor = vec4(finalColor, 1.0);
}
