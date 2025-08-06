package cn.coostack.cooparticlesapi.renderer.shader.uniform

import cn.coostack.cooparticlesapi.renderer.shader.CooShaderProgram
import org.lwjgl.opengl.GL33.*


class GlFloatUnformProvider(override val key: String, override var default: Float) : GlUniformProvider<Float> {
    override fun provide(shader: CooShaderProgram) {
        val program = shader.program
        val locate = glGetUniformLocation(program, key)
        if (locate == -1) return
        glUniform1f(locate, default)
    }
}