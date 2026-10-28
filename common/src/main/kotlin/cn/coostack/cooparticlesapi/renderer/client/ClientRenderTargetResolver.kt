package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.accessor.LevelRendererAccessor
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.OpenGlRenderTargetBridge
import cn.coostack.cooparticlesapi.renderer.backend.OpenGlRenderTargetBridge.bindFramebuffer as glBindFramebuffer
import cn.coostack.cooparticlesapi.renderer.backend.colorTextureId
import cn.coostack.cooparticlesapi.renderer.backend.depthTextureId
import cn.coostack.cooparticlesapi.renderer.backend.frameBufferId
import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL33.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_NONE
import org.lwjgl.opengl.GL33.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_READ_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.glGetFramebufferAttachmentParameteri
import org.lwjgl.opengl.GL33.glGetInteger

/**
 * 当前命令阶段的场景颜色、深度与最终合成目标。
 *
 * 显式 FBO 编号优先于目标桥接编号，避免 Iris 外部 FBO 被主目标代替。
 */
data class ResolvedRenderTargets(
    val sceneColorTarget: RenderTarget,
    val sceneDepthTarget: RenderTarget,
    val finalCompositeTarget: RenderTarget,
    val boundFramebufferId: Int,
    val targetLabel: String,
    val sceneColorFramebufferId: Int = sceneColorTarget.frameBufferId,
    val sceneDepthFramebufferId: Int = sceneDepthTarget.frameBufferId,
    val finalCompositeFramebufferId: Int = finalCompositeTarget.frameBufferId,
    val externalFramebuffer: Boolean = false
) {
    val sceneColorTextureId: Int?
        get() = if (externalFramebuffer) null else sceneColorTarget.colorTextureId.takeIf { it > 0 }
    val sceneDepthTextureId: Int?
        get() = sceneDepthTarget.depthTextureId.takeIf { it > 0 }
    val width: Int
        get() = finalCompositeTarget.width
    val height: Int
        get() = finalCompositeTarget.height
}

/**
 * 根据实际 GL attachment 识别当前场景与最终绘制目标，保留 Iris 外部 FBO 的所有权。
 *
 * 客户端 OpenGL 绘制入口调用 [resolveCurrentTargets]，不可跨帧缓存结果。
 */
object ClientRenderTargetResolver {
    /**
     * 参与目标选择的诊断标签与原版目标。
     * @property label 用于日志的来源名称
     * @property target 不接管所有权的原版目标
     */
    private data class NamedTarget(
        val label: String,
        val target: RenderTarget
    )

    private var lastFallbackSignature: String? = null
    private var lastExternalFramebufferSignature: String? = null

    /**
     * 根据输入和 `ClientRenderTargetResolver` 当前状态解析 `resolveCurrentTargets` 结果，供后续构建或绘制使用。
     *
     * 示例：`resolveCurrentTargets()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun resolveCurrentTargets(): ResolvedRenderTargets {
        OpenGlRenderTargetBridge.requireOpenGlBackend()
        val minecraft = Minecraft.getInstance()
        val mainTarget = minecraft.gameRenderer.mainRenderTarget()
        val accessor = minecraft.levelRenderer as? LevelRendererAccessor
        val boundFramebufferId = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val candidates = LinkedHashMap<Int, NamedTarget>()

        /**
         * 把输入对象加入 `NamedTarget` 的 `addCandidate` 管理范围，后续查询、构建或绘制会使用该绑定。
         *
         * 示例：`addCandidate(label = label, target = target)`。
         *
         * @param label 用于查找、绑定或记录目标的名称
         *
         * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         */
        fun addCandidate(label: String, target: RenderTarget?) {
            if (target == null || target.frameBufferId == 0) {
                return
            }
            candidates.putIfAbsent(target.frameBufferId, NamedTarget(label, target))
        }

        addCandidate("main", mainTarget)
        val passTargets = accessor?.availableFrameTargets().orEmpty()
        passTargets["main"]?.let { addCandidate("frame-main", it) }

