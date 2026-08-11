package cn.coostack.cooparticlesapi.renderer.shader.data

import org.lwjgl.stb.STBImage
import java.nio.ByteBuffer

data class TextureData(val buffer: ByteBuffer, val width: Int, val height: Int, val channels: Int) {

    /**
     * 释放 `TextureData` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    fun release() {
        STBImage.stbi_image_free(buffer)
    }

}