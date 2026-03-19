package cn.coostack.cooparticlesapi.renderer.shader.buffer

/**
 * Shader buffer 的绑定类型枚举。
 */
enum class ShaderBufferBinding {
    /**
     * Uniform Buffer Object。
     *
     * 适合只读、小体量、按 binding slot 共享给 shader 的常量数据。
     */
    UNIFORM_BUFFER,

    /**
     * Shader Storage Buffer Object。
     *
     * 适合更大体量、可读写或 compute 场景下需要随机访问的数据。
     */
    SHADER_STORAGE_BUFFER
}
