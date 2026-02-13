package cn.coostack.cooparticlesapi.barrages

import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroup
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.UUID

interface Barrage {
    var loc: Vec3
    val world: ServerLevel
    var hitBox: HitBox
    var shooter: LivingEntity?
    var direction: Vec3
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