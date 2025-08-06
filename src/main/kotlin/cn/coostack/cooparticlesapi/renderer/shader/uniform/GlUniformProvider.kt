package cn.coostack.cooparticlesapi.renderer.shader.uniform

import cn.coostack.cooparticlesapi.renderer.shader.CooShaderProgram

interface GlUniformProvider<T> {
    val key: String
    var default: T
    fun provide(shader: CooShaderProgram)
    fun setValue(t: T) {
        this.default = t
    }
}