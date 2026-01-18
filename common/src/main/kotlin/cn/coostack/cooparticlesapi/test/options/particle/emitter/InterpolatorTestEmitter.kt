package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import cn.coostack.cooparticlesapi.extend.*
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorDouble

/**
 * 测试一下粒子插值
 *
 * （假设粒子会有一些其他的变化参数）
 *
 * @constructor
 * TODO
 *
 * @param pos
 * @param world
 */
@CooAutoRegister
class InterpolatorTestEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {
    @CodecField
    var movement = Vec3.ZERO

    @CodecField
    var rotateSpeed = PI / 4

    @CodecField
    var radian = InterpolatorDouble(0.0)

    @CodecField
    var ticking = 0

    init {
        enableInterpolator = true
        emittersInterpolator.setRefiner(10.0)
    }

    override fun doTick() {
        teleportTo(pos + movement)
        radian += rotateSpeed
        if (ticking++ > 50) {
            ticking = 0
            movement *= -1
        }
    }

    /**
     * 防止最开始的lerpProgress是从1开始 (1, 0.00001 -> 0.99999)
     *
     * @param lerpProgress
     * @return
     */
    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        val current = radian.getWithInterpolator(lerpProgress)

        val x = cos(current) * 3.0
        val z = sin(current) * 3.0
        return PointsBuilder.of(listOf(RelativeLocation(x, 0.0, z)))
            .rotateTo(movement)
            .create().map {
                ControlableParticleData().apply {
                    if (lerpProgress == 1f) {
                        color = Math3DUtil.colorOf(255, 0, 0)
                    } else if (lerpProgress == 0f) {
                        color = Math3DUtil.colorOf(0, 255, 0)
                    }
                } to it
            }
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float
    ) {

    }
}