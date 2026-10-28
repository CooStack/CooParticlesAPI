package cn.coostack.cooparticlesapi.renderer.shader.texture

import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTexture
import cn.coostack.cooparticlesapi.renderer.backend.glTextureId
import cn.coostack.cooparticlesapi.renderer.backend.glSamplerId
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL33.glBindTexture
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL33.GL_TEXTURE0
import org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING
import org.lwjgl.opengl.GL33.glBindSampler
import org.lwjgl.opengl.GL33.glGetIntegeri

class IdentifierTexture(val id: Identifier) : GlTexture {
    var textureID = 0
    var lastTextureID = 0
    /** 原版纹理拥有的采样器对象名，本包装只借用而不释放。 */
    var samplerID = 0
        private set
    /** 进入绑定作用域时活动纹理单元原先的采样器。 */
    private var lastSamplerID = 0
    /** 当前绑定作用域使用的纹理单元。 */
    private var activeUnit = 0

    private val textureIdentifier: Identifier =
        Identifier.fromNamespaceAndPath(id.namespace, "textures/${id.path}")
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
        val texture = textureManager.getTexture(textureIdentifier)
        textureID = texture.texture.glTextureId
        samplerID = texture.sampler.glSamplerId
    }

    /**
     * 释放 `IdentifierTexture` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    override fun release() {
        textureID = 0
        samplerID = 0
    }

    /**
     * 执行 `IdentifierTexture` 定义的 `useOnCurrent` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`useOnCurrent()`。
     */
    override fun useOnCurrent() {
        lastTextureID = glGetInteger(GL_TEXTURE_BINDING_2D)
        activeUnit = glGetInteger(GL_ACTIVE_TEXTURE) - GL_TEXTURE0
        lastSamplerID = glGetIntegeri(GL_SAMPLER_BINDING, activeUnit)
        glBindTexture(GL_TEXTURE_2D, textureID)
        glBindSampler(activeUnit, samplerID)
    }

    /**
     * 清理 `IdentifierTexture` 的 `reset` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`reset()`。
     */
    override fun reset() {
        glBindTexture(GL_TEXTURE_2D, lastTextureID)
        glBindSampler(activeUnit, lastSamplerID)
    }
}
