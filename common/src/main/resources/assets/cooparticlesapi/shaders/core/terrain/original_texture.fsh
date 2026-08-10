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
uniform vec3 EffectTint;
uniform float EffectStrength;

in float vertexDistance;
in vec4 vertexColor;
in vec2 baseUv;
in vec2 effectUv;
in vec3 worldPosition;
in vec3 worldNormal;

out vec4 fragColor;

void main() {
    vec4 atlasColor = texture(BaseSampler, baseUv) * vertexColor * ColorModulator;
    if (atlasColor.a < CooAlphaCutoff) {
        discard;
    }
    vec3 baseRgb = atlasColor.rgb;
    if (CooIrisComposite != 0) {
        baseRgb = texture(SceneColor, gl_FragCoord.xy / ScreenSize).rgb;
    }
    float strength = clamp(max(TintStrength, EffectStrength), 0.0, 1.0);
    float luminance = dot(baseRgb, vec3(0.2126, 0.7152, 0.0722));
    float tintLuminance = max(dot(EffectTint, vec3(0.2126, 0.7152, 0.0722)), 0.001);
    vec3 colorized = EffectTint * (luminance / tintLuminance);
    vec4 color = vec4(mix(baseRgb, colorized, strength), atlasColor.a);
    fragColor = CooIrisComposite != 0
        ? color
        : linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
