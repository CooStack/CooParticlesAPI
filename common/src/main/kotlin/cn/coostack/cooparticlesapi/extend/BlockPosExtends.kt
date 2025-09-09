package cn.coostack.cooparticlesapi.extend

import jdk.jfr.Description
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

/**
 * @see BlockPos.containing
 */
fun ofFloored(vec: Vec3): BlockPos {
    // 狗市名字
    // 这到底是什么逆天mapping到底为什么会把 vec pos 转换为 blockPos 的名字命名为包含
    // ?
    return BlockPos.containing(vec)
}
