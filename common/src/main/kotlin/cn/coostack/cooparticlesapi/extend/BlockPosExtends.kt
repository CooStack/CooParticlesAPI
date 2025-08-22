package cn.coostack.cooparticlesapi.extend

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3


fun ofFloored(vec: Vec3): BlockPos {
    return BlockPos(Mth.floor(vec.x), Mth.floor(vec.y), Mth.floor(vec.z))
}
