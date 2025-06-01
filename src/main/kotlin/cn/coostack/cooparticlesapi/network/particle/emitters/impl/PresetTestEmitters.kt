package cn.coostack.cooparticlesapi.network.particle.emitters.impl

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters.Companion.decodeBase
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters.Companion.encodeBase
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

class PresetTestEmitters(pos: Vec3d, world: World?) : ClassParticleEmitters(pos, world) {
    var templateData = ControlableParticleData()

    companion object {
        const val ID = "preset-test-emitters"

        @JvmStatic
        val CODEC = PacketCodec.ofStatic<PacketByteBuf, ParticleEmitters>(
            { buf, data ->
                data as PresetTestEmitters
                encodeBase(data, buf)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
            }, {
                val instance = PresetTestEmitters(Vec3d.ZERO, null)
                decodeBase(instance, it)
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance
            }
        )
    }

    override fun doTick() {
    }

    override fun genParticles(): Map<ControlableParticleData, RelativeLocation> {
        return PointsBuilder()
            .withPreset { romaI(1.0) }
            .pointsOnEach { it.y += 1.0 }
            .withPreset { romaII(1.0) }
            .pointsOnEach { it.y += 1.0 }
            .withPreset { romaIII(1.0) }
            .pointsOnEach { it.y += 1.0 }
            .withPreset { romaIV(1.0) }
            .pointsOnEach { it.y += 1.0 }
            .withPreset { romaV(1.0) }
            .pointsOnEach { it.y += 1.0 }
            .withPreset { romaVI(1.0) }
            .pointsOnEach { it.y += 1.0 }
            .withPreset { romaVII(1.0) }
            .pointsOnEach { it.y += 1.0 }
            .withPreset { romaVIII(1.0) }
            .pointsOnEach { it.y += 1.0 }
            .withPreset { romaIX(1.0) }
            .pointsOnEach { it.y += 1.0 }
            .withPreset { romaX(1.0) }
            .create().associateBy {
                templateData.clone()
            }
    }

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