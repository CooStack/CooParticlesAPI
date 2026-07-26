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
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPrimitive
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelRenderer
import cn.coostack.cooparticlesapi.renderer.model.RenderTypeRenderEntityModelExecutor
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
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

/** Iris 为本地 OpenGL world pass 选择的 entity gbuffer 通道。 */
enum class IrisWorldPassMode {
    ENTITY_SOLID,
    ENTITY_CUTOUT,
    ENTITY_TRANSLUCENT
}

/**
 * 显式让 renderer 的本地 OpenGL world pass 在 Iris 最终合成前写入 entity gbuffer。
 *
 * 未实现这个接口的 renderer 继续使用原有绘制时序。大多数发光、半透明效果使用
 * [IrisWorldPassMode.ENTITY_TRANSLUCENT]；只有确实需要对应材质语义时才改成其他模式。
 */
interface IrisWorldPassRenderEntityRenderer<T : RenderEntity> {
    fun irisWorldPassMode(entity: T): IrisWorldPassMode {
        return IrisWorldPassMode.ENTITY_TRANSLUCENT
    }
}

/**
 * RenderType 路线与本地 OpenGL 路线的组合方式。
 */
enum class RenderTypeBackedRenderMode {
    /**
     * Iris 光影启用时优先 RenderType；没有 Iris 时，如果 renderer 也有 OpenGL world pass，
     * 就回到原路径，否则仍然使用 RenderType，避免实体不可见。
     */
    IRIS_FIRST_OPENGL_FALLBACK,

    /** 不管有没有 Iris，都只走 RenderType 路线。 */
    ALWAYS_RENDER_TYPE,

    /** RenderType 与本地 OpenGL world pass 都执行，适合明确需要双层输出的 renderer。 */
    DUAL,

    /**
     * Iris 光影启用时额外提交 RenderType，同时保留本地 OpenGL world pass。
     *
     * 适合“真实视觉由 Coo shader / FBO 绘制，Iris 只需要一份代理几何参与 entity pass
     * 或 shaderpack 后处理”的 renderer。
     */
    IRIS_PROXY_WITH_OPENGL,

    /** 暂时关闭 RenderType 路线，只走原有 OpenGL / frame-post 路径。 */
    DISABLED
}

/**
 * RenderEntity 的 vanilla buffer 路线。
 *
 * 这条路径把几何提交给 Minecraft 的 `MultiBufferSource` / `RenderType` 批次，
 * 适合希望被 Iris 当作 entity pass 继续处理的简单模型、贴图面片和发光层。
 * 复杂 FBO、compute、自定义 frame-post 仍然应该走 [WorldPassRenderEntityRenderer]
 * 或 [FramePostRenderEntityRenderer]。
 */
interface RenderTypeBackedRenderEntityRenderer<T : RenderEntity> : RenderEntityRenderer<T> {
    fun renderTypeMode(entity: T): RenderTypeBackedRenderMode {
        return RenderTypeBackedRenderMode.IRIS_FIRST_OPENGL_FALLBACK
    }

    fun renderRenderType(input: RenderTypeRenderInput<T>)
}

/**
 * 复用 [RenderEntityModelRenderer] 的 RenderType 兼容层。
 *
 * renderer 仍然只需要构建 `RenderEntityModel`。本接口会把模型顶点转换为
 * `VertexConsumer` 调用，并提交到每个 primitive 对应的 [RenderType]。
 */
interface RenderTypeBackedRenderEntityModelRenderer<T : RenderEntity> :
    RenderEntityModelRenderer<T>,
    RenderTypeBackedRenderEntityRenderer<T> {
    fun renderTypeForPrimitive(
        input: RenderTypeRenderInput<T>,
        primitive: RenderEntityModelPrimitive
    ): RenderType?

    override fun renderRenderType(input: RenderTypeRenderInput<T>) {
        val entity = input.instance.entity
        val model = buildModel(entity, input.tickDelta)
        RenderTypeRenderEntityModelExecutor.draw(model, input) { primitive ->
            renderTypeForPrimitive(input, primitive)
        }
    }
}

/**
 * 为本地 OpenGL RenderEntity 附加一份 Iris / shaderpack 可见的 RenderType 代理模型。
 *
 * 使用方式：
 * - renderer 继续实现 [WorldPassRenderEntityRenderer] 绘制原本的 Coo shader / FBO 效果。
 * - 再实现本接口，返回一份简化代理模型和对应 RenderType。
 * - Iris shaderpack 启用时 runtime 会额外提交代理模型；未启用时只走原本本地绘制。
 */
interface IrisRenderTypeProxyRenderer<T : RenderEntity> : RenderTypeBackedRenderEntityRenderer<T> {
    override fun renderTypeMode(entity: T): RenderTypeBackedRenderMode {
        return RenderTypeBackedRenderMode.IRIS_PROXY_WITH_OPENGL
    }

    fun buildIrisProxyModel(entity: T, tickDelta: Float): RenderEntityModel

    fun irisProxyRenderType(
        input: RenderTypeRenderInput<T>,
        primitive: RenderEntityModelPrimitive
    ): RenderType?

    override fun renderRenderType(input: RenderTypeRenderInput<T>) {
        val entity = input.instance.entity
        val model = buildIrisProxyModel(entity, input.tickDelta)
        RenderTypeRenderEntityModelExecutor.draw(model, input) { primitive ->
            irisProxyRenderType(input, primitive)
        }
    }
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
 * vanilla buffer 路线的输入。
 *
 * `poseStack` 已经进入实体局部空间；renderer 只需要继续写自己的局部变换和顶点。
 * 使用 `CooParticlesRenderTypes` 创建的 RenderType 会自动走现有 Iris 兼容包装：
 * 自定义 shader 会被标记为不可跳过，entity cutout/emissive 会尝试进入 Iris entity pass。
 */
data class RenderTypeRenderInput<T : RenderEntity>(
    val instance: RenderEntityInstance<T>,
    val tickDelta: Float,
    val viewMatrix: Matrix4f,
    val projMatrix: Matrix4f,
    val poseStack: PoseStack,
    val bufferSource: MultiBufferSource,
    val camera: Camera
)

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
