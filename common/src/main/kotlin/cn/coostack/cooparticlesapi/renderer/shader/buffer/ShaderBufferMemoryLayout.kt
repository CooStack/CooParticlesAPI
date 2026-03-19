package cn.coostack.cooparticlesapi.renderer.shader.buffer

/**
 * shader buffer 使用的内存布局枚举。
 */
enum class ShaderBufferMemoryLayout(val glslKeyword: String) {
    /**
     * `std140` 布局。
     *
     * 对齐更严格，兼容性更高，常见于 UBO。
     */
    STD140("std140"),

    /**
     * `std430` 布局。
     *
     * 对齐更紧凑，常见于 SSBO 与 compute 数据结构。
     */
    STD430("std430")
}
