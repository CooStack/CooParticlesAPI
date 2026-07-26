package cn.coostack.cooparticlesapi.renderer.effects.builtin

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import cn.coostack.cooparticlesapi.renderer.light.WorldLight
import cn.coostack.cooparticlesapi.renderer.light.WorldLightProvider
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * mask bloom 的泛光模式。
 *
 * 模式只影响 executor 内部的模糊与合成策略，不改变任何调用方式；
 * 默认 [SOFT] 与旧版本行为完全一致。
 */
enum class MaskBloomMode {
    /** 柔和泛光（默认）：单次高斯 ping-pong 模糊，与旧版本观感一致。 */
    SOFT,

    /** 强泛光：双重 ping-pong 模糊 + 更宽的模糊迭代 + 约 1.5 倍合成亮度，适合高能量光效。 */
    STRONG
}

/**
 * 距离聚光补偿参数。
 *
 * 现实中远处的光源因为视角变小、光线聚在一点，观感上会更亮。
 * 启用后 executor 会按相机与 `sourceEntity` 插值渲染位置的距离，
 * 在 `[startDistance, fullDistance]` 区间内线性提升 bloom 亮度，最高到 `maxBoost` 倍。
 *
 * 仅在 descriptor 提供了 `sourceEntity` 时生效；`MaskBloomConfig.distanceCompensation`
 * 为 `null`（默认）时该功能完全关闭。
 */
data class MaskBloomDistanceCompensation(
    /** 开始增强的相机距离（格）。 */
    val startDistance: Float = 16.0f,
    /** 达到最大增强的相机距离（格）。 */
    val fullDistance: Float = 64.0f,
    /** 最大额外亮度倍率，1.0 表示不增强。 */
    val maxBoost: Float = 2.0f
)

/**
 * 基于内容 mask 的 bloom 参数。
 *
 * - `threshold/thresholdSoftness` 控制进入 bloom mask 的门限
 * - `blurSigma/blurRange` 控制高斯模糊范围
 * - `intensity` 控制最终 bloom 亮度
 * - `baseMaskIntensity` 控制未模糊 source 保留比例，默认为 0，只输出真正 bloom
 * - `tint` 控制最终 bloom 色调
 *
 * 以下为可选增强项，默认值下行为与旧版本完全一致：
 * - `bloomMode` 泛光模式；[MaskBloomMode.STRONG] 为强泛光
 * - `exposureCompensation` 曝光补偿（EV 档位），0 表示关闭；按 `2^ev` 缩放 bloom 亮度，可为负
 * - `distanceCompensation` 距离聚光补偿；`null` 表示关闭
 */
data class MaskBloomConfig(
    val blurSigma: Float = 14.0f,
    val blurRange: Float = 10.0f,
    val intensity: Float = 3.0f,
    val baseMaskIntensity: Float = 0.0f,
    val threshold: Float = 0.0f,
    val thresholdSoftness: Float = 0.015f,
    val tint: Vector3f = Vector3f(1.0f, 1.0f, 1.0f),
    val bloomMode: MaskBloomMode = MaskBloomMode.SOFT,
    val exposureCompensation: Float = 0.0f,
    val distanceCompensation: MaskBloomDistanceCompensation? = null
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
    private val maskBloomCapabilities = setOf(
        RenderBackendCapability.FINAL_FRAME_POST,
        RenderBackendCapability.SAFE_WORLD_COMPOSITE
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
     * 构造一个内容驱动 mask bloom descriptor。
     *
     * 调用方需要在 `renderMask` 回调里把“应当发光的内容”绘制到 mask 目标。
     * executor 会统一负责 prefilter -> blur -> composite。
     *
     * 这是 mask bloom 的唯一行为入口；下面的
     * `sharedModelMaskBloom` / `customGlowMaskBloom` 只是带语义命名的便利别名，
     * 用来在 renderer 接口上区分“复用 world-pass 模型”和“专用 glow mask”两种调用场景，
     * 它们与本方法的执行路径完全一致。
     */
    fun maskBloom(
        effectId: String,
        sourceInstanceId: String,
        frameContext: RenderFrameContext,
        sourceEntity: RenderEntity? = null,
        config: MaskBloomConfig = MaskBloomConfig(),
        priority: Int = 220,
        requiredCapabilities: Set<RenderBackendCapability> = maskBloomCapabilities,
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
        requiredCapabilities: Set<RenderBackendCapability> = maskBloomCapabilities
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
        requiredCapabilities: Set<RenderBackendCapability> = maskBloomCapabilities,
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
        requiredCapabilities: Set<RenderBackendCapability> = maskBloomCapabilities,
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
        return MaskBloomConfig(
            blurSigma = 15.0f,
            blurRange = 10.0f,
            intensity = 2.8f,
            baseMaskIntensity = 0.0f,
            threshold = 0.0f,
            thresholdSoftness = 0.015f,
            tint = Vector3f(1.0f, 1.0f, 1.0f)
        )
    }
}
