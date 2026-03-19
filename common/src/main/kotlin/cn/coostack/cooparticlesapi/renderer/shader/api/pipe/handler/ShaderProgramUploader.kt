package cn.coostack.cooparticlesapi.renderer.shader.api.pipe.handler

import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram

/**
 * shader program 数据上传器。
 *
 * 常用于在真正绘制前，把 uniform、矩阵或运行时状态写入 program。
 */
@FunctionalInterface
fun interface ShaderProgramUploader {
    /**
     * 向当前 program 上传绘制所需的数据。
     */
    fun uploadShaderData(current: CooShaderProgram)
}
