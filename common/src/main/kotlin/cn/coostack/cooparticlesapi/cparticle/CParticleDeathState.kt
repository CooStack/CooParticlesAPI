package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import net.minecraft.world.phys.Vec3

/**
 * 槽位回收前保留的运动状态，与槽位后续复用完全独立。
 *
 * @property position 死亡时的绝对世界坐标
 * @property velocity 死亡时的世界空间速度，单位为方块每 tick
 * @property age 死亡时的 tick 年龄
 * @property reason 回收原因
 */
internal data class CParticleDeathState(
    val position: Vec3,
    val velocity: Vec3,
    val age: Int,
    val reason: RemoveReason,
)
