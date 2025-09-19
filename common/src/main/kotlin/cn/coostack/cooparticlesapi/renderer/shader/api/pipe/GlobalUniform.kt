package cn.coostack.cooparticlesapi.renderer.shader.api.pipe

import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram

interface GlobalUniform<T> {
    var key: String
    var value: T
    fun upload(program: CooShaderProgram)
}