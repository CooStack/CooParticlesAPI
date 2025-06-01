package cn.coostack.cooparticlesapi.network.particle.emitters.impl

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.particles.Controlable
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.client.particle.ParticleTextureSheet
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt

class DefendClassParticleEmitters(var player: UUID, pos: Vec3d, world: World?) : ClassParticleEmitters(pos, world) {
    var templateData = ControlableParticleData()

    companion object {
        const val ID = "defend-class-particle-emitters"

        @JvmStatic
        val CODEC = PacketCodec.ofStatic<PacketByteBuf, ParticleEmitters>(
            { buf, data ->
                data as DefendClassParticleEmitters
                buf.writeUuid(data.player)
                encodeBase(data, buf)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
            }, {
                val player = it.readUuid()
                val instance = DefendClassParticleEmitters(player, Vec3d.ZERO, null)
                decodeBase(instance, it)
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance
            }
        )
    }

    override fun doTick() {
    }

    override fun genParticles(): Map<ControlableParticleData, RelativeLocation> {
        val player = world!!.getPlayerByUuid(player) ?: return mapOf()
        val playerRotation = pos.relativize(player.eyePos)
        val res = HashMap<ControlableParticleData, RelativeLocation>()
        res.putAll(
            PointsBuilder()
                .addPolygonInCircle(6, 10, 1.0)
                .rotateTo(playerRotation)
                .create().associateBy { templateData.clone() }
        )
        res.putAll(
            PointsBuilder()
                .addWith {
                    val resList = ArrayList<RelativeLocation>()
                    var step = 1.0
                    while (step > 0) {
                        resList.addAll(
                            getPolygonInCircleLocations(6, (5 * step).roundToInt().coerceAtLeast(1), step)
                        )
                        step -= 0.1
                    }
                    resList
                }
                .rotateTo(playerRotation)
                .create()
                .associateBy {
                    templateData.clone().also {
                        it.alpha = 0.15f
                        it.textureSheet = ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT
                    }
                }
        )
        return res
    }

    val random = Random(System.currentTimeMillis())
    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: Vec3d,
        spawnWorld: World
    ) {
    }


    override fun getEmittersID(): String {
        return ID
    }

    override fun getCodec(): PacketCodec<PacketByteBuf, ParticleEmitters> {
        return CODEC
    }

}