package cn.coostack.cooparticlesapi.renderer.shader.texture

import cn.coostack.cooparticlesapi.renderer.shader.data.TextureData
import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTexture
import cn.coostack.cooparticlesapi.renderer.shader.utils.GlslUtil
import org.lwjgl.opengl.GL33.*

class FileTexture(val path: String) : GlTexture {
    var textureID = 0
    var lastTextureID = 0
    /**
     * 执行 `FileTexture` 定义的 `textureID` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`textureID()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun textureID(): Int {
        return textureID
    }

    /**
     * 初始化或准备 `FileTexture` 的 `init` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`init()`。
     */
    override fun init() {
        textureID = glGenTextures()
        useOnCurrent()
        val data = getTexData()
        val width = data.width
        val height = data.height
        val channel = data.channels
        val format = if (channel == 4) GL_RGBA else GL_RGB
        val internalFormat = if (channel == 4) GL_RGBA8 else GL_RGB8
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexImage2D(
            GL_TEXTURE_2D, 0, internalFormat,
            width, height, 0, format, GL_UNSIGNED_BYTE, data.buffer
        )
        glGenerateMipmap(GL_TEXTURE_2D)
        data.release()
        reset()
    }

    /**
     * 释放 `FileTexture` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    override fun release() {
        if (textureID > 0) {
            glDeleteTextures(textureID)
            textureID = 0
        }
    }

    /**
     * 执行 `FileTexture` 定义的 `useOnCurrent` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`useOnCurrent()`。
     */
    override fun useOnCurrent() {
        lastTextureID = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, textureID)
    }

    /**
     * 清理 `FileTexture` 的 `reset` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`reset()`。
     */
    override fun reset() {
        glBindTexture(GL_TEXTURE_2D, lastTextureID)
    }

    private fun getTexData(): TextureData = GlslUtil.readTextureFromJar(path)
}
