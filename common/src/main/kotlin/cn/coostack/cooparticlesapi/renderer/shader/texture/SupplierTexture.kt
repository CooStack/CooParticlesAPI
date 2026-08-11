package cn.coostack.cooparticlesapi.renderer.shader.texture

import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTexture
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glGetInteger
import java.util.function.Supplier

class SupplierTexture(private val supplier: Supplier<Int>) : GlTexture {
    private var lastTextureID = 0

    /**
     * 执行 `SupplierTexture` 定义的 `textureID` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`textureID()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun textureID(): Int {
        return supplier.get()
    }

    /**
     * 初始化或准备 `SupplierTexture` 的 `init` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`init()`。
     */
    override fun init() {
    }

    /**
     * 执行 `SupplierTexture` 定义的 `useOnCurrent` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`useOnCurrent()`。
     */
    override fun useOnCurrent() {
        lastTextureID = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, supplier.get())
    }

    /**
     * 清理 `SupplierTexture` 的 `reset` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`reset()`。
     */
    override fun reset() {
        glBindTexture(GL_TEXTURE_2D, lastTextureID)
    }
}
