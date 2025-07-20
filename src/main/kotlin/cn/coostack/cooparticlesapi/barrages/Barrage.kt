package cn.coostack.cooparticlesapi.barrages

import cn.coostack.cooparticlesapi.network.particle.ServerControler
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroup
import net.minecraft.entity.LivingEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import java.util.UUID

interface Barrage {
    var loc: Vec3d
    val world: ServerWorld
    var hitBox: HitBox
    var shooter: LivingEntity?
    var direction: Vec3d
    var lunch: Boolean
    val valid: Boolean
    val options: BarrageOption
    val uuid: UUID

    /**
     * 设置bindControl 会每tick都会设置 loc (teleport)
     * 如果输入的参数是一个Style
     * 那么这个Style必须拥有Provider
     */
    val bindControl: ServerControler<*>

    fun hit(result: BarrageHitResult)

    /**
     * @param result 击中的目标
     */
    fun onHit(result: BarrageHitResult)

    fun noclip(): Boolean

    fun tick()

}