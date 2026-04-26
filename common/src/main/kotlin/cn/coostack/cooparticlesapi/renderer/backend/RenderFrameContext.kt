package cn.coostack.cooparticlesapi.renderer.backend

import com.mojang.blaze3d.pipeline.RenderTarget
import org.joml.Matrix4f

/**
 * 当前帧、当前阶段共享的统一上下文对象。
 *
 * RenderEntity renderer、builtin effect 和 backend hook 都通过它读取这一帧的基础渲染信息。
 */
data class RenderFrameContext(
    /** 当前帧的部分 tick 插值值。 */
    val tickDelta: Float,
    /** 当前相机的 view 矩阵。 */
    val viewMatrix: Matrix4f,
    /** 当前相机的 projection 矩阵。 */
    val projMatrix: Matrix4f,
    /** 当前实际执行本帧的 backend。 */
    val backend: RenderBackend,
    /** 当前上下文所代表的统一渲染阶段。 */
    val stage: RenderFrameStage = RenderFrameStage.FRAME_BEGIN,
    /** backend 已经解析出的命名场景资源集合。 */
    val sceneResources: RenderSceneResources = RenderSceneResources.empty(),
    /** 当前可直接使用的场景颜色纹理 id；为空表示本阶段未解析到。 */
    val sceneColorTextureId: Int? = null,
    /** 当前场景颜色来源 framebuffer；用于 Iris 等没有 RenderTarget 包装的外部 FBO。 */
    val sceneColorFramebufferId: Int? = null,
    /** 当前可直接使用的场景深度纹理 id；为空表示本阶段未解析到。 */
    val sceneDepthTextureId: Int? = null,
    /** 当前场景深度来源 framebuffer；为空表示只能按纹理或降级路径处理。 */
    val sceneDepthFramebufferId: Int? = null,
    /** 当前帧最终合成输出目标；为空时通常表示仍然回写主目标。 */
    val finalCompositeTarget: RenderTarget? = null,
    /** 当前帧最终合成输出 framebuffer；可指向 Iris 当前绑定的外部 FBO。 */
    val finalCompositeFramebufferId: Int? = null,
    /** 当前帧是否使用了非 vanilla RenderTarget 管理的外部 framebuffer。 */
    val externalFramebuffer: Boolean = false,
    /** 当前解析出的主要目标标签，便于调试或 shader 侧日志追踪。 */
    val resolvedTargetLabel: String = "main",
    /** 当前绑定的 framebuffer id；主要用于底层 OpenGL 交互场景。 */
    val boundFramebufferId: Int? = null,
    /** 当前目标宽度。 */
    val targetWidth: Int? = null,
    /** 当前目标高度。 */
    val targetHeight: Int? = null
)
