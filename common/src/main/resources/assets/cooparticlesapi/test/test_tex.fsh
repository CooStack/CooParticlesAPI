#version 330 core

out vec4 FragColor;
in vec2 screen_uv;
uniform vec3 color;
uniform float t;
/**
 * 文档测试
 */
float test() {
    return 1.;
}
/**
 * 带有参数的文档测试
 */
float test(float a) {
    return a;
}

/**
 * 空返回值的调用补全测试
 */
void invoke() {

}

/**
 * 用于测试GLSL插件的内容
 * 注释测试 Pass
 */
void main() {
    vec4 a = vec4(255.0 / 255.0, 100.0 / 255., 1., 1.);
    FragColor = a * t;
}
