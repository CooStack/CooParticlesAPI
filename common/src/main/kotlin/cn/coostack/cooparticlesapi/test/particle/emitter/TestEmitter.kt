package cn.coostack.cooparticlesapi.test.particle.emitter

import cn.coostack.cooparticlesapi.extend.multiply
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.interpolator.particle.DirectParticleInterpolator
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TestEmitter(pos: Vec3, world: Level?) : ClassParticleEmitters(pos, world) {
    var templateData = ControlableParticleData()
    var emitterMoveDirection = Vec3.ZERO
    val particleVelocity = DirectParticleInterpolator()
        .setRefiner(5.0)
    var particleMoveDirection: Vec3 = Vec3.ZERO
    var particleRotateX: Double = 0.0
    var lastParticleRotateX: Double = 0.0

    companion object {
        const val ID = "test-particle-emitters"

        @JvmStatic
        val CODEC: StreamCodec<FriendlyByteBuf, ParticleEmitters> = StreamCodec.of<FriendlyByteBuf, ParticleEmitters>(
            { buf, data ->
                data as TestEmitter
                encodeBase(data, buf)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
                buf.writeVec3(data.emitterMoveDirection)
                buf.writeVec3(data.particleMoveDirection)
                buf.writeDouble(data.particleRotateX)
            }, {
                val instance = TestEmitter(Vec3.ZERO, null)
                decodeBase(instance, it)
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance.emitterMoveDirection = it.readVec3()
                instance.particleMoveDirection = it.readVec3()
                instance.particleRotateX = it.readDouble()
                instance
            }
        )
    }


    override fun update(emitters: ParticleEmitters) {
        super.update(emitters)
        if (emitters !is TestEmitter) {
            return
        }

        this.emitterMoveDirection = emitters.emitterMoveDirection
        this.particleMoveDirection = emitters.particleMoveDirection
        this.particleRotateX = emitters.particleRotateX

    }

    override fun doTick() {
        // 做一个速度渐变
        pos = pos.add(emitterMoveDirection)
        lastParticleRotateX = particleRotateX
        particleRotateX += PI / 4
    }

    override fun doSubtick(current: Vec3) {
        particleVelocity.putParticleArgs(
            current, particleMoveDirection, particleMoveDirection.length()
        )
    }


    val random = Random(System.currentTimeMillis())
    override fun genParticles(): List<Pair<ControlableParticleData, RelativeLocation>> {
        return particleVelocity.getRefinedResult()
            .map {
                templateData.clone().also { it ->
                    it.velocity = particleMoveDirection
                } to RelativeLocation()
            }
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float,
    ) {
        data.setTextureSheet(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
        data.size = 1f
        data.color = Vector3f(0f, 99 / 255f, 204 / 255f)
        data.alpha = 0f
        val delta = GraphMathHelper.lerp(particleLerpProgress, particleRotateX, lastParticleRotateX)
        val rotate = RelativeLocation(cos(delta), 0.0, sin(delta))
        Math3DUtil.rotatePointsToPoint(
            listOf(rotate),
            RelativeLocation.of(particleMoveDirection),
            RelativeLocation.yAxis(),
        )
        data.velocity = rotate.normalize().multiply(0.3).toVector() + particleMoveDirection
        spawnPos.add(
            RelativeLocation.of(data.velocity.multiply(particleLerpProgress.toDouble()))
        )
        controler.addPreTickAction {
            updatePhysics(pos, data)
            // 颜色插值
            val p1 = this.lifetime
            val precent = this.currentAge.toFloat() / p1
            this.particleAlpha = GraphMathHelper
                .lerp(currentAge * 2f / lifetime, 0f, 1f)
            color = with(GraphMathHelper) {
                lerp(
                    precent, Vector3f(1f, 145 / 255f, 189 / 255f), Vector3f(1f, 247 / 255f, 191 / 255f)
                ) * step(0.5f, precent) + lerp(
                    precent, Vector3f(0f, 99 / 255f, 204 / 255f), Vector3f(1f, 247 / 255f, 191 / 255f)
                ) * step(-0.5f, -precent)
            }
            size = with(GraphMathHelper) {
                lerp(precent, 1f, 0.1f)
            }
        }
    }

    override fun getEmittersID(): String {
        return ID
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleEmitters> {
        return CODEC
    }
}