package cn.coostack.cooparticlesapi.test.options.particle.client

import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import org.joml.Vector3f
import java.util.*

class TestSubGroupClient(uuid: UUID, val bindPlayer: UUID) : ControlableParticleGroup(uuid) {

    override fun loadParticleLocations(): Map<ParticleRelativeData, RelativeLocation> {
        val list = Math3DUtil.getCycloidGraphic(2.0, 2.0, -1, 2, 360, 1.0).onEach { it.y += 6 }
        return list.associateBy {
            withEffect({ ParticleDisplayer.Companion.withSingle(ControlableEndRodEffect(it)) }) {
                color = Vector3f(100 / 255f, 100 / 255f, 255 / 255f)
                this.lifetime = maxTick
            }
        }

    }


    override fun onGroupDisplay() {
        addPreTickAction {
            val bindPlayerEntity = world!!.getPlayerByUUID(bindPlayer) ?: let {
                return@addPreTickAction
            }
            rotateToWithAngle(
                RelativeLocation.of(bindPlayerEntity.forward),
                Math.toRadians(-10.0)
            )
        }
    }
}