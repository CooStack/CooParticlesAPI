package cn.coostack.cooparticlesapi.test.particle.client

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroupProvider
import cn.coostack.cooparticlesapi.particles.control.group.SequencedParticleGroup
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import org.joml.Vector3f
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID

class SequencedMagicCircleClient(uuid: UUID, val bindPlayer: UUID) : SequencedParticleGroup(uuid) {
    var maxScaleTick = 36
    var current = 0

    class Provider : ControlableParticleGroupProvider {
        override fun createGroup(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ControlableParticleGroup {
            val bindUUID = args["bind_player"]!!.loadedValue as UUID
            return SequencedMagicCircleClient(uuid, bindUUID)
        }

        override fun changeGroup(
            group: ControlableParticleGroup,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ) {
        }
    }

    override fun loadParticleLocationsWithIndex(): SortedMap<SequencedParticleRelativeData, RelativeLocation> {
        val res = TreeMap<SequencedParticleRelativeData, RelativeLocation>()
        val points = Math3DUtil.getCycloidGraphic(3.0, 5.0, -2, 3, 360, .5)
//        val points = Math3DUtil.getCycloidGraphic(1.0,1.0,1,1,360,6.0)
        points.forEachIndexed { index, it ->
            res[withEffect(
                { id -> ParticleDisplayer.withSingle(ControlableEndRodEffect(id)) }, {
                    color = Vector3f(100 / 255f, 100 / 255f, 255 / 255f)
                }, index
            )] = it.also { it.y += 15.0 }
        }
        return res
    }

    override fun beforeDisplay(locations: SortedMap<SequencedParticleRelativeData, RelativeLocation>) {
        super.beforeDisplay(locations)
        scale = 1.0 / maxScaleTick
    }

    var toggle = false
    override fun onGroupDisplay() {
        addPreTickAction {
            if (current < maxScaleTick && !toggle) {
                current++
                scale(scale + 1.0 / maxScaleTick)
            } else if (current < maxScaleTick) {
                current++
                scale(scale - 1.0 / maxScaleTick)
            } else {
                toggle = !toggle
                current = 0
            }
            rotateParticlesAsAxis(Math.toRadians(10.0))
            val player = world!!.getPlayerByUUID(bindPlayer) ?: return@addPreTickAction
            val dir = player.forward
            rotateParticlesToPoint(RelativeLocation.of(dir))
            teleportTo(player.eyePosition)
        }
    }
}