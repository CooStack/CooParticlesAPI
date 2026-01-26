package cn.coostack.cooparticlesapi.barrages

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.state.BlockState

class BarrageHitResult {
    var hitBlockState: BlockState? = null

    val hitBlocks = ArrayList<BlockPos>()

    val entities = ArrayList<LivingEntity>()

    val barrages = ArrayList<Barrage>()

}