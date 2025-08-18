package cn.coostack.cooparticlesapi.renderer.shader.api.texture

/**
 * 外部引用 并且绑定 texture
 * 使用 GlTextureManager
 */
interface GlTexture {
    fun textureID(): Int

    fun init()

    /**
     * 在当前已经激活的 texture通道中绑定 texture
     */
    fun useOnCurrent()

    fun reset()

}