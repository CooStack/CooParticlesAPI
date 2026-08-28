package cn.coostack.cooparticlesapi.compat

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Iris final pass 颜色输入查询使用的反射成员集合。
 *
 * @property getPipelineManager 读取 Iris pipeline manager 的静态方法
 * @property getPipeline 读取当前 world pipeline 的方法
 * @property renderTargetsField 当前 pipeline 持有的 render targets 字段
 * @property finalPassRendererField 当前 pipeline 持有的 final pass renderer 字段
 * @property finalPassField final pass renderer 持有的 final shader pass 字段
 * @property programField final shader pass 持有的 OpenGL program 字段
 * @property getProgramId 读取 final shader OpenGL program 对象名的方法
 * @property stageReadsFromAltField final shader pass 记录的 ping-pong 读取集合字段
 * @property baselineField final pass renderer 持有的基准 framebuffer 字段
 * @property getColorAttachment 读取 framebuffer 指定颜色 attachment 的方法
 * @property getRenderTarget 读取指定 Iris render target 的方法
 * @property getMainTexture 读取 render target 主纹理的方法
 * @property getAltTexture 读取 render target 备用纹理的方法
 * @property getCurrentWidth 读取 Iris render targets 当前宽度的方法
 * @property getCurrentHeight 读取 Iris render targets 当前高度的方法
 */
internal data class IrisFinalPassMethods(
    val getPipelineManager: Method,
    val getPipeline: Method,
    val renderTargetsField: Field,
    val finalPassRendererField: Field,
    val finalPassField: Field,
    val programField: Field,
    val getProgramId: Method,
    val stageReadsFromAltField: Field,
    val baselineField: Field,
    val getColorAttachment: Method,
    val getRenderTarget: Method,
    val getMainTexture: Method,
    val getAltTexture: Method,
    val getCurrentWidth: Method,
    val getCurrentHeight: Method,
)

/** Iris composite 链输入颜色查询使用的反射成员集合。 */
internal data class IrisCompositeMethods(
    val getPipelineManager: Method,
    val getPipeline: Method,
    val renderTargetsField: Field,
    val compositeRendererField: Field,
    val compositePassesField: Field,
    val compositeProgramField: Field,
    val compositeStageReadsFromAltField: Field,
    val getProgramId: Method,
    val getRenderTarget: Method,
    val getMainTexture: Method,
    val getAltTexture: Method,
    val getCurrentWidth: Method,
    val getCurrentHeight: Method,
)
