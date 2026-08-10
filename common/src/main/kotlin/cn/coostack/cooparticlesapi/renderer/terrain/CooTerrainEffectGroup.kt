package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState

/**
 * 描述一组共享同一方块 Pipeline 和 uniform 的方块位置。
 *
 * 构建器中的延迟均以调用 [CooTerrainEffectManager.apply] 时的服务端游戏时间为起点。
 * 未设置 [CooTerrainEffectGroupBuilder.duration] 的组会一直保留，直到调用
 * [CooTerrainEffectManager.remove]。
 *
 * 示例：
 * ```kotlin
 * val group = CooTerrainEffectGroup(effectId, pipeline) {
 *     positions(affectedBlocks)
 *     uniform("Strength", 0.8F)
 *     duration(40L)
 * }
 * CooTerrainEffectManager.apply(level, group)
 * ```
 *
 * @property id 组 ID；同一维度内再次应用相同 ID 会替换旧组
 * @property pipeline 负责渲染组内方块的不可变 Pipeline 模板
 * @param block 配置位置、生效延迟、uniform 和持续时间的构建块
 */
class CooTerrainEffectGroup(
    val id: ResourceLocation,
    val pipeline: CooRenderPipeline<BlockState>,
    block: CooTerrainEffectGroupBuilder.() -> Unit
) {
    /** 构建块生成的不可变效果定义，仅由服务端 Manager 读取。 */
    internal val definition = CooTerrainEffectGroupBuilder().apply(block).build()
}
