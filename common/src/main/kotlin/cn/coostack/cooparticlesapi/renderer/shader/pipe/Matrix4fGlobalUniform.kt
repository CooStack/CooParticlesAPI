package cn.coostack.cooparticlesapi.renderer.shader.pipe

import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.GlobalUniform
import org.joml.Matrix4f

class Matrix4fGlobalUniform(override var key: String) : GlobalUniform<Matrix4f> {
    override var value: Matrix4f = Matrix4f()

    override fun upload(program: CooShaderProgram) {
        program.setMatrix4(key, value)
    }
}