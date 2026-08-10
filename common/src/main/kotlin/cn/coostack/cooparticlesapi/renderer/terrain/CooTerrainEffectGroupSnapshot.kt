package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

/**
 * 在服务端和客户端之间同步的效果组完整快照。
 *
 * 时间字段使用对应维度的绝对游戏 tick；客户端据此处理延迟生效和到期，无需逐 tick 发送状态。
 *
 * @property dimension 组所属维度 ID
 * @property id 组 ID，同一维度内唯一
 * @property pipelineId 两端应注册的方块 Pipeline ID
 * @property startedAt 组开始时的绝对游戏 tick
 * @property expiresAt 组到期的绝对游戏 tick；`null` 表示持久组
 * @property activations 键为方块坐标，值为该位置生效的绝对游戏 tick
 * @property uniforms 键为 shader uniform 名称，值为组共享的 uniform 数据
 * @property sequence 用于确定重叠组的绘制优先顺序，数值越大越新
 * @property revision 用于丢弃乱序网络更新的单调版本号
 */
internal data class CooTerrainEffectGroupSnapshot(
    val dimension: ResourceLocation,
    val id: ResourceLocation,
    val pipelineId: ResourceLocation,
    val startedAt: Long,
    val expiresAt: Long?,
    val activations: Map<BlockPos, Long>,
    val uniforms: Map<String, CooUniformValue>,
    val sequence: Long,
    val revision: Long
)
