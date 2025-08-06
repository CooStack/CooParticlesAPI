package cn.coostack.cooparticlesapi.renderer.shader.uniform

import cn.coostack.cooparticlesapi.renderer.shader.CooShaderProgram
import org.joml.Matrix2f
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33.*

class GlMatrix2fvUnformProvider(override val key: String, override var default: Matrix2f) :
    GlUniformProvider<Matrix2f> {
    var transpose = false
    override fun provide(shader: CooShaderProgram) {
        val program = shader.program
        val locate = glGetUniformLocation(program, key)
        if (locate == -1) return
        glUniformMatrix2fv(locate, transpose, default.get(BufferUtils.createFloatBuffer(16)))
    }

}