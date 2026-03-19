package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.client.RenderUtil
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectTypes
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomConfig
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomMaskRenderContext
import org.joml.Matrix4f

private val renderEntityGlowSceneTargets = setOf(
    RenderSceneTargets.POST,
    RenderSceneTargets.SCENE_COLOR,
    RenderSceneTargets.SCENE_DEPTH
)

/**
 * RenderEntity 在客户端的共享生命周期与能力声明接口。
 *
 * 它只保留所有 renderer 都必须面对的公共部分：
 * - `describeFeatures(...)` 声明阶段 / target / effect type 需求
 * - `createVisualProfile(...)` 生成视觉画像
 * - `initialize(...)` 做资源准备
 *
 * 真正参与 world pass 或 frame-post 的能力由更细的子接口显式表达，
 * 避免再通过“默认空实现”来模糊 renderer 的真实职责。
 */
interface RenderEntityRenderer<T : RenderEntity> {
    /**
     * 描述当前实体在渲染管线里的能力需求。
     *
     * 默认会从内建 provider 中推导出一份 feature set。
     * 如果你明确知道自己需要哪些 stage / target / effect type，建议直接覆盖。
     */
    fun describeFeatures(entity: T): RenderEntityFeatureSet {
        return BuiltinRenderEffectDescriptors.describeEntity(entity)
    }

    /**
     * 生成当前实体的视觉画像。
     *
     * 这个对象不会直接渲染任何内容，而是向 runtime 声明混合方式、
     * scene color/depth 依赖和优先级等视觉元信息。
     */
    fun createVisualProfile(entity: T): RenderEntityVisualProfile {
        return RenderEntityVisualProfile()
    }

    /**
     * renderer 的初始化钩子。
     *
     * 适合做 shader、缓存、临时资源或一次性状态的准备。
     */
    fun initialize(instance: RenderEntityInstance<T>)
}

/**
 * 显式声明当前 renderer 参与 world pass 本地几何绘制。
 */
interface WorldPassRenderEntityRenderer<T : RenderEntity> : RenderEntityRenderer<T> {
    fun renderLocal(input: LocalRenderInput<T>)
}

/**
 * 显式声明当前 renderer 会向 frame-post effect graph 提交 descriptor。
 */
interface FramePostRenderEntityRenderer<T : RenderEntity> : RenderEntityRenderer<T> {
    fun collectRenderContributions(
        input: RenderContributionInput<T>,
        collector: RenderContributionCollector
    )
}

/**
 * 共享模型绘制路径的 pass 标记。
 *
 * - `WORLD_PASS`：常规世界绘制
 * - `GLOW_MASK`：把同一套模型内容写入 glow mask
 */
enum class SharedModelMaskBloomPass {
    WORLD_PASS,
    GLOW_MASK
}

/**
 * 复用同一套模型绘制逻辑的输入。
 */
data class SharedModelMaskBloomInput<T : RenderEntity>(
    val instance: RenderEntityInstance<T>,
    val pass: SharedModelMaskBloomPass,
    val tickDelta: Float,
    val viewMatrix: Matrix4f,
    val projMatrix: Matrix4f,
    val modelMatrix: Matrix4f,
    val maskContext: MaskBloomMaskRenderContext? = null
)

/**
 * 专用 glow-mask 绘制输入。
 */
data class GlowMaskRenderInput<T : RenderEntity>(
    val instance: RenderEntityInstance<T>,
    val tickDelta: Float,
    val viewMatrix: Matrix4f,
    val projMatrix: Matrix4f,
    val modelMatrix: Matrix4f,
    val maskContext: MaskBloomMaskRenderContext
)

/**
 * 适用于“已有 world-pass 模型路径，希望直接复用同一套模型内容写 glow mask”的接口。
 */
