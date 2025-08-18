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
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt

class DefendClassParticleEmitters(var player: UUID, pos: Vec3, world: Level?) : ClassParticleEmitters(pos, world) {
    var templateData = ControlableParticleData()

    companion object {
        const val ID = "defend-class-particle-emitters"

        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, ParticleEmitters>(
            { buf, data ->
                data as DefendClassParticleEmitters
                buf.writeUUID(data.player)
                encodeBase(data, buf)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
            }, {
                val player = it.readUUID()
                val instance = DefendClassParticleEmitters(player, Vec3.ZERO, null)
                decodeBase(instance, it)
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance
            }
        )
    }

    override fun doTick() {
    }

    override fun genParticles(): Map<ControlableParticleData, RelativeLocation> {
        val player = world!!.getPlayerByUUID(player) ?: return mapOf()
        val playerRotation = player.eyePosition.subtract(pos)
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
                        it.setTextureSheet(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
                    }
                }
        )
        return res
    }

    val random = Random(System.currentTimeMillis())
    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: Vec3,
        spawnWorld: Level
    ) {
    }


    override fun getEmittersID(): String {
        return ID
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleEmitters> {
        return CODEC
    }

}