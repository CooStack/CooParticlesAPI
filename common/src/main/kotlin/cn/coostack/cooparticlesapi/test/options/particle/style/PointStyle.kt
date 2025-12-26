package cn.coostack.cooparticlesapi.test.options.particle.style

import cn.coostack.cooparticlesapi.extend.multiply
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleProvider
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.helper.buffer.ControlableBuffer
import cn.coostack.cooparticlesapi.utils.helper.buffer.ControlableBufferHelper
import java.util.UUID

/**
 * 视频录制素材
 */
class PointStyle(uuid: UUID = UUID.randomUUID()) : ParticleGroupStyle(256.0, uuid) {

    class Provider : ParticleStyleProvider {
        override fun createStyle(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ParticleGroupStyle {
            return PointStyle(uuid)
        }

    }

    @ControlableBuffer("tick")
    var tick = 0

    @ControlableBuffer("bindPlayer")
    var bindPlayer: UUID = UUID.randomUUID()
    override fun getCurrentFrames(): Map<StyleData, RelativeLocation> {
        return mapOf(
            StyleData {
                ParticleDisplayer.withSingle(ControlableEndRodEffect(it))
            } to RelativeLocation()
        )
    }

    override fun onDisplay() {
        addPreTickAction {
            if (tick++ > 120) {
                remove()
            }
            val player = world?.getPlayerByUUID(bindPlayer) ?: return@addPreTickAction
            teleportTo(player.eyePosition.add(player.forward.normalize().multiply(2)))
        }
    }

    override fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>> {
        return ControlableBufferHelper.getPairs(this)
    }

    override fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
        ControlableBufferHelper.setPairs(this, args)
    }
}