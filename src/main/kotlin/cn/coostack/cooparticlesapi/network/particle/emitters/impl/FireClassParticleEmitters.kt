package cn.coostack.cooparticlesapi.network.particle.emitters.impl

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

class FireClassParticleEmitters(var player: UUID, pos: Vec3d, world: World?) : ClassParticleEmitters(pos, world) {
    var templateData = ControlableParticleData()
    var fireSize = 0.5
    var fireForce = 0.6

    init {
        airDensity = PhysicConstant.SEA_AIR_DENSITY
//        mass = 1000.0
        wind = GlobalWindDirection(
            Vec3d(0.0, fireForce * 30, 0.0)
        )
            .loadEmitters(this)
    }

    companion object {
        const val ID = "fire-class-particle-emitters"

        @JvmStatic
        val CODEC = PacketCodec.ofStatic<RegistryByteBuf, ParticleEmitters>(
            { buf, data ->
                data as FireClassParticleEmitters
                buf.writeUuid(data.player)
                encodeBase(data, buf)
                buf.writeDouble(data.fireSize)
                buf.writeDouble(data.fireForce)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
            }, {
                val player = it.readUuid()
                val instance = FireClassParticleEmitters(player, Vec3d.ZERO, null)
                decodeBase(instance, it)
                instance.fireSize = it.readDouble()
                instance.fireForce = it.readDouble()
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance
            }
        )
    }

    override fun doTick() {
//        val player = world!!.getPlayerByUuid(player) ?: return
//        pos = player.eyePos
//        val size = wind.direction.length()
//        wind.direction = player.rotationVector.normalize().multiply(size)
    }

    override fun genParticles(): Map<ControlableParticleData, RelativeLocation> {
        val velocityList = PointsBuilder()
            .addRoundShape(fireSize, 0.25, 10, (120 * fireSize).roundToInt())
            .rotateTo(Vec3d(0.0, 0.0, 1.0))
            .pointsOnEach { it.y += 6.0 }
//            .rotateTo(
//                world!!.getPlayerByUuid(player)!!.rotationVector
//            )
            .create()
        val res = HashMap<ControlableParticleData, RelativeLocation>()
        val random = Random(System.currentTimeMillis())
        // 0.0 - 2.0
        val step = 1.0
        var current = step
        for (i in 0 until 60) {
            val it = velocityList.random()
            res[templateData.clone().apply {
                this.velocity = it.normalize().multiply(fireForce).toVector()
            }] = RelativeLocation(0.0, current, 0.0)
        }
        return res
    }

    val random = Random(System.currentTimeMillis())
    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: Vec3d,
        spawnWorld: World
    ) {
        data.velocity = data.velocity.add(
            Vec3d(
                random.nextDouble(-fireForce * 10, fireForce * 10),
                random.nextDouble(-10.0, 10.0),
                random.nextDouble(-fireForce * 10, fireForce * 10)
            ).normalize().multiply(0.125)
        )
//        data.alpha = 0f
        data.color = Math3DUtil.colorOf(
            random.nextInt(240, 255),
            random.nextInt(160, 180),
            random.nextInt(40, 80),
        )
        val alphaBezier = Math3DUtil.generateBezierCurve(
            RelativeLocation(data.maxAge.toDouble(), 0.0, 0.0),
            RelativeLocation(0.0, 1.0, 0.0),
            RelativeLocation(-data.maxAge.toDouble(), 1.0, 0.0),
            data.maxAge
        )
        val size = alphaBezier.size
        val maxY = random.nextDouble(0.6, 0.8)
        controler.addPreTickAction {
//            updatePhysics(controler.particle.pos, data)
            data.velocity = data.velocity.add(0.0, 0.05, 0.0)
            if (data.velocity.y >= maxY) {
                data.velocity = Vec3d(data.velocity.x, maxY, data.velocity.z)
            }
            this.size = 0.3f
//            color.x = ((color.x * 255 + currentAge * 10).coerceIn(0.0f, 255.0f))
//            color.y = ((color.y * 255 - currentAge * 10).coerceIn(0.0f, 255.0f) / 255f)
//            color.z = ((color.z * 255 - currentAge * 10).coerceIn(0.0f, 255.0f) / 255f)
            colorOfRGB(
                (color.x * 255).coerceIn(0.0f, 255.0f).roundToInt(),
                (color.y * 255 - currentAge * 1).coerceIn(0.0f, 255.0f).roundToInt(),
                (color.z * 255 - currentAge * 1).coerceIn(0.0f, 255.0f).roundToInt()
            )
//            val index = currentAge.coerceIn(0, size - 1)
//            particleAlpha = alphaBezier[index].y.toFloat()
        }
    }


    override fun getEmittersID(): String {
        return ID
    }

    override fun getCodec(): PacketCodec<RegistryByteBuf, ParticleEmitters> {
        return CODEC
    }
}