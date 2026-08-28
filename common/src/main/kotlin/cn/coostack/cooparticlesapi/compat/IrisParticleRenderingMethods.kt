package cn.coostack.cooparticlesapi.compat

import java.lang.reflect.Method

/**
 * Iris 粒子渲染模式查询使用的反射方法集合。
 *
 * @property getPipelineManager 读取 Iris pipeline manager 的静态方法
 * @property getPipeline 读取当前 world pipeline 的方法
 * @property getParticleRenderingSettings 读取当前粒子分流设置的方法
 */
internal data class IrisParticleRenderingMethods(
    val getPipelineManager: Method,
    val getPipeline: Method,
    val getParticleRenderingSettings: Method,
)
