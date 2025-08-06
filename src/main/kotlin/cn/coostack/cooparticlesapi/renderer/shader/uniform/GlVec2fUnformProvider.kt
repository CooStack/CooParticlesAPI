package cn.coostack.cooparticlesapi.renderer.shader.uniform

import cn.coostack.cooparticlesapi.renderer.shader.CooShaderProgram
import org.joml.Vector2f
import org.lwjgl.opengl.GL33.*

class GlVec2fUnformProvider(override val key: String, override var default: Vector2f) : GlUniformProvider<Vector2f> {
    override fun provide(shader: CooShaderProgram) {
        val program = shader.program
        val locate = glGetUniformLocation(program, key)
        if (locate == -1) return
        glUniform2f(locate, default.x,default.y)
    }
}