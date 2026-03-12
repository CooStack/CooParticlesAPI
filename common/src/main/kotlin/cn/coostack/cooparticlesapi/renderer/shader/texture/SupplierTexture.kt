package cn.coostack.cooparticlesapi.renderer.shader.texture

import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTexture
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glGetInteger
import java.util.function.Supplier

class SupplierTexture(private val supplier: Supplier<Int>) : GlTexture {
    private var lastTextureID = 0

    override fun textureID(): Int {
        return supplier.get()
    }

    override fun init() {
    }

    override fun useOnCurrent() {
        lastTextureID = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, supplier.get())
    }

    override fun reset() {
        glBindTexture(GL_TEXTURE_2D, lastTextureID)
    }
}
