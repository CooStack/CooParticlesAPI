package cn.coostack.cooparticlesapi.extend

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * named by Fabric Yarn mapping
 * @see BlockPos.containing
 */
fun ofFloored(vec: Vec3): BlockPos {
    // 狗市名字
    // 这到底是什么逆天mapping到底为什么会把 vec pos 转换为 blockPos 的名字命名为包含
    // ?
    return BlockPos.containing(vec)
}
