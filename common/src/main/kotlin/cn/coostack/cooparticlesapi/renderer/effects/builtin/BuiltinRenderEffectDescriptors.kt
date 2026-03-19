package cn.coostack.cooparticlesapi.renderer.effects.builtin

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import cn.coostack.cooparticlesapi.renderer.glow.PersistentBloom
import cn.coostack.cooparticlesapi.renderer.glow.PersistentBloomContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.PostGlowSphereConfig
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlow
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.light.WorldLight
import cn.coostack.cooparticlesapi.renderer.light.WorldLightProvider
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * screen glow 请求的 descriptor payload。
 */
data class ScreenGlowRenderRequest(
    val frameContext: RenderFrameContext,
    val collect: (ScreenGlowRenderContext, MutableList<ScreenGlow>) -> Unit
)

/**
 * persistent bloom 请求的 descriptor payload。
 */
data class PersistentBloomRenderRequest(
    val frameContext: RenderFrameContext,
    val collect: (ScreenGlowRenderContext, MutableList<PersistentBloom>) -> Unit
)

/**
 * 基于内容 mask 的 bloom 参数。
 *
 * - `threshold/thresholdSoftness` 控制进入 bloom mask 的门限
 * - `blurSigma/blurRange` 控制高斯模糊范围
 * - `intensity` 控制最终 bloom 亮度
 * - `baseMaskIntensity` 控制未模糊部分保留比例
 * - `tint` 控制最终 bloom 色调
 */
data class MaskBloomConfig(
    val blurSigma: Float = 14.0f,
    val blurRange: Float = 10.0f,
    val intensity: Float = 3.0f,
    val baseMaskIntensity: Float = 0.24f,
    val threshold: Float = 0.0f,
    val thresholdSoftness: Float = 0.015f,
    val tint: Vector3f = Vector3f(1.0f, 1.0f, 1.0f)
)

/**
 * 贴图 billboard mask 绘制参数。
 *
 * 这个结构用于通用的“白色/alpha 贴图发光”场景，不限制调用方只能画 billboard。
 * 若调用方需要更复杂的模型，可直接在 `MaskBloomRenderRequest.renderMask(...)` 回调里自行绘制。
 */
data class MaskBloomTexturedBillboard(
    val textures: SimpleTextures,
    val modelMatrix: Matrix4f,
    val tint: Vector4f = Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
    val sourceBoost: Float = 1.0f,
    val alphaCutoff: Float = 0.01f,
    val emissiveCutoff: Float = 0.02f,
    val alphaWeight: Float = 1.0f,
    val emissiveWeight: Float = 1.0f,
    val fullQuadMask: Boolean = false,
    val fullQuadMaskSoftness: Float = 0.12f
)

/**
 * mask bloom callback 上下文。
 */
class MaskBloomMaskRenderContext internal constructor(
    val frameContext: RenderFrameContext,
    val sourceEntity: RenderEntity?,
    val sourceInstanceId: String,
    val cameraWorldPos: Vector3f,
    val screenSize: Vector2f,
    private val texturedBillboardDrawer: (MaskBloomTexturedBillboard) -> Unit
) {
    /**
     * 使用内建 helper 把纹理 billboard 绘制到 bloom mask。
     */
    fun drawTexturedBillboard(content: MaskBloomTexturedBillboard) {
        texturedBillboardDrawer(content)
    }
}

/**
 * 基于模型/贴图内容绘制 mask 的 bloom 请求 payload。
 */
data class MaskBloomRenderRequest(
    val frameContext: RenderFrameContext,
    val sourceEntity: RenderEntity?,
    val sourceInstanceId: String,
    val config: MaskBloomConfig,
    val renderMask: (MaskBloomMaskRenderContext) -> Unit
)

/**
 * world light 请求的 descriptor payload。
 */
data class WorldLightRenderRequest(
    val frameContext: RenderFrameContext,
    val collect: (MutableList<WorldLight>) -> Unit
)

/**
 * glow sphere 请求的 descriptor payload。
 */
data class PostGlowSphereRenderRequest(
    val entity: RenderEntity,
    val frameContext: RenderFrameContext,
    val config: PostGlowSphereConfig
)

/**
 * compute dispatch 请求的 descriptor payload。
 */
