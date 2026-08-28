package cn.coostack.cooparticlesapi.compat

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Iris terrain 深度查询使用的反射成员集合。
 *
 * @property getPipelineManager 读取 Iris pipeline manager 的静态方法
 * @property getPipeline 读取当前 world pipeline 的方法
 * @property renderTargetsField 当前 pipeline 持有的 render targets 字段
 * @property getDepthTexture 读取最终场景深度纹理对象名的方法
 * @property getDepthTextureNoTranslucents 读取半透明 terrain 前深度包装对象的方法
 * @property getDepthTextureNoHand 读取 hand 绘制前深度包装对象的方法；旧版本可能缺失
 * @property getDepthTextureId 从 Iris 深度包装对象读取纹理对象名的方法
 * @property getCurrentWidth 读取 Iris render targets 当前宽度的方法
 * @property getCurrentHeight 读取 Iris render targets 当前高度的方法
 */
internal data class IrisTerrainDepthMethods(
    val getPipelineManager: Method,
    val getPipeline: Method,
    val renderTargetsField: Field,
    val getDepthTexture: Method,
    val getDepthTextureNoTranslucents: Method,
    val getDepthTextureNoHand: Method?,
    val getDepthTextureId: Method,
    val getCurrentWidth: Method,
    val getCurrentHeight: Method,
)
