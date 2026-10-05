package cn.coostack.cooparticlesapi.entities.structure

import cn.coostack.cooparticlesapi.mixin.structure.TorchParticleAccess
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.RedstoneTorchBlock
import net.minecraft.world.level.block.TorchBlock
import net.minecraft.world.level.block.RedstoneWallTorchBlock
import net.minecraft.world.level.block.WallTorchBlock
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * 从静态火把状态解析出的纯显示数据，不负责亮度或任何方块逻辑。
 * @property position 粒子发射点的结构局部坐标，保留墙上火把的朝向
 * @property particle 火把自身配置的火焰类型，或点亮红石火把的红尘
 * @property smoke 是否同时生成烟雾，红石火把不生成烟雾
 * @property jitter 是否使用原版红石火把的随机位置扰动
 */
data class ModelTorchEmission(val position: Vec3, val particle: ParticleOptions,
                                      val smoke: Boolean, val jitter: Boolean) {
    companion object {
        /**
         * 解析火把显示效果，普通发光方块不会因此冒火或冒烟。
         * 示例：`ModelTorchEmission.fromState(state, localPos)`。
         * @param state 快照中的原始方块状态
         * @param pos 该方块在结构中的局部格坐标
         * @return 非火把或熄灭的红石火把返回空值
         */
        fun fromState(state: BlockState, pos: BlockPos): ModelTorchEmission? {
            val block = state.block
            val redstone = block is RedstoneTorchBlock
            if (block !is TorchBlock && !redstone) return null
            if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) return null
            val particle = if (redstone) DustParticleOptions.REDSTONE else (block as TorchParticleAccess).torchParticle
            var position = Vec3(pos.x + 0.5, pos.y + 0.7, pos.z + 0.5)
            if (block is WallTorchBlock || block is RedstoneWallTorchBlock) {
                // 原版墙上火把的顶端向支撑面偏移 0.27 格、上移 0.22 格，不能用方块中心代替。
                val towardWall = state.getValue(BlockStateProperties.HORIZONTAL_FACING).opposite
                position = position.add(towardWall.stepX * 0.27, 0.22, towardWall.stepZ * 0.27)
            }
            return ModelTorchEmission(position, particle, !redstone, redstone)
        }
    }
}
