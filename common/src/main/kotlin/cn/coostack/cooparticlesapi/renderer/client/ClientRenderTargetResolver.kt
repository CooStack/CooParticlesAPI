package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.accessor.LevelRendererAccessor
import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL33
import java.util.LinkedHashMap

data class ResolvedRenderTargets(
    val sceneColorTarget: RenderTarget,
    val sceneDepthTarget: RenderTarget,
    val finalCompositeTarget: RenderTarget,
    val boundFramebufferId: Int,
    val targetLabel: String
) {
    val sceneColorTextureId: Int
        get() = sceneColorTarget.colorTextureId
    val sceneDepthTextureId: Int
        get() = sceneDepthTarget.depthTextureId
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

    fun resolveCurrentTargets(): ResolvedRenderTargets {
        val minecraft = Minecraft.getInstance()
        val mainTarget = minecraft.mainRenderTarget
        val accessor = minecraft.levelRenderer as? LevelRendererAccessor
        val boundFramebufferId = GL33.glGetInteger(GL33.GL_FRAMEBUFFER_BINDING)
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
            ?: chooseFallback(mainTarget, accessor, boundFramebufferId, candidates.values.toList())
        val sceneSourceTarget = chooseSceneSource(mainTarget, accessor, matchedTarget)
        val sceneDepthTarget = if (sceneSourceTarget.target.depthTextureId > 0) {
            sceneSourceTarget.target
        } else if (matchedTarget.target.depthTextureId > 0) {
            matchedTarget.target
        } else {
            mainTarget
        }

        return ResolvedRenderTargets(
            sceneColorTarget = sceneSourceTarget.target,
            sceneDepthTarget = sceneDepthTarget,
            finalCompositeTarget = matchedTarget.target,
            boundFramebufferId = boundFramebufferId,
            targetLabel = matchedTarget.label
        )
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
