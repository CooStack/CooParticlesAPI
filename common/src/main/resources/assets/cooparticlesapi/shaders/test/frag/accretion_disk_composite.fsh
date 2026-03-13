#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D glowMask;
uniform sampler2D distortionMask;
uniform sampler2D sceneLightMask;
uniform sampler2D sceneTex;
uniform sampler2D blurredGlow;
uniform sampler2D sceneDepth;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec4 glow = texture(glowMask, screen_uv);
    vec4 distortion = texture(distortionMask, screen_uv);
    vec4 sceneLight = texture(sceneLightMask, screen_uv);

    vec3 scene = texture(sceneTex, screen_uv).rgb;
    vec2 sampleUv = clamp(screen_uv + distortion.xy, vec2(0.001), vec2(0.999));
    vec3 lensedScene = texture(sceneTex, sampleUv).rgb;
    vec3 blurred = texture(blurredGlow, screen_uv).rgb;

    float sceneDepthSample = texture(sceneDepth, screen_uv).r;
    float horizonMask = saturate(distortion.z);
    float coverage = saturate(distortion.w);
    float diskOpacity = saturate(glow.a);
    float sceneLightWeight = saturate(sceneLight.a);
    float emissionWeight = saturate(luminance(glow.rgb) * 0.085);
    float deflection = length(distortion.xy);
    float diskPresence = saturate(max(diskOpacity * 1.05, emissionWeight * 1.20));
    float lensMix = saturate(coverage * 0.92 + deflection * 18.0);

    vec3 refracted = mix(scene, lensedScene, lensMix);
    vec3 caustic = (lensedScene - scene) * saturate(deflection * 5.4 + coverage * 0.15) * (1.0 - diskPresence * 0.62);
    vec3 diskExtinctionTint = mix(vec3(1.0), vec3(0.92, 0.78, 0.52), diskPresence * 0.30);
    vec3 color = refracted * (1.0 - diskPresence * 1.18) * diskExtinctionTint + caustic * 0.16;

    vec3 bloom = blurred * (0.70 + emissionWeight * 1.40 + diskPresence * 0.58);
    color += glow.rgb * (1.24 + diskPresence * 0.42);
    color += bloom;
    color += sceneLight.rgb * (0.98 + sceneLightWeight * 0.34 + diskPresence * 0.12);

    // Input pass already clipped against shared scene depth.
    float farBoost = smoothstep(0.82, 1.0, sceneDepthSample);
    color += glow.rgb * farBoost * (0.08 + diskPresence * 0.12);
    color = mix(color, vec3(0.0), horizonMask);

    FragColor = vec4(color, 1.0);
}
