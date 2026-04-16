uniform sampler2D waterDuDvTex;   // unit 0: DuDv map for wave distortion
uniform sampler2D waterNormalTex; // unit 1: normal map for wave lighting/fresnel
uniform float time;

varying vec2  vTC1;
varying vec2  vTC2;
varying vec3  vEyeNormal;
varying vec3  vEyePos;
varying vec3  vSpecDir;

const float DISTORTION  = 0.04;
const float FRESNEL_POW = 3.0;

const vec3 DEEP_COLOR    = vec3(0.04, 0.22, 0.42);
const vec3 SHALLOW_COLOR = vec3(0.10, 0.42, 0.70);
const vec3 SKY_ZENITH    = vec3(0.20, 0.52, 0.92);
const vec3 SKY_HORIZON   = vec3(0.72, 0.86, 1.00);
const vec3 SUBSURFACE    = vec3(0.04, 0.14, 0.26);

vec3 skyGradient(float worldY) {
    if (worldY >= 0.0) {
        float t = pow(clamp(worldY, 0.0, 1.0), 0.5);
        return mix(SKY_HORIZON, SKY_ZENITH, t);
    } else {
        float t = clamp(-worldY, 0.0, 1.0);
        return mix(SKY_HORIZON, SUBSURFACE, t);
    }
}

void main() {
    // ---- DuDv distortion (animated) -------------------------------------------
    vec2 dudvUV1 = vTC1 + vec2(time * 0.018,  time * 0.012);
    vec2 dudvUV2 = vTC2 + vec2(time * -0.010, time * 0.020);
    vec2 dudv1 = texture2D(waterDuDvTex, dudvUV1).rg * 2.0 - 1.0;
    vec2 dudv2 = texture2D(waterDuDvTex, dudvUV2).rg * 2.0 - 1.0;
    vec2 dudv  = (dudv1 + dudv2) * 0.5 * DISTORTION;

    // ---- Perturbed eye-space normal --------------------------------------------
    vec2 normalUV = vTC2 + vec2(time * 0.014, -time * 0.011) + dudv * 1.2;
    vec3 mapN = texture2D(waterNormalTex, normalUV).rgb * 2.0 - 1.0;
    vec3 N = normalize(vEyeNormal + vec3(mapN.x * 0.45 + dudv.x * 0.75,
                                         mapN.z * 0.35,
                                         mapN.y * 0.45 + dudv.y * 0.75));
    vec3 E = normalize(-vEyePos);

    // ---- Diffuse wave shading (makes ripples visible from above) ---------------
    // The perturbed normal shades differently from the light, giving visible texture.
    float diffuse     = max(dot(N, normalize(vSpecDir)), 0.0);
    float surfaceLight = 0.5 + diffuse * 0.5;  // 0.5 ambient + 0.5 diffuse

    // ---- Fresnel --------------------------------------------------------------
    float NdotE  = max(dot(N, E), 0.0);
    float fresnel = clamp(pow(1.0 - NdotE, FRESNEL_POW) * 1.2, 0.0, 1.0);

    // ---- Reflection: procedural sky gradient ----------------------------------
    vec3 eyeR   = reflect(-E, N);
    vec3 worldR = normalize((gl_ModelViewMatrixInverse * vec4(eyeR, 0.0)).xyz);
    vec3 reflColor = skyGradient(worldR.y);

    // ---- Water body color modulated by wave lighting --------------------------
    // Peaks/slopes catch more light → visible ripple pattern even from above.
    vec3 waterBody = mix(DEEP_COLOR, SHALLOW_COLOR, clamp(surfaceLight - 0.4, 0.0, 0.6))
                     * surfaceLight;

    // ---- Fresnel blend: always show at least 20% sky reflection ---------------
    float reflWeight = max(fresnel, 0.20);
    vec3 waterColor  = mix(waterBody, reflColor, reflWeight);

    // ---- Specular highlights --------------------------------------------------
    vec3  R     = reflect(-E, N);
    float RdotL = max(dot(R, vSpecDir), 0.0);
    float spec  = pow(RdotL, gl_FrontMaterial.shininess) * (0.3 + fresnel * 0.7);
    vec3  specColor = spec * gl_FrontMaterial.specular.rgb * gl_LightSource[0].specular.rgb;

    vec3 litColor = clamp(waterColor + specColor, 0.0, 1.0);

    // ---- Per-fragment fog -----------------------------------------------------
    float fogDist   = length(vEyePos);
    float fogFactor = clamp((gl_Fog.end - fogDist) / (gl_Fog.end - gl_Fog.start), 0.0, 1.0);
    vec3  finalColor = mix(gl_Fog.color.rgb, litColor, fogFactor);

    float alpha = mix(0.55, 0.90, fresnel);
    gl_FragColor = vec4(finalColor, alpha);
}
