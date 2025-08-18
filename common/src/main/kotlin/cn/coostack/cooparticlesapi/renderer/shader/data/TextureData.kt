package cn.coostack.cooparticlesapi.renderer.shader.data

import org.lwjgl.stb.STBImage
import java.nio.ByteBuffer

data class TextureData(val buffer: ByteBuffer, val width: Int, val height: Int, val channels: Int) {

    fun release() {
        STBImage.stbi_image_free(buffer)
    }

}