package cn.coostack.cooparticlesapi.test.particle.client

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroupProvider
import cn.coostack.cooparticlesapi.particles.impl.TestEndRodEffect
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.util.math.Vec3d
import org.joml.Vector3f
import java.util.*

class BarrierSwordGroupClient(uuid: UUID, var targetEntityID: Int?) : ControlableParticleGroup(uuid) {
    private var defaultDirection: Vec3d = Vec3d(0.0, 1.0, 0.0)

    class Provider : ControlableParticleGroupProvider {
        override fun createGroup(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ControlableParticleGroup {
            return BarrierSwordGroupClient(uuid, null).also {
                it.defaultDirection = args["direction"]!!.loadedValue as Vec3d
            }
        }

        override fun changeGroup(group: ControlableParticleGroup, args: Map<String, ParticleControlerDataBuffer<*>>) {
            group as BarrierSwordGroupClient
            if (args.containsKey("target_entity_id")) {
                group.targetEntityID = args["target_entity_id"]!!.loadedValue as Int
            }
        }

    }

    var hilt = 4
    var swordBody = 6
    var swordHeadLen = 3
    var bodySize = 1.0
    var tail = 4
    var step = 0.5

    init {
        axis = RelativeLocation.zAxis()
    }


    override fun loadParticleLocations(): Map<ParticleRelativeData, RelativeLocation> {
        return genSword().associateBy {
            withEffect({ u ->
                ParticleDisplayer.Companion.withSingle(
                    TestEndRodEffect(u)
                )
            }) {
                color = Vector3f(1f, 1f, 1f)
            }
        }
    }

    /**   z
     *   / \ -> h = swordHeadLen 是sword body最上方开始记录
     *   | | -> sword body
     *   |a| a-> bodySize -> -bodySize , bodySize
     *  ----- -> sword hilt * 2 + 1   - >> x
     *    | --> sword tail
     */
    private fun genSword(): List<RelativeLocation> {
        val res = mutableListOf<RelativeLocation>()
        for (i in -hilt..hilt) {
            res.add(RelativeLocation(i * step, 0.0, 0.0))
        }

        for (i in 1..swordBody) {
            res.add(
                RelativeLocation(
                    -bodySize * step, 0.0, step * i
                )
            )
            res.add(
                RelativeLocation(
                    bodySize * step, 0.0, step * i
                )
            )
        }
        for (i in 1..tail) {
            res.add(
                RelativeLocation(
                    0.0, 0.0, -step * i
                )
            )
        }

        val highest = RelativeLocation(0.0, 0.0, step * swordBody + swordHeadLen * step)
        val l = RelativeLocation(-bodySize * step, 0.0, step * swordBody)
        val r = RelativeLocation(+bodySize * step, 0.0, step * swordBody)

        res.addAll(
            Math3DUtil.getLineLocations(
                highest.toVector(), l.toVector(), swordBody
            )
        )
        res.addAll(
            Math3DUtil.getLineLocations(
                highest.toVector(), r.toVector(), swordBody
            )
        )
        return res
    }

    override fun onGroupDisplay() {
        axis = RelativeLocation.yAxis()
        addPreTickAction {
            if (targetEntityID != null) {
                val entity = world!!.getEntityById(targetEntityID!!) ?: return@addPreTickAction
                val direction = origin.relativize(entity.pos)
                rotateParticlesToPoint(RelativeLocation.of(direction))
            } else {
                rotateParticlesToPoint(RelativeLocation.of(defaultDirection))
            }
        }
    }
}