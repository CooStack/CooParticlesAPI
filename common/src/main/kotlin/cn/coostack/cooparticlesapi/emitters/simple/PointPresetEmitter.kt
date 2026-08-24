package cn.coostack.cooparticlesapi.emitters.simple

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.cparticle.CParticleColorCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.network.particle.data.minRangeTo
import cn.coostack.cooparticlesapi.network.particle.emitters.*
import cn.coostack.cooparticlesapi.network.particle.emitters.command.*
import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.*
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.impl.*
import cn.coostack.cooparticlesapi.supports.TextureSheetsEnum
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import cn.coostack.cooparticlesapi.extend.offsetRandomly
import kotlin.math.*
import kotlin.random.Random

/**
 * 预设， Minecraft原版基于服务器发射的所有粒子
 * 可以理解为支持velocity的box发射器
 *
 * 因此，该Emitter可以完美接替服务端发送的粒子行为
 *
 * @constructor
 * @param pos
 * @param world
 */
@CooAutoRegister
class PointPresetEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {

    @CodecField
    var damping = 0.4

    @CodecField
    var velocityRandomOffsets = 0.0 minRangeTo 0.0

    /**
     * 是否启用从left 到right 的颜色生命周期渐变
     */
    @CodecField
    var enableColorGradient = false

    /**
     * 是否启用基于left 到right的随机颜色，同时为true则以生命周期渐变优先
     */
    @CodecField
    var enableRandomColor = false

    @CodecField
    var particleData = SimpleRandomParticleData().apply {
        minAge = 10
        maxAge = 20
        minCount = 6
        maxCount = 24
        minSize = 0.08
        maxSize = 0.18
        minSpeed = 0.2
        maxSpeed = 0.6
        leftColor = Vector3f(1.0f, 1.0f, 1.0f)
        rightColor = Vector3f(1.0f, 1.0f, 1.0f)
    }


    @CodecField
    var template = ControlableCParticleData().apply {
        velocity = Vec3(0.0, 0.1, 0.0)
        uniformSize = false
        weightSize = 1.0f
        heightSize = 1.0f
        visibleRange = 256.0f
        color = Vector3f(1.0f, 1.0f, 1.0f)
        setTextureSheet(TextureSheetsEnum.PARTICLE_SHEET_TRANSLUCENT)
        cameraOption = ParticleCameraOption.BILLBOARD
        speedLimit = 32.0
        sign = 0
        effect = ControlableEndRodEffect(uuid)
        updateMode = CParticleUpdateMode.STATIC
        blockCollision = false
    }

    init {
        maxTick = 1
    }

    override fun cparticleForces(): List<CParticleForce> = listOf(
        CParticleForce.ExpDrag(damping = damping, minSpeed = 0.0, linear = 0.0)
    )

    override fun doTick() {
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        val res = mutableListOf<Pair<ControlableParticleData, RelativeLocation>>()

        // 发射器 #1: 发射器 #1
        if (tick >= 0) {
            res.addAll(
                PointsBuilder()
                    .addPoints(
                        let {
                            ArrayList<RelativeLocation>().apply {
                                repeat(particleData.getRandomCount()) {
                                    this += RelativeLocation()
                                }
                            }
                        }
                    )
                    .createWithoutClone()
                    .map { rel ->
                        val speed = particleData.getRandomSpeed()
                        val particleSize = particleData.getRandomSize()
                        val baseDir = template.velocity
                        val velocity = if (baseDir.lengthSqr() < 1e-8) Vec3.ZERO else baseDir.normalize().scale(speed)
                        template.clone().apply {
                            maxAge = particleData.getRandomParticleMaxAge()
                            if (enableColorGradient) {
                                this.color = Vector3f(1.0f, 1.0f, 1.0f)
                                colorCurve = CParticleColorCurve.linear(
                                    particleData.leftColor, particleData.rightColor
                                )
                            } else if (enableRandomColor) {
                                color = particleData.getRandomColor()
                            }
                            uniformSize = false
                            weightSize = particleSize * template.weightSize
                            heightSize = particleSize * template.heightSize
                            this.velocity = velocity.offsetRandomly(velocityRandomOffsets.random())
                        } to rel
                    }
            )
        }

        return res
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