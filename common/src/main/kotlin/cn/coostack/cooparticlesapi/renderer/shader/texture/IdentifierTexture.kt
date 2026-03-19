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
    override fun textureID(): Int {
        return textureID
    }

    override fun init() {
        val textureManager = Minecraft.getInstance().textureManager
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        textureManager.bindForSetup(textureResourceLocation)
        textureID = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, previousTexture)
    }

    override fun release() {
        textureID = 0
    }

    override fun useOnCurrent() {
        lastTextureID = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, textureID)
    }

    override fun reset() {
        glBindTexture(GL_TEXTURE_2D, lastTextureID)
    }
}
