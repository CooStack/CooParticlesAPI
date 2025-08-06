package cn.coostack.cooparticlesapi.renderer.shader.uniform

import cn.coostack.cooparticlesapi.renderer.shader.CooShaderProgram
import org.joml.Vector3f
import org.lwjgl.opengl.GL33.*

class GlVec3fUnformProvider(override val key: String, override var default: Vector3f) : GlUniformProvider<Vector3f> {
    override fun provide(shader: CooShaderProgram) {
        val program = shader.program
        val locate = glGetUniformLocation(program, key)
        if (locate == -1) return
        glUniform3f(locate, default.x, default.y, default.z)
    }

}