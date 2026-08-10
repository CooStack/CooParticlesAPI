package cn.coostack.cooparticlesapi.renderer.terrain

/**
 * 保存一个 OpenGL framebuffer attachment 的绑定类型和对象名。
 *
 * @property type `GL_NONE`、纹理或渲染缓冲对象类型
 * @property name 对应 OpenGL 对象名；无绑定时为 `0`
 */
internal data class FramebufferAttachment(
    val type: Int,
    val name: Int
)
