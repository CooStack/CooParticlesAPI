#version 150

#coo_import <terrain_light_fog.glsl>

uniform sampler2D StarfieldSampler;
uniform vec2 ScreenSize;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 baseUv;
in vec2 effectUv;
in vec3 worldPosition;
in vec3 worldNormal;

out vec4 fragColor;

void main() {
    vec2 screenUv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
    vec4 starfield = texture(StarfieldSampler, screenUv);
    starfield.a *= vertexColor.a * ColorModulator.a;
    fragColor = linear_fog(starfield, vertexDistance, FogStart, FogEnd, FogColor);
}
