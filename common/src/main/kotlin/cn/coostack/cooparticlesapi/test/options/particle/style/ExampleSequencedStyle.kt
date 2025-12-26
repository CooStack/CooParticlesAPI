package cn.coostack.cooparticlesapi.test.options.particle.style

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleProvider
import cn.coostack.cooparticlesapi.network.particle.style.SequencedParticleStyle
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudEffect
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import java.util.SortedMap
import java.util.UUID

class ExampleSequencedStyle(val bindPlayerUUID: UUID, uuid: UUID = UUID.randomUUID()) :
    SequencedParticleStyle(64.0, uuid) {

    class Provider : ParticleStyleProvider {
        override fun createStyle(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ParticleGroupStyle {
            val player = args["bind_player"]!!.loadedValue as UUID
            return ExampleSequencedStyle(player, uuid)
        }

    }

    var count = 120
    override fun getParticlesCount(): Int {
        return count
    }

    override fun getCurrentFramesSequenced(): SortedMap<SortedStyleData, RelativeLocation> {
        return PointsBuilder()
            .addCircle(2.0, 120)
            .createWithSequencedStyleData { rl, order ->
                SortedStyleData({
                    ParticleDisplayer.withSingle(
                        ControlableCloudEffect(it)
                    )
                }, order)
            }
    }

    override fun writePacketArgsSequenced(): Map<String, ParticleControlerDataBuffer<*>> {
        return mapOf(
            "bind_player" to ParticleControlerDataBuffers.uuid(bindPlayerUUID),
            "current" to ParticleControlerDataBuffers.int(current),
            "reverse" to ParticleControlerDataBuffers.boolean(reverse)
        )
    }

    override fun readPacketArgsSequenced(args: Map<String, ParticleControlerDataBuffer<*>>) {
        args["current"]?.let {
            current = it.loadedValue as Int
            changeParticlesStatus((0..current).toList().toIntArray(), true)
        }
    }

    var current = 0
    val max = 40
    var reverse = false
    override fun onDisplay() {
        addPreTickAction {
            if (reverse) {
                current--
            } else {
                current++
            }
            if (current > max) {
                current = max
                reverse = true
            }
            if (current < 0) {
                current = 0
                reverse = false
            }
            val player = world!!.getPlayerByUUID(bindPlayerUUID) ?: return@addPreTickAction
            teleportTo(player.eyePosition)
            rotateParticlesToPoint(RelativeLocation.of(player.forward))
            if (!reverse) {
                addMultiple(3)
            } else {
                removeMultiple(3)
            }
        }
    }


}