data class ComputeDispatchRenderRequest(
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
 * - 直接构建内建 glow/bloom/light/compute descriptor
 */
object BuiltinRenderEffectDescriptors {
    private val scenePostCapabilities = setOf(
        RenderBackendCapability.FINAL_FRAME_POST,
        RenderBackendCapability.SCENE_COLOR_COPY,
        RenderBackendCapability.SCENE_DEPTH_READ
    )
    private val scenePostTargets = setOf(
        RenderSceneTargets.POST,
        RenderSceneTargets.SCENE_COLOR,
        RenderSceneTargets.SCENE_DEPTH
    )

    /**
     * 只根据实体实现的 builtin provider 接口推导附加 feature。
     *
     * 这里返回的是“provider 带来的增量声明”，不包含任何 world pass /
     * frame-post 的默认阶段假设，方便更细粒度的 renderer 组合显式控制。
     */
    fun describeBuiltinProviders(entity: RenderEntity): RenderEntityFeatureSet {
        var features = RenderEntityFeatureSet(
            stages = emptySet(),
            requestedSceneTargets = emptySet(),
            effectTypes = emptySet(),
            localRendererEnabled = false,
            effectGraphEnabled = false
        )
        if (entity is ScreenGlowContextProvider || entity is ScreenGlowProvider) {
            features = features.merge(
                RenderEntityFeatureSet(
                    stages = setOf(RenderFrameStage.FRAME_POST),
                    requestedSceneTargets = scenePostTargets,
                    effectTypes = setOf(BuiltinRenderEffectTypes.SCREEN_GLOW),
                    localRendererEnabled = false,
                    effectGraphEnabled = true
                )
            )
        }
        if (entity is PersistentBloomContextProvider) {
            features = features.merge(
                RenderEntityFeatureSet(
                    stages = setOf(RenderFrameStage.FRAME_POST),
                    requestedSceneTargets = scenePostTargets,
                    effectTypes = setOf(BuiltinRenderEffectTypes.PERSISTENT_BLOOM),
                    localRendererEnabled = false,
                    effectGraphEnabled = true
                )
            )
        }
        if (entity is WorldLightProvider) {
            features = features.merge(
                RenderEntityFeatureSet(
                    stages = setOf(RenderFrameStage.FRAME_POST),
                    requestedSceneTargets = scenePostTargets,
                    effectTypes = setOf(BuiltinRenderEffectTypes.WORLD_LIGHT),
                    localRendererEnabled = false,
                    effectGraphEnabled = true
                )
            )
        }
        return features
    }

    /**
     * 根据实体是否实现了若干 built-in provider 接口，推导它需要的 feature set。
     */
    fun describeEntity(entity: RenderEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet().merge(describeBuiltinProviders(entity))
    }

    /**
     * 把实体实现的内建 provider 接口转换为 descriptor 提交到 effect graph。
     */
    fun collectEntity(
        entity: RenderEntity,
        frameContext: RenderFrameContext,
        collector: RenderContributionCollector
    ) {
        when (entity) {
            is ScreenGlowContextProvider -> collector.submit(
                screenGlow(
                    sourceInstanceId = entity.uuid.toString(),
                    provider = entity,
                    frameContext = frameContext
                )
            )
            is ScreenGlowProvider -> collector.submit(
                screenGlow(
                    sourceInstanceId = entity.uuid.toString(),
                    provider = entity,
                    frameContext = frameContext
                )
            )
        }
        if (entity is PersistentBloomContextProvider) {
            collector.submit(
                persistentBloom(
                    sourceInstanceId = entity.uuid.toString(),
                    provider = entity,
                    frameContext = frameContext
                )
            )
        }
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
     * 直接构造一个 screen glow descriptor。
     *
     * `collect` 回调负责在真正执行时向输出列表写入 `ScreenGlow`。
     */
    fun screenGlow(
        sourceInstanceId: String,
        frameContext: RenderFrameContext,
        priority: Int = 300,
        collect: (ScreenGlowRenderContext, MutableList<ScreenGlow>) -> Unit
    ): RenderEffectDescriptor {
        return RenderEffectDescriptor(
            effectType = BuiltinRenderEffectTypes.SCREEN_GLOW,
            effectId = BuiltinRenderEffectTypes.SCREEN_GLOW.toString(),
            priority = priority,
            sourceInstanceId = sourceInstanceId,
            requiredCapabilities = scenePostCapabilities,
            payload = ScreenGlowRenderRequest(frameContext, collect)
        )
    }

    /**
     * 使用旧版 `ScreenGlowProvider` 构造 descriptor。
     */
    fun screenGlow(
        sourceInstanceId: String,
        provider: ScreenGlowProvider,
        frameContext: RenderFrameContext
    ): RenderEffectDescriptor {
        return screenGlow(sourceInstanceId, frameContext) { _, output ->
            provider.collectScreenGlows(frameContext.tickDelta, output)
        }
    }

    /**
     * 使用上下文感知版 `ScreenGlowContextProvider` 构造 descriptor。
     */
    fun screenGlow(
        sourceInstanceId: String,
        provider: ScreenGlowContextProvider,
        frameContext: RenderFrameContext
    ): RenderEffectDescriptor {
        return screenGlow(sourceInstanceId, frameContext) { glowContext, output ->
            provider.collectScreenGlows(glowContext, output)
        }
    }

    /**
     * 直接构造一个 persistent bloom descriptor。
     */
    fun persistentBloom(
        sourceInstanceId: String,
        frameContext: RenderFrameContext,
        priority: Int = 200,
        collect: (ScreenGlowRenderContext, MutableList<PersistentBloom>) -> Unit
    ): RenderEffectDescriptor {
        return RenderEffectDescriptor(
            effectType = BuiltinRenderEffectTypes.PERSISTENT_BLOOM,
            effectId = BuiltinRenderEffectTypes.PERSISTENT_BLOOM.toString(),
            priority = priority,
            sourceInstanceId = sourceInstanceId,
            requiredCapabilities = scenePostCapabilities,
            payload = PersistentBloomRenderRequest(frameContext, collect)
        )
    }

    /**
     * 使用 `PersistentBloomContextProvider` 构造 descriptor。
     */
    fun persistentBloom(
        sourceInstanceId: String,
        provider: PersistentBloomContextProvider,
        frameContext: RenderFrameContext
    ): RenderEffectDescriptor {
        return persistentBloom(sourceInstanceId, frameContext) { glowContext, output ->
            provider.collectPersistentBlooms(glowContext, output)
        }
    }

    /**
     * 构造一个内容驱动 mask bloom descriptor。
     *
     * 调用方需要在 `renderMask` 回调里把“应当发光的内容”绘制到 mask 目标。
     * executor 会统一负责 prefilter -> blur -> composite。
     */
    fun maskBloom(
        effectId: String,
        sourceInstanceId: String,
        frameContext: RenderFrameContext,
        sourceEntity: RenderEntity? = null,
        config: MaskBloomConfig = MaskBloomConfig(),
        priority: Int = 220,
        requiredCapabilities: Set<RenderBackendCapability> = scenePostCapabilities,
        renderMask: (MaskBloomMaskRenderContext) -> Unit
    ): RenderEffectDescriptor {
        return RenderEffectDescriptor(
            effectType = BuiltinRenderEffectTypes.MASK_BLOOM,
            effectId = effectId,
            priority = priority,
            sourceInstanceId = sourceInstanceId,
            requiredCapabilities = requiredCapabilities,
            payload = MaskBloomRenderRequest(
                frameContext = frameContext,
                sourceEntity = sourceEntity,
                sourceInstanceId = sourceInstanceId,
                config = config,
                renderMask = renderMask
            )
        )
    }

    /**
     * 使用内建 textured billboard helper 构造 mask bloom descriptor。
     */
    fun maskBloomTexturedBillboard(
        effectId: String,
        sourceInstanceId: String,
        frameContext: RenderFrameContext,
        textures: SimpleTextures,
        modelMatrix: Matrix4f,
        sourceEntity: RenderEntity? = null,
        config: MaskBloomConfig = MaskBloomConfig(),
        tint: Vector4f = Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
        sourceBoost: Float = 1.0f,
        alphaCutoff: Float = 0.01f,
        emissiveCutoff: Float = 0.02f,
        alphaWeight: Float = 1.0f,
        emissiveWeight: Float = 1.0f,
        fullQuadMask: Boolean = false,
        fullQuadMaskSoftness: Float = 0.12f,
        priority: Int = 220,
        requiredCapabilities: Set<RenderBackendCapability> = scenePostCapabilities
    ): RenderEffectDescriptor {
        return maskBloom(
            effectId = effectId,
            sourceInstanceId = sourceInstanceId,
            frameContext = frameContext,
            sourceEntity = sourceEntity,
            config = config,
            priority = priority,
            requiredCapabilities = requiredCapabilities
        ) { context ->
            context.drawTexturedBillboard(
                MaskBloomTexturedBillboard(
                    textures = textures,
                    modelMatrix = Matrix4f(modelMatrix),
                    tint = Vector4f(tint),
                    sourceBoost = sourceBoost,
                    alphaCutoff = alphaCutoff,
                    emissiveCutoff = emissiveCutoff,
                    alphaWeight = alphaWeight,
                    emissiveWeight = emissiveWeight,
                    fullQuadMask = fullQuadMask,
                    fullQuadMaskSoftness = fullQuadMaskSoftness
                )
            )
        }
    }

    /**
     * 复用 world-pass 模型内容来写 glow mask。
     *
     * 适用于“同一套模型怎么画到主通道，就怎么把颜色/alpha/顶点色写到 glow mask”。
     */
    fun sharedModelMaskBloom(
        effectId: String,
        sourceInstanceId: String,
        frameContext: RenderFrameContext,
        sourceEntity: RenderEntity? = null,
        config: MaskBloomConfig = defaultRenderEntityModelGlowConfig(),
        priority: Int = 220,
        requiredCapabilities: Set<RenderBackendCapability> = scenePostCapabilities,
        renderMask: (MaskBloomMaskRenderContext) -> Unit
    ): RenderEffectDescriptor {
        return maskBloom(
            effectId = effectId,
            sourceInstanceId = sourceInstanceId,
            frameContext = frameContext,
            sourceEntity = sourceEntity,
            config = config,
            priority = priority,
            requiredCapabilities = requiredCapabilities,
            renderMask = renderMask
        )
    }

    /**
     * 只绘制 glow mask，而不要求存在现成的 world-pass 模型路径。
     */
    fun customGlowMaskBloom(
        effectId: String,
        sourceInstanceId: String,
        frameContext: RenderFrameContext,
        sourceEntity: RenderEntity? = null,
        config: MaskBloomConfig = defaultRenderEntityModelGlowConfig(),
        priority: Int = 220,
        requiredCapabilities: Set<RenderBackendCapability> = scenePostCapabilities,
        renderMask: (MaskBloomMaskRenderContext) -> Unit
    ): RenderEffectDescriptor {
        return maskBloom(
            effectId = effectId,
            sourceInstanceId = sourceInstanceId,
            frameContext = frameContext,
            sourceEntity = sourceEntity,
            config = config,
            priority = priority,
            requiredCapabilities = requiredCapabilities,
            renderMask = renderMask
        )
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
     * 构造一个帧尾 glow 球体 descriptor。
     */
    @Deprecated(
        message = "RenderEntity glow should use content-driven MASK_BLOOM. POST_GLOW_SPHERE is legacy compatibility only.",
        replaceWith = ReplaceWith("sharedModelMaskBloom(effectId, sourceInstanceId, frameContext, entity, defaultRenderEntityModelGlowConfig(), priority, requiredCapabilities, renderMask)")
    )
    fun postGlowSphere(
        effectId: String,
        sourceInstanceId: String,
        entity: RenderEntity,
        frameContext: RenderFrameContext,
        config: PostGlowSphereConfig,
        priority: Int = 0,
        requiredCapabilities: Set<RenderBackendCapability> = setOf(RenderBackendCapability.FINAL_FRAME_POST)
    ): RenderEffectDescriptor {
        return RenderEffectDescriptor(
            effectType = BuiltinRenderEffectTypes.POST_GLOW_SPHERE,
            effectId = effectId,
            priority = priority,
            sourceInstanceId = sourceInstanceId,
            requiredCapabilities = requiredCapabilities,
            payload = PostGlowSphereRenderRequest(
                entity = entity,
                frameContext = frameContext,
                config = config
            )
        )
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

    /**
     * RenderEntity 模型泛光的推荐默认参数。
     *
     * 目标观感是“过曝主体 + 宽软 halo”，也就是整块 emissive 内容先稳定进入 mask，
     * 再通过更宽的 blur 和更强的 composite 形成类似离屏截图里那种白色泛光。
     */
    fun defaultRenderEntityModelGlowConfig(): MaskBloomConfig {
        return MaskBloomConfig()
    }
}
