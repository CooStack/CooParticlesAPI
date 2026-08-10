package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.core.BlockPos

/**
 * 服务端保存前的效果组不可变定义。
 *
 * @property activationOffsets 键为方块坐标，值为相对组开始时间的延迟 tick
 * @property uniforms 键为 shader uniform 名称，值为对应的 uniform 数据
 * @property durationTicks 组持续 tick 数；`null` 表示不自动到期
 */
internal data class CooTerrainEffectGroupDefinition(
    val activationOffsets: Map<BlockPos, Long>,
    val uniforms: Map<String, CooUniformValue>,
    val durationTicks: Long?
)
