package cn.coostack.cooparticlesapi.compat

/**
 * Iris final pass 输出颜色纹理及其渲染尺寸。
 *
 * @property textureId OpenGL 颜色纹理对象名
 * @property width 颜色纹理宽度，单位为像素
 * @property height 颜色纹理高度，单位为像素
 */
internal data class IrisFinalPassColorTexture(
    val textureId: Int,
    val width: Int,
    val height: Int,
)