interface SharedModelMaskBloomRenderEntityRenderer<T : RenderEntity> :
    WorldPassRenderEntityRenderer<T>,
    FramePostRenderEntityRenderer<T> {
    fun renderSharedModel(input: SharedModelMaskBloomInput<T>)

    fun glowMaskEffectId(entity: T): String = "${entity.getRenderID()}#model_glow"

    fun glowMaskConfig(entity: T): MaskBloomConfig =
        BuiltinRenderEffectDescriptors.defaultRenderEntityModelGlowConfig()

    fun glowMaskPriority(entity: T): Int = 220

    fun buildGlowMaskModelMatrix(entity: T, frameContext: RenderFrameContext): Matrix4f {
        return RenderUtil.buildModelMatrix(entity, frameContext.tickDelta)
    }

    override fun describeFeatures(entity: T): RenderEntityFeatureSet {
        return BuiltinRenderEffectDescriptors.describeBuiltinProviders(entity).merge(
            RenderEntityFeatureSet(
                stages = setOf(RenderFrameStage.WORLD_PASS, RenderFrameStage.FRAME_POST),
                requestedSceneTargets = renderEntityGlowSceneTargets,
                effectTypes = setOf(BuiltinRenderEffectTypes.MASK_BLOOM),
                localRendererEnabled = true,
                effectGraphEnabled = true
            )
        )
    }

    override fun renderLocal(input: LocalRenderInput<T>) {
        renderSharedModel(
            SharedModelMaskBloomInput(
                instance = input.instance,
                pass = SharedModelMaskBloomPass.WORLD_PASS,
                tickDelta = input.tickDelta,
                viewMatrix = Matrix4f(input.viewMatrix),
                projMatrix = Matrix4f(input.projMatrix),
                modelMatrix = Matrix4f(input.modelMatrix)
            )
        )
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<T>,
        collector: RenderContributionCollector
    ) {
        val entity = input.instance.entity
        val glowModelMatrix = buildGlowMaskModelMatrix(entity, input.frameContext)
        collector.submit(
            BuiltinRenderEffectDescriptors.sharedModelMaskBloom(
                effectId = glowMaskEffectId(entity),
                sourceInstanceId = entity.uuid.toString(),
                frameContext = input.frameContext,
                sourceEntity = entity,
                config = glowMaskConfig(entity),
                priority = glowMaskPriority(entity)
            ) { maskContext ->
                renderSharedModel(
                    SharedModelMaskBloomInput(
                        instance = input.instance,
                        pass = SharedModelMaskBloomPass.GLOW_MASK,
                        tickDelta = input.frameContext.tickDelta,
                        viewMatrix = Matrix4f(input.frameContext.viewMatrix),
                        projMatrix = Matrix4f(input.frameContext.projMatrix),
                        modelMatrix = Matrix4f(glowModelMatrix),
                        maskContext = maskContext
                    )
                )
            }
        )
    }
}

/**
 * 适用于“没有现成 world-pass 模型路径，但仍需要局部模型 glow”的专用 glow-mask 接口。
 */
interface DedicatedGlowMaskRenderEntityRenderer<T : RenderEntity> :
    FramePostRenderEntityRenderer<T> {
    fun renderGlowMask(input: GlowMaskRenderInput<T>)

    fun glowMaskEffectId(entity: T): String = "${entity.getRenderID()}#custom_glow_mask"

    fun glowMaskConfig(entity: T): MaskBloomConfig =
        BuiltinRenderEffectDescriptors.defaultRenderEntityModelGlowConfig()

    fun glowMaskPriority(entity: T): Int = 220

    fun buildGlowMaskModelMatrix(entity: T, frameContext: RenderFrameContext): Matrix4f {
        return RenderUtil.buildModelMatrix(entity, frameContext.tickDelta)
    }

    override fun describeFeatures(entity: T): RenderEntityFeatureSet {
        return BuiltinRenderEffectDescriptors.describeBuiltinProviders(entity).merge(
            RenderEntityFeatureSet(
                stages = setOf(RenderFrameStage.FRAME_POST),
                requestedSceneTargets = renderEntityGlowSceneTargets,
                effectTypes = setOf(BuiltinRenderEffectTypes.MASK_BLOOM),
                localRendererEnabled = false,
                effectGraphEnabled = true
            )
        )
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<T>,
        collector: RenderContributionCollector
    ) {
        val entity = input.instance.entity
        val glowModelMatrix = buildGlowMaskModelMatrix(entity, input.frameContext)
        collector.submit(
            BuiltinRenderEffectDescriptors.customGlowMaskBloom(
                effectId = glowMaskEffectId(entity),
                sourceInstanceId = entity.uuid.toString(),
                frameContext = input.frameContext,
                sourceEntity = entity,
                config = glowMaskConfig(entity),
                priority = glowMaskPriority(entity)
            ) { maskContext ->
                renderGlowMask(
                    GlowMaskRenderInput(
                        instance = input.instance,
                        tickDelta = input.frameContext.tickDelta,
                        viewMatrix = Matrix4f(input.frameContext.viewMatrix),
                        projMatrix = Matrix4f(input.frameContext.projMatrix),
                        modelMatrix = Matrix4f(glowModelMatrix),
                        maskContext = maskContext
                    )
                )
            }
        )
    }
}

/**
 * 可选的同步后刷新钩子。
 */
interface RenderEntityUpdateHook<T : RenderEntity> : RenderEntityRenderer<T> {
    fun update(instance: RenderEntityInstance<T>, entity: T)
}

/**
 * 可选的资源释放钩子。
 */
interface RenderEntityReleaseHook<T : RenderEntity> : RenderEntityRenderer<T> {
    fun release(instance: RenderEntityInstance<T>)
}
