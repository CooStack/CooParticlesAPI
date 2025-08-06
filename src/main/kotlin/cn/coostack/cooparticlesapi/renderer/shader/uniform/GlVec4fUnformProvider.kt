package cn.coostack.cooparticlesapi.renderer.shader.uniform

import cn.coostack.cooparticlesapi.renderer.shader.CooShaderProgram
import org.joml.Vector4f
import org.lwjgl.opengl.GL33.*

class GlVec4fUnformProvider(override val key: String, override var default: Vector4f) : GlUniformProvider<Vector4f> {
    override fun provide(shader: CooShaderProgram) {
        val program = shader.program
        val locate = glGetUniformLocation(program, key)
        if (locate == -1) return
        glUniform4f(locate, default.x, default.y, default.z, default.w)
    }

}