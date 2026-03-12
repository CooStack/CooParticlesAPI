#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D glowMask;
uniform sampler2D distortionMask;
uniform sampler2D sceneTex;
uniform sampler2D blurredGlow;

void main() {
    vec4 glow = texture(glowMask, screen_uv);
    vec4 distortion = texture(distortionMask, screen_uv);

    vec3 scene = texture(sceneTex, screen_uv).rgb;
    vec3 blurred = texture(blurredGlow, screen_uv).rgb;
    vec2 bend = distortion.xy;
    float thickness = clamp(distortion.z, 0.0, 1.0);
    float coverage = clamp(distortion.w, 0.0, 1.0);
    float bendAmount = length(bend);
    vec2 tangent = bendAmount > 1.0e-5 ? vec2(-bend.y, bend.x) / bendAmount : vec2(0.0);
    float fresnel = pow(1.0 - thickness, 3.0);
    vec3 refracted = texture(sceneTex, clamp(screen_uv + bend, vec2(0.001), vec2(0.999))).rgb;
    vec3 refractedA = texture(
        sceneTex,
        clamp(screen_uv + bend * 0.70 + tangent * fresnel * 0.016, vec2(0.001), vec2(0.999))
    ).rgb;
    vec3 refractedB = texture(sceneTex, clamp(screen_uv + bend * 1.45, vec2(0.001), vec2(0.999))).rgb;
    vec3 refractedC = texture(
        sceneTex,
        clamp(screen_uv + bend * 0.85 - tangent * fresnel * 0.016, vec2(0.001), vec2(0.999))
    ).rgb;
    vec3 bentScene = refracted * 0.34 + refractedA * 0.20 + refractedB * 0.30 + refractedC * 0.16;

    vec3 halo = glow.rgb * 0.28 + blurred * (1.45 + glow.a * 1.05 + coverage * 0.90);

    vec3 color = mix(scene, bentScene, coverage * (0.52 + fresnel * 0.56));
    color += glow.rgb * (0.55 + glow.a * 1.10 + fresnel * 1.35);
    color += halo * (1.20 + fresnel * 1.35);
    color = mix(color, glow.rgb * 0.65 + halo * 1.35 + color * 0.36, glow.a * (0.22 + fresnel * 0.52));

    FragColor = vec4(color, 1.0);
}
