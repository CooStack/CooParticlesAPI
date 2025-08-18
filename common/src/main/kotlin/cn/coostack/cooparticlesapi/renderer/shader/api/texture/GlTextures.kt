package cn.coostack.cooparticlesapi.renderer.shader.api.texture

/**
 * 自动管理材质通道
 */
interface GlTextures {

    fun addTexture(texture: GlTexture)

    fun init()

    fun use()

    fun reset()

    /**
     * 绑定该材质绘制
     * 自动解除使用
     */
    fun drawWith(renderContext: Runnable)

}