#version 330 core

in vec2 screen_uv;
out vec4 FragColor;

uniform sampler2D scene;
uniform sampler2D bloomBlur;
uniform float intensity = 1.0;

void main()
{
    const float gamma = 2.2;
    vec3 hdrColor = texture(scene, screen_uv).rgb;
    vec3 bloomColor = texture(bloomBlur, screen_uv).rgb;
    hdrColor += bloomColor;// additive blending
    // tone mapping
    vec3 result = vec3(1.0) - exp(-hdrColor * intensity);
    // also gamma correct while we're at it
    result = pow(result, vec3(1.0 / gamma));
    //    FragColor = vec4(hdrColor.rgb + result.rgb, (hdrColor.a + result.a) / 2);
    FragColor = vec4(result, 1.);
}