package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4fc
import org.joml.Vector3f

/**
 * 把已固定的重生连接应用到 CPU 预留槽位，与 respawn compute 的坐标转换保持一致。
 *
 * 不求值配置闭包、不申请容量；取消只清除等待标志并交给下一轮统一回收。
 * 示例：全部系统捕获死亡后，以父代世界快照调用本函数，再统一回收父代。
 *
 * @param target 已持有预留后继的存储
 * @param slot 预留后继的槽位
 * @param death 父代死亡时的世界坐标、速度和原因
 * @param targetOrigin 目标系统的双精度原点
 * @param targetInverse 目标系统整组矩阵的逆矩阵，必须可逆
 * @param position 固定世界位置或相对死亡位置的偏移
 * @param relativeToDeath 是否把 [position] 加到父代死亡位置
 * @param inheritVelocity 继承父代世界速度的倍率
 * @param epochTick 激活时目标系统的 tick
 * @param canceled 是否取消该连接及其后继
 */
internal fun activatePreparedCpuRespawn(
    target: CParticleStore,
    slot: Int,
    death: CParticleDeathState,
    targetOrigin: Vec3,
    targetInverse: Matrix4fc,
    position: Vec3,
    relativeToDeath: Boolean,
    inheritVelocity: Float,
    epochTick: Int,
    canceled: Boolean,
) {
    val base = slot * CParticleStore.STRIDE
    val flags = target.data[base + CParticleStore.OFF_FLAGS].toInt()
    if (flags and CParticleInstanceFlags.RESPAWN_WAITING == 0) return
    if (canceled) {
        target.data[base + CParticleStore.OFF_FLAGS] = ((flags and
            (CParticleInstanceFlags.RESPAWN_WAITING or CParticleInstanceFlags.NEWBORN).inv()) or
            CParticleInstanceFlags.RESPAWN_CANCELED).toFloat()
        target.markDirty(slot)
        return
    }
    // 先在世界空间组合偏移，再进入目标局部空间；原点差保留双精度直至最后转换。
    val worldPosition = if (relativeToDeath) death.position.add(position) else position
    val localPosition = targetInverse.transformPosition(Vector3f(
        (worldPosition.x - targetOrigin.x).toFloat(),
        (worldPosition.y - targetOrigin.y).toFloat(),
        (worldPosition.z - targetOrigin.z).toFloat(),
    ))
    // 初始速度与继承速度按 compute 的顺序组合后统一变换，避免缩放被重复应用。
    val velocity = targetInverse.transformDirection(Vector3f(
        target.data[base + CParticleStore.OFF_VEL] + death.velocity.x.toFloat() * inheritVelocity,
        target.data[base + CParticleStore.OFF_VEL + 1] + death.velocity.y.toFloat() * inheritVelocity,
        target.data[base + CParticleStore.OFF_VEL + 2] + death.velocity.z.toFloat() * inheritVelocity,
    ))
    target.activatePreparedCpu(slot, localPosition, velocity, epochTick)
}
