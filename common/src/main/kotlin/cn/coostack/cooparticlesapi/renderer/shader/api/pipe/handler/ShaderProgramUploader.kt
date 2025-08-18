package cn.coostack.cooparticlesapi.renderer.shader.api.pipe.handler

import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram

/**
 * 在这里调用 对uniform的set操作
 */
@FunctionalInterface
fun interface ShaderProgramUploader {
    /**
     * 在这里输入uniform
     * @param current 当前正在使用的屏幕着色器
     */
    fun uploadShaderData(current: CooShaderProgram)
}