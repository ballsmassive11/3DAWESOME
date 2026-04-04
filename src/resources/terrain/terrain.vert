varying vec2  vTexCoord;
varying float vBlendT;
varying vec3  vLighting;

void main() {
    vTexCoord = gl_MultiTexCoord0.st;
    vBlendT   = gl_Color.a;

    vec4 eyePos = gl_ModelViewMatrix * gl_Vertex;

    vec3 n = normalize(gl_NormalMatrix * gl_Normal);
    vec3 L = normalize(vec3(gl_LightSource[0].position));

    vec3  ambient  = gl_FrontMaterial.ambient.rgb * gl_LightModel.ambient.rgb;
    float NdotL    = max(dot(n, L), 0.0);
    vec3  diffuse  = NdotL * gl_FrontMaterial.diffuse.rgb * gl_LightSource[0].diffuse.rgb;

    vec3  E       = normalize(-eyePos.xyz);
    vec3  H       = normalize(L + E);
    float spec    = (NdotL > 0.0) ? pow(max(dot(n, H), 0.0), gl_FrontMaterial.shininess) : 0.0;
    vec3  specular = spec * gl_FrontMaterial.specular.rgb * gl_LightSource[0].specular.rgb;

    vLighting        = clamp(ambient + diffuse + specular, 0.0, 1.5);
    gl_FogFragCoord  = abs(eyePos.z);
    gl_Position      = gl_ModelViewProjectionMatrix * gl_Vertex;
}
