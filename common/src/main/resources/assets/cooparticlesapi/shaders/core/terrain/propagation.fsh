#version 150

#coo_import <terrain_light_fog.glsl>

uniform sampler2D BaseSampler;
uniform sampler2D SceneColor;
uniform float TintStrength;
uniform float CooAlphaCutoff;
uniform vec2 ScreenSize;
uniform int CooIrisComposite;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 baseUv;
flat in float effectElapsedTicks;

out vec4 fragColor;

float eased(float value) {
    return smoothstep(0.0, 1.0, clamp(value, 0.0, 1.0));
}

void main() {
    vec4 atlasColor = texture(BaseSampler, baseUv) * vertexColor * ColorModulator;
    if (atlasColor.a < CooAlphaCutoff) {
        discard;
    }
    vec3 baseRgb = atlasColor.rgb;
    if (CooIrisComposite != 0) {
        baseRgb = texture(SceneColor, gl_FragCoord.xy / ScreenSize).rgb;
    }
    vec3 white = vec3(1.0);
    vec3 copperRed = vec3(0.72, 0.28, 0.12);
    vec3 verdigris = vec3(0.22, 0.70, 0.38);
    vec3 effectTint = white;
    float effectStrength = 0.0;
    float ticks = effectElapsedTicks;
    if (ticks < 10.0) {
        effectStrength = eased(ticks / 10.0);
    } else if (ticks < 20.0) {
        effectTint = mix(white, copperRed, eased((ticks - 10.0) / 10.0));
        effectStrength = 1.0;
    } else if (ticks < 30.0) {
        effectTint = mix(copperRed, verdigris, eased((ticks - 20.0) / 10.0));
        effectStrength = 1.0;
    } else if (ticks < 60.0) {
        effectTint = verdigris;
        effectStrength = 1.0;
    } else if (ticks < 70.0) {
        effectTint = mix(verdigris, copperRed, eased((ticks - 60.0) / 10.0));
        effectStrength = 1.0;
    } else if (ticks < 80.0) {
        effectTint = mix(copperRed, white, eased((ticks - 70.0) / 10.0));
        effectStrength = 1.0;
    } else if (ticks < 90.0) {
        effectStrength = 1.0 - eased((ticks - 80.0) / 10.0);
    }
    float strength = clamp(max(TintStrength, effectStrength), 0.0, 1.0);
    float luminance = dot(baseRgb, vec3(0.2126, 0.7152, 0.0722));
    float tintLuminance = max(dot(effectTint, vec3(0.2126, 0.7152, 0.0722)), 0.001);
    vec3 colorized = effectTint * (luminance / tintLuminance);
    vec4 color = vec4(mix(baseRgb, colorized, strength), atlasColor.a);
    fragColor = CooIrisComposite != 0
        ? color
        : linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);



}
