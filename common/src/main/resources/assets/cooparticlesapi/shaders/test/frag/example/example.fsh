#version 330 core

in vec2 uv;
out vec4 FragCoord;
uniform sampler2D tex;
uniform bool useSpriteUv;
uniform vec4 spriteUvRect;

vec2 resolveUv(vec2 baseUv) {
    if (!useSpriteUv) {
        return baseUv;
    }
    return spriteUvRect.xy + baseUv * spriteUvRect.zw;
}

void main(){
    vec4 color = texture(tex, resolveUv(uv));
    if (color.a <= 1.0e-4) {
        discard;
    }
    FragCoord = color;
}
