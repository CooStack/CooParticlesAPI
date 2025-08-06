package cn.coostack.cooparticlesapi.renderer.shader.uniform

import cn.coostack.cooparticlesapi.renderer.shader.CooShaderProgram
import org.joml.Matrix3x2f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33.*

class GlMatrix3x2fvUnformProvider(override val key: String, override var default: Matrix3x2f) :
    GlUniformProvider<Matrix3x2f> {
    var transpose = false
    override fun provide(shader: CooShaderProgram) {
        val program = shader.program
        val locate = glGetUniformLocation(program, key)

        if (locate == -1) return
        glUniformMatrix3x2fv(locate, transpose, default.get(BufferUtils.createFloatBuffer(16)))
    }

}