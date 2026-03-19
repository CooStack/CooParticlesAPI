package cn.coostack.cooparticlesapi.renderer.shader.api.texture

/**
 * OpenGL 纹理对象抽象。
 */
interface GlTexture {
    /**
     * 返回底层纹理 id。
     */
    fun textureID(): Int

    /**
     * 初始化纹理对象。
     */
    fun init()

    /**
     * 释放纹理资源。
     */
    fun release() {
    }

    /**
     * 在当前已激活的 texture slot 上绑定该纹理。
     */
    fun useOnCurrent()

    /**
     * 恢复纹理绑定前的状态。
     */
    fun reset()
}
