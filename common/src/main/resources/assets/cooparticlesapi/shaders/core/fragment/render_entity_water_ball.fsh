#version 330 core

in vec4 fragColor;
in vec2 texUv;
in vec3 viewPos;
in vec3 viewNormal;

uniform sampler2D noiseTex;
uniform vec4 tint;
uniform float time;
uniform float intensity;

out vec4 FragColor;

void main() {
    vec2 uv = vec2(fract(texUv.x), clamp(texUv.y, 0.001, 0.999));
    vec2 flowA = fract(uv * vec2(2.9, 1.55) + vec2(time * 0.026, -time * 0.014));
    vec2 flowB = fract(uv.yx * vec2(1.85, 3.45) + vec2(-time * 0.019, time * 0.024));
    vec3 noiseA = texture(noiseTex, flowA).rgb;
    vec3 noiseB = texture(noiseTex, flowB).rgb;

    float fineWave = noiseA.r * 0.55 + noiseB.g * 0.45;
    float crossing = abs(noiseA.b - noiseB.r);
    float crest = smoothstep(0.64, 0.96, fineWave + crossing * 0.34);
    float strand = smoothstep(0.86, 0.98, texture(noiseTex, fract(flowA * 2.15 + noiseB.rg * 0.08)).r);
    vec3 normal = normalize(viewNormal + vec3(noiseA.r - noiseB.g, noiseA.b - noiseB.r, 0.0) * 0.115);
    vec3 viewDir = normalize(-viewPos);
    float facing = clamp(dot(normal, viewDir), 0.0, 1.0);
    float edge = pow(1.0 - facing, 1.65);
    float rim = pow(1.0 - facing, 3.6);

    vec3 lightDir = normalize(vec3(-0.42, 0.58, 0.70));
    float specular = pow(max(dot(reflect(-lightDir, normal), viewDir), 0.0), 68.0) * (0.18 + crest * 0.34);

    vec3 deepWater = vec3(0.010, 0.105, 0.170);
    vec3 shallowWater = vec3(0.035, 0.245, 0.315);
    vec3 reflectedSky = vec3(0.34, 0.50, 0.54);
    vec3 base = mix(deepWater, tint.rgb, 0.34);
    vec3 water = mix(base, shallowWater, fineWave * 0.20);
    water = mix(water, reflectedSky, edge * 0.28);
    water += vec3(0.030, 0.070, 0.075) * strand * (1.0 - edge * 0.55);
    water += vec3(0.20, 0.24, 0.24) * specular;

    float alpha = tint.a * (0.045 + fineWave * 0.035 + crest * 0.025 + edge * 0.72 + rim * 0.18);
    float brightness = clamp(intensity, 0.0, 1.0);
    FragColor = vec4(water * brightness, clamp(alpha, 0.0, 0.52) * fragColor.a);
}
