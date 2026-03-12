#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D holeMask;
uniform sampler2D distortionMask;
uniform sampler2D sceneTex;

void main() {
    vec4 hole = texture(holeMask, screen_uv);
    vec4 distortion = texture(distortionMask, screen_uv);

    vec3 scene = texture(sceneTex, screen_uv).rgb;
    vec2 bend = distortion.xy;
    float darkness = clamp(distortion.z, 0.0, 1.0);
    float coverage = clamp(distortion.w, 0.0, 1.0);
    float bendAmount = length(bend);
    vec2 tangent = bendAmount > 1.0e-5 ? vec2(-bend.y, bend.x) / bendAmount : vec2(0.0);
    float edge = pow(clamp(1.0 - darkness, 0.0, 1.0), 1.15);

    vec3 refracted = texture(sceneTex, clamp(screen_uv + bend, vec2(0.001), vec2(0.999))).rgb;
    vec3 deep = texture(
        sceneTex,
        clamp(screen_uv + bend * (1.35 + edge * 0.85), vec2(0.001), vec2(0.999))
    ).rgb;
    vec3 shearA = texture(
        sceneTex,
        clamp(screen_uv + bend * 0.55 + tangent * (0.020 + coverage * 0.012), vec2(0.001), vec2(0.999))
    ).rgb;
    vec3 shearB = texture(
        sceneTex,
        clamp(screen_uv + bend * 0.55 - tangent * (0.020 + coverage * 0.012), vec2(0.001), vec2(0.999))
    ).rgb;
    vec3 inward = texture(sceneTex, clamp(screen_uv - bend * 0.22, vec2(0.001), vec2(0.999))).rgb;
    vec3 orbitA = texture(
        sceneTex,
        clamp(screen_uv + bend * (1.40 + edge * 0.90) + tangent * (0.032 + coverage * 0.016), vec2(0.001), vec2(0.999))
    ).rgb;
    vec3 orbitB = texture(
        sceneTex,
        clamp(screen_uv + bend * (1.40 + edge * 0.90) - tangent * (0.032 + coverage * 0.016), vec2(0.001), vec2(0.999))
    ).rgb;
    vec3 farField = texture(
        sceneTex,
        clamp(screen_uv + bend * (1.95 + edge * 1.10), vec2(0.001), vec2(0.999))
    ).rgb;

    vec3 bentLight = refracted * 0.18
        + deep * 0.16
        + shearA * 0.10
        + shearB * 0.10
        + inward * 0.08
        + orbitA * 0.16
        + orbitB * 0.16
        + farField * 0.06;
    float lensMix = clamp(coverage * (0.66 + edge * 0.72) + bendAmount * 4.2, 0.0, 1.0);
    vec3 color = mix(scene, bentLight, lensMix);
    color += (bentLight - scene) * coverage * (0.08 + edge * 0.12);
    color = mix(color, color * 0.002, clamp(hole.a, 0.0, 1.0));

    float ring = max(max(hole.r, hole.g), hole.b);
    color += hole.rgb * (1.55 + edge * 2.60);
    color += bentLight * ring * (0.10 + edge * 0.22);
    color -= vec3(0.02, 0.015, 0.01) * clamp(hole.a * coverage * 0.8, 0.0, 1.0);

    FragColor = vec4(color, 1.0);
}
