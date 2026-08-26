package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.accessor.LevelRendererAccessor
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneResource
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneResources
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.post.OpenGlPostEffectExecutionBackend
import net.minecraft.client.Minecraft

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
        val resources = mutableListOf(
            RenderSceneResource(
                id = RenderSceneTargets.MAIN,
                label = "main",
                target = minecraft.mainRenderTarget
            ),
            RenderSceneResource(
                id = RenderSceneTargets.POST,
                label = resolvedTargets.targetLabel,
                target = resolvedTargets.finalCompositeTarget,
                colorTextureId = resolvedTargets.sceneColorTextureId,
                depthTextureId = sceneDepthTextureId
            ),
            RenderSceneResource(
                id = RenderSceneTargets.SCENE_COLOR,
                label = "${resolvedTargets.targetLabel}:color",
                target = resolvedTargets.sceneColorTarget,
                colorTextureId = resolvedTargets.sceneColorTextureId,
                depthTextureId = resolvedTargets.sceneColorTarget.depthTextureId
            ),
            RenderSceneResource(
                id = RenderSceneTargets.SCENE_DEPTH,
                label = "${resolvedTargets.targetLabel}:depth",
                target = resolvedTargets.sceneDepthTarget,
                colorTextureId = resolvedTargets.sceneDepthTarget.colorTextureId.takeIf { !resolvedTargets.externalFramebuffer },
                depthTextureId = sceneDepthTextureId
            ),
            RenderSceneResource(
                id = RenderSceneTargets.SCENE_DEPTH_NO_HAND,
                label = "${resolvedTargets.targetLabel}:depth-no-hand",
                target = resolvedTargets.sceneDepthTarget,
                colorTextureId = null,
                depthTextureId = sceneDepthNoHandTextureId
            ),
            RenderSceneResource(
                id = RenderSceneTargets.TERRAIN_DEPTH,
                label = "${resolvedTargets.targetLabel}:terrain-depth",
                target = resolvedTargets.sceneDepthTarget,
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

        accessor?.translucentTarget()?.let { target ->
            resources += RenderSceneResource(RenderSceneTargets.TRANSLUCENT_TARGET, "translucent", target)
        }
        accessor?.itemEntityTarget()?.let { target ->
            resources += RenderSceneResource(RenderSceneTargets.ITEM_ENTITY_TARGET, "itemEntity", target)
        }
        accessor?.particlesTarget()?.let { target ->
            resources += RenderSceneResource(RenderSceneTargets.PARTICLES_TARGET, "particles", target)
        }
        accessor?.weatherTarget()?.let { target ->
            resources += RenderSceneResource(RenderSceneTargets.WEATHER_TARGET, "weather", target)
        }
        accessor?.cloudsTarget()?.let { target ->
            resources += RenderSceneResource(RenderSceneTargets.CLOUDS_TARGET, "clouds", target)
        }
        return RenderSceneResources.of(*resources.toTypedArray())
    }
}