        val matchedTarget = candidates[boundFramebufferId]
            ?: candidates.values.firstOrNull { OpenGlRenderTargetBridge.matches(it.target, boundFramebufferId) }
        val boundToOitTarget = passTargets.any { (purpose, target) ->
            purpose != "main" && OpenGlRenderTargetBridge.matches(target, boundFramebufferId)
        }
        val usesExternalFramebuffer = matchedTarget == null && boundFramebufferId > 0 && !boundToOitTarget
        val finalTarget = if (usesExternalFramebuffer) {
            logExternalFramebuffer(boundFramebufferId, candidates.values.toList())
            NamedTarget("external-bound", mainTarget)
        } else {
            matchedTarget ?: chooseFallback(mainTarget, boundFramebufferId, candidates.values.toList())
        }
        val sceneSourceTarget = if (usesExternalFramebuffer) {
            NamedTarget("external-bound-scene", mainTarget)
        } else {
            finalTarget
        }
        val sceneDepthTarget = if (sceneSourceTarget.target.depthTextureId > 0) {
            sceneSourceTarget.target
        } else if (finalTarget.target.depthTextureId > 0) {
            finalTarget.target
        } else {
            mainTarget
        }
        val externalDepthFramebufferId = selectExternalDepthFramebufferId(boundFramebufferId, sceneDepthTarget)

        return ResolvedRenderTargets(
            sceneColorTarget = sceneSourceTarget.target,
            sceneDepthTarget = sceneDepthTarget,
            finalCompositeTarget = finalTarget.target,
            boundFramebufferId = boundFramebufferId,
            targetLabel = finalTarget.label,
            sceneColorFramebufferId = when {
                usesExternalFramebuffer || sceneSourceTarget.target === matchedTarget?.target -> boundFramebufferId
                else -> sceneSourceTarget.target.frameBufferId
            },
            sceneDepthFramebufferId = when {
                usesExternalFramebuffer -> externalDepthFramebufferId
                sceneDepthTarget === matchedTarget?.target -> boundFramebufferId
                else -> sceneDepthTarget.frameBufferId
            },
            finalCompositeFramebufferId = if (usesExternalFramebuffer || matchedTarget != null) boundFramebufferId else finalTarget.target.frameBufferId,
            externalFramebuffer = usesExternalFramebuffer
        )
    }

    private fun selectExternalDepthFramebufferId(boundFramebufferId: Int, sceneDepthTarget: RenderTarget): Int {
        if (hasDepthAttachment(boundFramebufferId)) {
            return boundFramebufferId
        }
        return if (sceneDepthTarget.depthTextureId > 0) {
            sceneDepthTarget.frameBufferId
        } else {
            0
        }
    }

    private fun hasDepthAttachment(framebufferId: Int): Boolean {
        if (framebufferId <= 0) {
            return false
        }
        val previousFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        return try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebufferId)
            glGetFramebufferAttachmentParameteri(
                GL_READ_FRAMEBUFFER,
                GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            ) != GL_NONE
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousFramebuffer)
        }
    }

    private fun chooseFallback(
        mainTarget: RenderTarget,
        boundFramebufferId: Int,
        candidates: List<NamedTarget>
    ): NamedTarget {
        if (boundFramebufferId == 0) {
            logFallback(boundFramebufferId, candidates, "default-framebuffer", "main-fallback")
            return NamedTarget("main-fallback", mainTarget)
        }
        logFallback(boundFramebufferId, candidates, "main-default", "main-fallback")
        return NamedTarget("main-fallback", mainTarget)
    }

    private fun logFallback(
        boundFramebufferId: Int,
        candidates: List<NamedTarget>,
        reason: String,
        selectedLabel: String
    ) {
        val candidateSummary = candidates.joinToString { "${it.label}:${it.target.frameBufferId}" }
        val signature = "$boundFramebufferId|$reason|$selectedLabel|$candidateSummary"
        if (signature == lastFallbackSignature) {
            return
        }
        lastFallbackSignature = signature
        if (reason == "main-default" || reason == "default-framebuffer") {
            CooParticlesConstants.logger.debug(
                "Falling back post target selection boundFbo={} reason={} selected={} candidates={}",
                boundFramebufferId,
                reason,
                selectedLabel,
                candidateSummary
            )
        } else {
            CooParticlesConstants.logger.warn(
                "Falling back post target selection boundFbo={} reason={} selected={} candidates={}",
                boundFramebufferId,
                reason,
                selectedLabel,
                candidateSummary
            )
        }
    }

    private fun logExternalFramebuffer(boundFramebufferId: Int, candidates: List<NamedTarget>) {
        val candidateSummary = candidates.joinToString { "${it.label}:${it.target.frameBufferId}" }
        val signature = "$boundFramebufferId|$candidateSummary"
        if (signature == lastExternalFramebufferSignature) {
            return
        }
        lastExternalFramebufferSignature = signature
        CooParticlesConstants.logger.info(
            "Using externally bound post framebuffer boundFbo={} candidates={}",
            boundFramebufferId,
            candidateSummary
        )
    }

}
