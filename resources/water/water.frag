uniform sampler2D waterDuDvTex;   // unit 0
uniform sampler2D waterNormalTex; // unit 1
uniform float time;

varying vec2 vTC;
varying vec3 vEyeNormal;
varying vec3 vEyePos;
varying vec3 vSpecDir;

const float DISTORTION = 0.04;
const vec3 WATER_COLOR = vec3(0.1, 0.4, 0.6);

void main() {
    // ---- DuDv distortion -------------------------------------------------------
    vec2 dudvUV1 = vTC + vec2(time * 0.02, time * 0.01);
    vec2 dudvUV2 = vTC + vec2(time * -0.01, time * 0.02);
    vec2 dudv1 = texture2D(waterDuDvTex, dudvUV1).rg * 2.0 - 1.0;
    vec2 dudv2 = texture2D(waterDuDvTex, dudvUV2).rg * 2.0 - 1.0;
    vec2 dudv  = (dudv1 + dudv2) * 0.5 * DISTORTION;

    // ---- Normals ---------------------------------------------------------------
    vec2 normalUV = vTC + vec2(time * 0.01, -time * 0.01) + dudv;
    vec3 mapN = texture2D(waterNormalTex, normalUV).rgb * 2.0 - 1.0;
    vec3 N = normalize(vEyeNormal + vec3(mapN.x * 0.5, mapN.z * 0.3, mapN.y * 0.5));
    vec3 E = normalize(-vEyePos);

    // ---- Lighting --------------------------------------------------------------
    float diffuse = max(dot(N, vSpecDir), 0.0);
    vec3 color = WATER_COLOR * (0.6 + 0.4 * diffuse);

    // Specular
    vec3 R = reflect(-vSpecDir, N);
    float spec = pow(max(dot(R, E), 0.0), gl_FrontMaterial.shininess);
    vec3 specColor = spec * gl_FrontMaterial.specular.rgb * gl_LightSource[0].specular.rgb;

    vec3 litColor = color + specColor;

    // ---- Fog -------------------------------------------------------------------
    float fogDist = length(vEyePos);
    float fogFactor = clamp((gl_Fog.end - fogDist) / (gl_Fog.end - gl_Fog.start), 0.0, 1.0);
    vec3 finalColor = mix(gl_Fog.color.rgb, litColor, fogFactor);

    gl_FragColor = vec4(finalColor, 0.6);
}
