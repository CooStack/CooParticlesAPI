#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

#ifndef COO_BRIGHTNESS
uniform float Brightness;
#define COO_BRIGHTNESS Brightness
#endif
#ifndef COO_ALPHA
uniform float Alpha;
#define COO_ALPHA Alpha
#endif

uniform sampler2D Sampler0;

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec4 overlayColor;
layout(location = 2) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.01) {
        discard;
    }
    color *= vertexColor * ColorModulator;
    float effectiveAlpha = color.a * COO_ALPHA;
    color.rgb *= effectiveAlpha;
    color.a = effectiveAlpha;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color.rgb *= COO_BRIGHTNESS;
    fragColor = color;
}
