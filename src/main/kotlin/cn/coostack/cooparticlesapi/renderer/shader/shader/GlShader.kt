package cn.coostack.cooparticlesapi.renderer.shader.shader

interface GlShader {
    val type: ShaderType
    fun shaderCode(): String

    /**
     * 只有初始化之后才能获取到shaderID
     */
    fun shaderID(): Int

    /**
     * 先初始化
     */
    fun init()

    fun assertShaderCompiled()
}