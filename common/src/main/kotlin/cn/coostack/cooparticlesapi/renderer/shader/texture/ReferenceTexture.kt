package cn.coostack.cooparticlesapi.renderer.shader.texture

import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTexture
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glGetInteger

class ReferenceTexture(val refID: Int) : GlTexture {
    var lastTextureID = 0
    override fun textureID(): Int {
        return refID
    }

    override fun init() {
    }

    override fun useOnCurrent() {
        lastTextureID = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, refID)
    }

    override fun reset() {
        glBindTexture(GL_TEXTURE_2D, lastTextureID)
    }
}