package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import net.minecraft.resources.ResourceLocation

/**
 * 描述一个 RenderEntity 在当前 runtime 中声明的能力集合。
 *
 * 这不是“执行结果”，而是 renderer 告诉管线：
 * - 自己想参与哪些阶段
 * - 需要哪些场景资源
 * - 可能提交哪些 effect type
 * - 是否启用 world pass / effect graph
 *
 * FeatureSet 是 runtime 真正读取的“pipeline 开关”：
 * `RenderEntityInstance` 会用 `localRendererEnabled / effectGraphEnabled` 与 `stages`
 * 来决定 world pass 与 frame-post 收集是否进入；
 * 视觉合成相关的元信息（混合模式、优先级、scene 拷贝/深度依赖）请放在
 * [RenderEntityVisualProfile] 里，二者职责正交。
 */
data class RenderEntityFeatureSet(
    /** 当前实体愿意参与的渲染阶段。 */
    val stages: Set<RenderFrameStage> = setOf(
        RenderFrameStage.WORLD_PASS,
        RenderFrameStage.FRAME_POST
    ),
    /** 当前 renderer 期望管线预先解析或提供的 scene target。 */
    val requestedSceneTargets: Set<ResourceLocation> = emptySet(),
    /** 当前实例可能提交的 effect descriptor 类型集合。 */
    val effectTypes: Set<ResourceLocation> = emptySet(),
    /** 是否启用 `renderLocal(...)` world pass 路径。 */
    val localRendererEnabled: Boolean = true,
    /** 是否启用 effect graph / descriptor 收集路径。 */
    val effectGraphEnabled: Boolean = true
) {
    /**
     * 合并两个 feature set，得到一个更宽松的能力声明。
     *
     * 典型用途是把实体自带能力与某个附加模块的能力组合起来。
     * 布尔值按“只要任一方开启就开启”的规则合并。
     */
    fun merge(other: RenderEntityFeatureSet): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = stages + other.stages,
            requestedSceneTargets = requestedSceneTargets + other.requestedSceneTargets,
            effectTypes = effectTypes + other.effectTypes,
            localRendererEnabled = localRendererEnabled || other.localRendererEnabled,
            effectGraphEnabled = effectGraphEnabled || other.effectGraphEnabled
        )
    }
}

/**
 * `collectRenderContributions(...)` 的标准输入。
 *
 * @property instance 当前实体对应的客户端运行时实例
 * @property frameContext 当前帧上下文，包含 stage、backend 和 scene resource 解析结果
 */
data class RenderContributionInput<T : RenderEntity>(
    val instance: RenderEntityInstance<T>,
    val frameContext: RenderFrameContext
)

/**
 * 向 effect graph 提交渲染贡献的收集器。
 *
 * 提交的是 `RenderEffectDescriptor`，而不是直接执行后处理逻辑。
 */
fun interface RenderContributionCollector {
    /**
     * 提交一个 effect descriptor 到当前帧的贡献收集流程。
     */
    fun submit(effect: RenderEffectDescriptor)
}
