package cn.coostack.cooparticlesapi.renderer.shader.api.pipe

import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram

/**
 * 可复用的全局 uniform 抽象。
 *
 * 适合把某些在多个 shader 间共享的 uniform 值封装成对象。
 */
interface GlobalUniform<T> {
    /**
     * 当前 uniform 的键名。
     */
    var key: String
    /**
     * 当前 uniform 的值。
     */
    var value: T

    /**
     * 把当前值上传到指定 shader program。
     */
    fun upload(program: CooShaderProgram)
}
