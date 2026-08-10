package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import net.minecraft.world.level.block.state.BlockState

/**
 * 客户端索引中同时保存的效果快照和已配置 Pipeline。
 *
 * @property snapshot 当前组的同步状态
 * @property pipeline 已应用 snapshot uniform 的渲染 Pipeline
 */
internal data class StoredGroup(
    val snapshot: CooTerrainEffectGroupSnapshot,
    val pipeline: CooRenderPipeline<BlockState>
)
