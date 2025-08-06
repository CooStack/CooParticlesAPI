package cn.coostack.cooparticlesapi.renderer.shader.uniform

import cn.coostack.cooparticlesapi.renderer.shader.CooShaderProgram
import org.joml.Matrix2f
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33.*

class GlMatrix3fvUnformProvider(override val key: String, override var default: Matrix3f) :
    GlUniformProvider<Matrix3f> {
    var transpose = false
    override fun provide(shader: CooShaderProgram) {
        val program = shader.program
        val locate = glGetUniformLocation(program, key)
        if (locate == -1) return
        glUniformMatrix3fv(locate, transpose, default.get(BufferUtils.createFloatBuffer(16)))
    }

}