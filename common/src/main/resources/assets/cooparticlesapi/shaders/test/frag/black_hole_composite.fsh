#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D holeMask;
uniform sampler2D distortionMask;
uniform sampler2D sceneTex;

void main() {
    vec4 hole = texture(holeMask, screen_uv);
    vec4 distortion = texture(distortionMask, screen_uv);

    vec2 refractedUv = clamp(screen_uv + distortion.xy, vec2(0.001), vec2(0.999));
    vec3 scene = texture(sceneTex, screen_uv).rgb;
    vec3 refracted = texture(sceneTex, refractedUv).rgb;

    vec3 color = mix(scene, refracted, clamp(distortion.w, 0.0, 1.0));
    color = mix(color, color * 0.02, clamp(hole.a, 0.0, 1.0));
    color += hole.rgb;

    FragColor = vec4(color, 1.0);
}
