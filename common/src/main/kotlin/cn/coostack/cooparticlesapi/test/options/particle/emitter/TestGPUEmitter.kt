package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.cparticle.CParticleColorCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
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
import kotlin.math.*
import kotlin.random.Random

@CooAutoRegister
class TestGPUEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {
    override fun cparticleForces(): List<CParticleForce> = listOf(
        CParticleForce.ExpDrag(damping = 0.05, minSpeed = 0.0, linear = 0.0)
    )

    private val data1 = SimpleRandomParticleData()

    private val emitter1SizeX = BezierKeyframeFloatCurve(listOf(BezierFloatKeyframe(time = 0.0, value = 0.0, outX = 39.889, outY = 2.45, inX = -33.0, inY = 0.0), BezierFloatKeyframe(time = 1.0, value = 1.0, outX = 33.0, outY = 0.0, inX = -58.172, inY = 2.639)))
    private val emitter1SizeY = BezierKeyframeFloatCurve(listOf(BezierFloatKeyframe(time = 0.0, value = 0.972, outX = 65.097, outY = 0.925, inX = -33.0, inY = 0.0), BezierFloatKeyframe(time = 1.0, value = 1.0, outX = 33.0, outY = 0.0, inX = -44.875, inY = 5.717)))
    private val emitter1Opacity = BezierKeyframeFloatCurve(listOf(BezierFloatKeyframe(time = 0.0, value = 0.0, outX = 81.003, outY = -0.01414, inX = -33.0, inY = 0.0), BezierFloatKeyframe(time = 1.0, value = 0.0, outX = 33.0, outY = 0.0, inX = -69.657, inY = 2.5097)))

    @CodecField
    var template = ControlableCParticleData().apply {
        velocity = Vec3(0.0, 0.12, 0.0)
        uniformSize = false
        weightSize = 1.0f
        heightSize = 1.0f
        visibleRange = 128.0f
        color = Vector3f(1f, 1f, 1f)
        alpha = (100.0 / 100.0).toFloat()
        light = 15
        setTextureSheet(TextureSheetsEnum.ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE)
        cameraOption = ParticleCameraOption.AXIS_BILLBOARD
        axis = Vec3(0.0, 1.0, 0.0)
        roll = (0.0 * PI / 180.0).toFloat()
        speedLimit = 32.0
        sign = 0
        effect = ControlableFlashEffect(uuid)
        updateMode = CParticleUpdateMode.STATIC
        blockCollision = true
        alphaCurve = CParticleCurve.fromFloatCurve(emitter1Opacity)
        scaleXCurve = CParticleCurve.fromFloatCurve(emitter1SizeX)
        scaleYCurve = CParticleCurve.fromFloatCurve(emitter1SizeY)
        colorCurve = CParticleColorCurve.linear(Vector3f(0.996078f, 0.521569f, 0.262745f), Vector3f(1.0f, 0.878431f, 0.439216f))
        randomAgePreTick = false
    }

    init {
        delay = 1
        maxTick = -1
    }

    override fun doTick() {
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        val res = mutableListOf<Pair<ControlableParticleData, RelativeLocation>>()

        // 发射器 #1: 发射器 #1
        if (tick >= 0) {
            data1.apply {
                minAge = 10
                maxAge = 20
                minCount = 10
                maxCount = 20
                minSize = 0.1
                maxSize = 0.2
                minSpeed = 0.2
                maxSpeed = 0.6
                leftColor = Vector3f(0.996078f, 0.521569f, 0.262745f)
                rightColor = Vector3f(1.0f, 0.878431f, 0.439216f)
            }
            res.addAll(
                PointsBuilder()
                    .addWith { List(data1.getRandomCount()) { RelativeLocation(0.0, 0.0, 0.0) } }
                    .createWithoutClone()
                    .map { rel ->
                        val speed = data1.getRandomSpeed()
                        val particleSize = data1.getRandomSize()
                        val velocityJitter = Vec3((Random.nextDouble() * 2.0 - 1.0) * 0.04, (Random.nextDouble() * 2.0 - 1.0) * 0.04, (Random.nextDouble() * 2.0 - 1.0) * 0.04)
                        val baseDir = template.velocity.add(velocityJitter)
                        val velocity = if (baseDir.lengthSqr() < 1e-8) Vec3.ZERO else baseDir.normalize().scale(speed)
                        template.clone().apply {
                            maxAge = data1.getRandomParticleMaxAge()
                            this.color = Vector3f(1f, 1f, 1f)
                            uniformSize = false
                            weightSize = (particleSize * template.weightSize)
                            heightSize = (particleSize * template.heightSize)
                            this.velocity = velocity
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
        controler.addPreTickAction {
            val lifeProgress = if (this.lifetime <= 0) 1.0 else (this.currentAge.toDouble() / this.lifetime.toDouble()).coerceIn(0.0, 1.0)
            when (data.sign) {
                template.sign -> {
                    this.color = data1.getInterpolatedColor(lifeProgress)
                    this.uniformSize = false
                    this.weightSize = (data.weightSize * emitter1SizeX.sample(lifeProgress)).toFloat()
                    this.heightSize = (data.heightSize * emitter1SizeY.sample(lifeProgress)).toFloat()
                    this.particleAlpha = (emitter1Opacity.sample(lifeProgress)).toFloat().coerceIn(0f, 1f)
                }
            }
        }
    }
}