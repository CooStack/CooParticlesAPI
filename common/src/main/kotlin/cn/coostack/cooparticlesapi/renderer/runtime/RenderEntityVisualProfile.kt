package cn.coostack.cooparticlesapi.renderer.runtime

import kotlin.math.max

/**
 * 本地合成时的混合模式。
 */
enum class CompositeMode {
    /** 使用新结果直接覆盖目标内容。 */
    REPLACE,
    /** 使用 alpha 混合叠加。 */
    ALPHA,
    /** 使用加色混合，适合 glow、能量体等效果。 */
    ADDITIVE
}

/**
 * RenderEntity 的视觉画像配置。
 *
 * 这个对象不是实体状态本身，而是 renderer 根据实体状态推导出的“视觉需求快照”。
 *
 * 与 [RenderEntityFeatureSet] 的关系：
 * - `RenderEntityFeatureSet` 是“管线层声明”：它直接决定 `RenderEntityInstance.renderLocal`
 *   是否进入 world pass、`collectRenderContributions` 是否被调用，
 *   以及当前实例希望管线提前解析哪些 scene target。这是真正会被 runtime 读取的开关。
 * - `RenderEntityVisualProfile` 是“视觉合成快照”：用于描述外部合成希望使用的
 *   混合模式 / 优先级 / scene 颜色或深度依赖等元信息，
 *   主要面向 debug overlay、profile 工具以及未来的本地合成扩展。
 *
 * 因此 renderer 通常会同时声明两者：FeatureSet 决定要不要执行，VisualProfile 提供执行时的视觉提示。
 */
data class RenderEntityVisualProfile(
    /** 当前实例希望使用的最终合成模式。 */
    val compositeMode: CompositeMode = CompositeMode.REPLACE,
    /** 是否需要读取 scene color copy。 */
    val needsSceneColorCopy: Boolean = false,
    /** 是否需要读取场景深度。 */
    val needsSceneDepth: Boolean = false,
    /** 是否启用本地 effect chain。 */
    val localChainEnabled: Boolean = false,
    /** 是否启用旧版 frame effect 语义标记。 */
    val frameEffectsEnabled: Boolean = false,
    /** 渲染优先级，值越大越靠后。 */
    val renderPriority: Int = 0
) {
    /**
     * 合并两份视觉画像。
     *
     * 典型用途是把基础画像和某个附加效果画像叠加成一个最终需求描述。
     */
    fun merge(other: RenderEntityVisualProfile): RenderEntityVisualProfile {
        return RenderEntityVisualProfile(
            compositeMode = mergeCompositeMode(other.compositeMode),
            needsSceneColorCopy = needsSceneColorCopy || other.needsSceneColorCopy,
            needsSceneDepth = needsSceneDepth || other.needsSceneDepth,
            localChainEnabled = localChainEnabled || other.localChainEnabled,
            frameEffectsEnabled = frameEffectsEnabled || other.frameEffectsEnabled,
            renderPriority = max(renderPriority, other.renderPriority)
        )
    }

    private fun mergeCompositeMode(other: CompositeMode): CompositeMode {
        return when {
            compositeMode == CompositeMode.ADDITIVE || other == CompositeMode.ADDITIVE -> CompositeMode.ADDITIVE
            compositeMode == CompositeMode.ALPHA || other == CompositeMode.ALPHA -> CompositeMode.ALPHA
            else -> CompositeMode.REPLACE
        }
    }
}
