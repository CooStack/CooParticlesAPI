package cn.coostack.cooparticlesapi.particles

import net.minecraft.world.phys.Vec3

/**
 * 修改粒子在2个tick之间的移动方式
 * 而不是始终线性移动
 */
fun interface ParticleLerpInterpolator {
    /**
     * @param prev 上一个tick的位置
     * @param current 当前tick的位置
     * @param delta 当前tick的进行进度
     * @return 插值结果
     */
    fun consume(prev: Vec3, current: Vec3, delta: Float): Vec3
}