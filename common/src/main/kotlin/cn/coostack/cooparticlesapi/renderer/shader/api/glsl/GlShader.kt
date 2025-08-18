package cn.coostack.cooparticlesapi.renderer.shader.api.glsl

interface GlShader {
    val type: GlShaderType
    fun shaderID(): Int
    fun compile()
    fun assertCompiled()
    fun deleteShader()
}