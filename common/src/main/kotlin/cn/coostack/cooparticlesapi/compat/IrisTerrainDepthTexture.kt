package cn.coostack.cooparticlesapi.compat

/**
 * Iris 场景深度纹理及其当前渲染尺寸。
 *
 * @property textureId OpenGL 深度纹理对象名
 * @property width 深度纹理宽度，单位为像素
 * @property height 深度纹理高度，单位为像素
 */
internal data class IrisTerrainDepthTexture(
    val textureId: Int,
    val width: Int,
    val height: Int,
)
