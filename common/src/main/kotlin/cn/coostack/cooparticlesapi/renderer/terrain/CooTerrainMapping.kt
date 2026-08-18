package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState

/**
 * 不可变、可跨世界复用的 terrain mapping 模板。
 *
 * 模板不持有运行时位置集合；位置快照和生命周期均属于 [CooTerrainMappingInstance]。
 */
data class CooTerrainMapping(
    val id: ResourceLocation,
    val pipeline: CooRenderPipeline<BlockState>,
    val regionType: CooTerrainMappingRegionType,
    val defaults: CooTerrainMappingDefaults = CooTerrainMappingDefaults()
)
