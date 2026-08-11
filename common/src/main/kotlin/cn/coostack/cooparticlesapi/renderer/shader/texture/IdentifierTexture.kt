package cn.coostack.cooparticlesapi.renderer.shader.texture

import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTexture
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL33.glBindTexture
import org.lwjgl.opengl.GL33.glGetInteger

class IdentifierTexture(val id: ResourceLocation) : GlTexture {
    var textureID = 0
    var lastTextureID = 0

    private val textureResourceLocation: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(id.namespace, "textures/${id.path}")
    /**
     * 执行 `IdentifierTexture` 定义的 `textureID` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`textureID()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun textureID(): Int {
        return textureID
    }

    /**
     * 初始化或准备 `IdentifierTexture` 的 `init` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`init()`。
     */
    override fun init() {
        val textureManager = Minecraft.getInstance().textureManager
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        textureManager.bindForSetup(textureResourceLocation)
        textureID = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, previousTexture)
    }

    /**
     * 释放 `IdentifierTexture` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    override fun release() {
        textureID = 0
    }

    /**
     * 执行 `IdentifierTexture` 定义的 `useOnCurrent` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`useOnCurrent()`。
     */
    override fun useOnCurrent() {
        lastTextureID = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, textureID)
    }

    /**
     * 清理 `IdentifierTexture` 的 `reset` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`reset()`。
     */
    override fun reset() {
        glBindTexture(GL_TEXTURE_2D, lastTextureID)
    }
}
