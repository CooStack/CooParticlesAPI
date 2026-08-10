package cn.coostack.cooparticlesapi.renderer.effects.builtin

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectCollector
import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import cn.coostack.cooparticlesapi.renderer.light.WorldLight
import cn.coostack.cooparticlesapi.renderer.light.WorldLightProvider

/**
 * world light 请求的 descriptor payload。
 */
internal data class WorldLightRenderRequest(
    val frameContext: RenderFrameContext,
    val collect: (MutableList<WorldLight>) -> Unit
)

/**
 * compute dispatch 请求的 descriptor payload。
 */
internal data class ComputeDispatchRenderRequest(
    val program: CooComputeShaderProgram,
    val groupX: Int,
    val groupY: Int = 1,
    val groupZ: Int = 1,
    val prepare: (CooComputeShaderProgram) -> Unit = {},
    val memoryBarrierMask: Int? = null
)

/**
 * 内建 descriptor 构造辅助类。
 *
 * 这里统一提供两类能力：
 * - 从实体推导 feature set
 * - 直接构建内建 light/compute descriptor
 */
internal object BuiltinRenderEffectDescriptors {
    private val scenePostCapabilities = setOf(
        RenderBackendCapability.FINAL_FRAME_POST,
        RenderBackendCapability.SCENE_COLOR_COPY,
        RenderBackendCapability.SCENE_DEPTH_READ
    )
    /**
     * 把实体实现的内建 provider 接口转换为 descriptor 提交到 effect graph。
     */
    fun collectEntity(
        entity: RenderEntity,
        frameContext: RenderFrameContext,
        collector: RenderEffectCollector
    ) {
        if (entity is WorldLightProvider) {
            collector.submit(
                worldLight(
                    sourceInstanceId = entity.uuid.toString(),
                    provider = entity,
                    frameContext = frameContext
                )
            )
        }
    }

    /**
     * 直接构造一个 world light descriptor。
     */
    fun worldLight(
        sourceInstanceId: String,
        frameContext: RenderFrameContext,
        priority: Int = 100,
        collect: (MutableList<WorldLight>) -> Unit
    ): RenderEffectDescriptor {
        return RenderEffectDescriptor(
            effectType = BuiltinRenderEffectTypes.WORLD_LIGHT,
            effectId = BuiltinRenderEffectTypes.WORLD_LIGHT.toString(),
            priority = priority,
            sourceInstanceId = sourceInstanceId,
            requiredCapabilities = scenePostCapabilities,
            payload = WorldLightRenderRequest(frameContext, collect)
        )
    }

    /**
     * 使用 `WorldLightProvider` 构造 descriptor。
     */
    fun worldLight(
        sourceInstanceId: String,
        provider: WorldLightProvider,
        frameContext: RenderFrameContext
    ): RenderEffectDescriptor {
        return worldLight(sourceInstanceId, frameContext) { output ->
            provider.collectWorldLights(frameContext.tickDelta, output)
        }
    }

    /**
     * 构造一个 compute dispatch descriptor。
     */
    fun computeDispatch(
        effectId: String,
        sourceInstanceId: String,
        program: CooComputeShaderProgram,
        groupX: Int,
        groupY: Int = 1,
        groupZ: Int = 1,
        priority: Int = 150,
        requiredCapabilities: Set<RenderBackendCapability> = setOf(RenderBackendCapability.FINAL_FRAME_POST),
        memoryBarrierMask: Int? = null,
        prepare: (CooComputeShaderProgram) -> Unit = {}
    ): RenderEffectDescriptor {
        return RenderEffectDescriptor(
            effectType = BuiltinRenderEffectTypes.COMPUTE_DISPATCH,
            effectId = effectId,
            priority = priority,
            sourceInstanceId = sourceInstanceId,
            requiredCapabilities = requiredCapabilities,
            payload = ComputeDispatchRenderRequest(
                program = program,
                groupX = groupX,
                groupY = groupY,
                groupZ = groupZ,
                prepare = prepare,
                memoryBarrierMask = memoryBarrierMask
            )
        )
    }

}
