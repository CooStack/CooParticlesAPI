package cn.coostack.cooparticlesapi.renderer.terrain

/**
 * Iris 地形深度 attachment 的恢复记录。
 *
 * @property framebuffer 需要恢复的 framebuffer 对象名
 * @property depthAttachment 进入覆盖绘制前的深度 attachment
 */
internal data class IrisDepthAttachmentRestore(
    val framebuffer: Int,
    val depthAttachment: FramebufferAttachment
)
