package cn.coostack.cooparticlesapi.renderer.shader.api.texture

/**
 * 多纹理集合管理抽象。
 *
 * 用于统一管理一组会被同时绑定到不同 texture slot 的纹理。
 */
interface GlTextures {

    /**
     * 添加一张纹理到当前集合。
     */
    fun addTexture(texture: GlTexture)

    /**
     * 返回当前集合里的纹理数量。
     */
    fun getTextureCounts(): Int

    /**
     * 初始化集合中的纹理资源。
     */
    fun init()

    /**
     * 释放集合中的纹理资源。
     */
    fun release() {
    }

    /**
     * 绑定集合中的全部纹理。
     */
    fun use()

    /**
     * 恢复纹理集合绑定前的状态。
     */
    fun reset()

    /**
     * 在纹理集合已绑定的上下文中执行绘制逻辑。
     */
    fun drawWith(renderContext: Runnable)
}
