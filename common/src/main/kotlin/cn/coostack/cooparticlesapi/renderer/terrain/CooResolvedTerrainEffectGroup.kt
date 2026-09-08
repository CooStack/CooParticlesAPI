package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import net.minecraft.world.level.block.state.BlockState

/**
 * 已解析为可直接渲染状态的客户端效果组。
 *
 * @property snapshot 组的维度、位置、生效时间和 uniform 快照
 * @property pipeline 已将快照 uniform 应用到的方块 Pipeline
 */
data class CooResolvedTerrainEffectGroup(
    val snapshot: CooTerrainEffectGroupSnapshot,
    val pipeline: CooRenderPipeline<BlockState>
)
