package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.accessor.LevelRendererAccessor
import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL33.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
import org.lwjgl.opengl.GL33.GL_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_NONE
import org.lwjgl.opengl.GL33.glBindFramebuffer
import org.lwjgl.opengl.GL33.glGetFramebufferAttachmentParameteri
import org.lwjgl.opengl.GL33.glGetInteger
import java.util.LinkedHashMap

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

object ClientRenderTargetResolver {
    private data class NamedTarget(
        val label: String,
        val target: RenderTarget
    )

    private var lastFallbackSignature: String? = null
    private var lastSceneSourceSignature: String? = null
    private var lastExternalFramebufferSignature: String? = null

    fun resolveCurrentTargets(): ResolvedRenderTargets {
        val minecraft = Minecraft.getInstance()
        val mainTarget = minecraft.mainRenderTarget
        val accessor = minecraft.levelRenderer as? LevelRendererAccessor
        val boundFramebufferId = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val candidates = LinkedHashMap<Int, NamedTarget>()

        fun addCandidate(label: String, target: RenderTarget?) {
            if (target == null || target.frameBufferId == 0) {
                return
            }
            candidates.putIfAbsent(target.frameBufferId, NamedTarget(label, target))
        }

        addCandidate("main", mainTarget)
        accessor?.let {
            addCandidate("translucent", it.translucentTarget())
            addCandidate("itemEntity", it.itemEntityTarget())
            addCandidate("particles", it.particlesTarget())
            addCandidate("weather", it.weatherTarget())
            addCandidate("clouds", it.cloudsTarget())
        }

        val matchedTarget = candidates[boundFramebufferId]
        val usesExternalFramebuffer = matchedTarget == null && boundFramebufferId > 0
        val finalTarget = if (usesExternalFramebuffer) {
            logExternalFramebuffer(boundFramebufferId, candidates.values.toList())
            NamedTarget("external-bound", mainTarget)
        } else {
            matchedTarget ?: chooseFallback(mainTarget, accessor, boundFramebufferId, candidates.values.toList())
        }
        val sceneSourceTarget = if (usesExternalFramebuffer) {
            NamedTarget("external-bound-scene", mainTarget)
        } else {
            chooseSceneSource(mainTarget, accessor, finalTarget)
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
            sceneColorFramebufferId = if (usesExternalFramebuffer) boundFramebufferId else sceneSourceTarget.target.frameBufferId,
            sceneDepthFramebufferId = if (usesExternalFramebuffer) externalDepthFramebufferId else sceneDepthTarget.frameBufferId,
            finalCompositeFramebufferId = if (usesExternalFramebuffer) boundFramebufferId else finalTarget.target.frameBufferId,
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
        val previousFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        return try {
            glBindFramebuffer(GL_FRAMEBUFFER, framebufferId)
            glGetFramebufferAttachmentParameteri(
                GL_FRAMEBUFFER,
                GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            ) != GL_NONE
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer)
        }
    }

    private fun chooseSceneSource(
        mainTarget: RenderTarget,
        accessor: LevelRendererAccessor?,
        finalTarget: NamedTarget
    ): NamedTarget {
        if (accessor == null || !accessor.hasTransparencyChain()) {
            return finalTarget
        }
        if (finalTarget.target.frameBufferId != mainTarget.frameBufferId) {
            return finalTarget
        }
        val selected = NamedTarget("main-scene", mainTarget)
        logSceneSource(finalTarget, selected)
        return selected
    }

    private fun chooseFallback(
        mainTarget: RenderTarget,
        accessor: LevelRendererAccessor?,
        boundFramebufferId: Int,
        candidates: List<NamedTarget>
    ): NamedTarget {
        if (boundFramebufferId == 0) {
            logFallback(boundFramebufferId, candidates, "default-framebuffer", "main-fallback")
            return NamedTarget("main-fallback", mainTarget)
        }
        if (accessor == null) {
            logFallback(boundFramebufferId, candidates, "no-accessor", "main-fallback")
            return NamedTarget("main-fallback", mainTarget)
        }
        if (accessor.hasTransparencyChain()) {
            accessor.itemEntityTarget()?.let {
                logFallback(boundFramebufferId, candidates, "transparency-chain", "itemEntity-fallback")
                return NamedTarget("itemEntity-fallback", it)
            }
            accessor.translucentTarget()?.let {
                logFallback(boundFramebufferId, candidates, "transparency-chain", "translucent-fallback")
                return NamedTarget("translucent-fallback", it)
            }
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
            cn.coostack.cooparticlesapi.CooParticlesConstants.logger.debug(
                "Falling back post target selection boundFbo={} reason={} selected={} candidates={}",
                boundFramebufferId,
                reason,
                selectedLabel,
                candidateSummary
            )
        } else {
            cn.coostack.cooparticlesapi.CooParticlesConstants.logger.warn(
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
        cn.coostack.cooparticlesapi.CooParticlesConstants.logger.info(
            "Using externally bound post framebuffer boundFbo={} candidates={}",
            boundFramebufferId,
            candidateSummary
        )
    }

    private fun logSceneSource(finalTarget: NamedTarget, sceneSource: NamedTarget) {
        val signature = "${finalTarget.label}:${finalTarget.target.frameBufferId}|${sceneSource.label}:${sceneSource.target.frameBufferId}"
        if (signature == lastSceneSourceSignature) {
            return
        }
        lastSceneSourceSignature = signature
        if (finalTarget.target.frameBufferId == sceneSource.target.frameBufferId) {
            cn.coostack.cooparticlesapi.CooParticlesConstants.logger.debug(
                "Scene source target aligned with final target label={} fbo={}",
                finalTarget.label,
                finalTarget.target.frameBufferId
            )
        } else {
            cn.coostack.cooparticlesapi.CooParticlesConstants.logger.info(
                "Scene source target differs from final target final={}({}) scene={}({})",
                finalTarget.label,
                finalTarget.target.frameBufferId,
                sceneSource.label,
                sceneSource.target.frameBufferId
            )
        }
    }
}
