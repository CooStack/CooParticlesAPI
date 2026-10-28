package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.accessor.LevelRendererAccessor
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneResource
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneResources
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.backend.colorTextureId
import cn.coostack.cooparticlesapi.renderer.backend.depthTextureId
import cn.coostack.cooparticlesapi.renderer.post.OpenGlPostEffectExecutionBackend
import net.minecraft.client.Minecraft
import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.Identifier

/**
 * 在当前绘制阶段组合原版目标和可选 Iris 场景纹理。
 *
 * 通过 [resolveCurrentResources] 取得本帧资源表，不接管其中 target 或 texture 的生命周期。
 */
object ClientRenderSceneResourcesResolver {
    /**
     * 根据输入和 `ClientRenderSceneResourcesResolver` 当前状态解析 `resolveCurrentResources` 结果，供后续构建或绘制使用。
     *
     * 示例：`resolveCurrentResources()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun resolveCurrentResources(): RenderSceneResources {
        val minecraft = Minecraft.getInstance()
        val resolvedTargets = ClientRenderTargetResolver.resolveCurrentTargets()
        val accessor = minecraft.levelRenderer as? LevelRendererAccessor
        val irisSceneDepthTextureId = IrisCompat.currentSceneDepthTexture()?.textureId
        val sceneDepthTextureId = irisSceneDepthTextureId ?: resolvedTargets.sceneDepthTextureId
        val sceneDepthNoHandTextureId = IrisCompat.currentSceneDepthNoHandTexture()?.textureId
            ?: sceneDepthTextureId
        val finalColorTextureId = IrisCompat.currentFinalPassColorTexture()?.textureId
            ?: resolvedTargets.sceneColorTextureId
        val sceneDepthColorTextureId = resolvedTargets.sceneDepthTarget.colorTextureId
            .takeIf { it > 0 && !resolvedTargets.externalFramebuffer }
        val resources = mutableListOf(
            RenderSceneResource(
                id = RenderSceneTargets.MAIN,
                label = "main",
                target = minecraft.gameRenderer.mainRenderTarget()
            ),
            RenderSceneResource(
                id = RenderSceneTargets.POST,
                label = resolvedTargets.targetLabel,
                target = resolvedTargets.finalCompositeTarget,
                colorTextureIds = listOfNotNull(finalColorTextureId),
                colorTextureId = finalColorTextureId,
                depthTextureId = sceneDepthTextureId
            ),
            RenderSceneResource(
                id = RenderSceneTargets.SCENE_COLOR,
                label = "${resolvedTargets.targetLabel}:color",
                target = resolvedTargets.sceneColorTarget,
                colorTextureIds = listOfNotNull(finalColorTextureId),
                colorTextureId = finalColorTextureId,
                depthTextureId = resolvedTargets.sceneColorTarget.depthTextureId
            ),
            RenderSceneResource(
                id = RenderSceneTargets.SCENE_DEPTH,
                label = "${resolvedTargets.targetLabel}:depth",
                target = resolvedTargets.sceneDepthTarget,
                colorTextureIds = listOfNotNull(sceneDepthColorTextureId),
                colorTextureId = sceneDepthColorTextureId,
                depthTextureId = sceneDepthTextureId
            ),
            RenderSceneResource(
                id = RenderSceneTargets.SCENE_DEPTH_NO_HAND,
                label = "${resolvedTargets.targetLabel}:depth-no-hand",
                target = resolvedTargets.sceneDepthTarget,
                colorTextureIds = emptyList(),
                colorTextureId = null,
                depthTextureId = sceneDepthNoHandTextureId
            ),
            RenderSceneResource(
                id = RenderSceneTargets.TERRAIN_DEPTH,
                label = "${resolvedTargets.targetLabel}:terrain-depth",
                target = resolvedTargets.sceneDepthTarget,
                colorTextureIds = emptyList(),
                colorTextureId = null,
                depthTextureId = IrisCompat.currentTerrainDepthTexture()?.textureId
                    ?: sceneDepthTextureId
            ),
            RenderSceneResource(
                id = RenderSceneTargets.TERRAIN_OPAQUE_DEPTH,
                label = "${resolvedTargets.targetLabel}:terrain-opaque-depth",
                depthTextureId = OpenGlPostEffectExecutionBackend.terrainOpaqueDepthTexture()
            ),
            RenderSceneResource(
                id = RenderSceneTargets.TERRAIN_TRANSLUCENT_DEPTH_BEFORE,
                label = "${resolvedTargets.targetLabel}:terrain-translucent-before",
                depthTextureId = OpenGlPostEffectExecutionBackend.terrainTranslucentDepthBeforeTexture()
            ),
            RenderSceneResource(
                id = RenderSceneTargets.TERRAIN_TRANSLUCENT_DEPTH_AFTER,
                label = "${resolvedTargets.targetLabel}:terrain-translucent-after",
                depthTextureId = OpenGlPostEffectExecutionBackend.terrainTranslucentDepthAfterTexture()
            ),
            RenderSceneResource(
                id = RenderSceneTargets.CPARTICLE_COVERAGE_MASK,
                label = "${resolvedTargets.targetLabel}:cparticle-color-coverage",
                colorTextureId = OpenGlPostEffectExecutionBackend.cParticleCoverageTexture()
            )
        )

        accessor?.availableFrameTargets()?.forEach { (purpose, target) ->
            if (purpose != "main") {
                resources += RenderSceneResource(
                    Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "frame_target/$purpose"),
                    purpose,
                    target,
                )
            }
        }
        return RenderSceneResources.of(*resources.toTypedArray())
    }
}
