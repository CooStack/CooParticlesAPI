package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.minus
import kotlin.math.PI

@CooAutoRegister
class TestGPURotationComposition(pos: Vec3, world: Level?) : AutoParticleComposition(pos, world) {

    @CodecField
    var to = RelativeLocation()
    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        return PointsBuilder()
            .addLine(Vec3.ZERO, Vec3(0.0, 3.0, 0.0), 100)
            .addLine(Vec3(1.5,2.6, 0.0), Vec3(0.0, 5.0, 0.0), 100)
            .addLine(Vec3(-1.5,2.6, 0.0), Vec3(0.0, 5.0, 0.0), 100)
            .addLine(Vec3(0.0,2.6, 1.5), Vec3(0.0, 5.0, 0.0), 100)
            .addLine(Vec3(0.0,2.6, -1.5), Vec3(0.0, 5.0, 0.0), 100)
            .createWithCompositionData {
                CompositionData()
                    .setDisplayerSupplier {
                        ParticleDisplayer.withCParticle(it)
                    }
            }
    }

    override fun onDisplay() {
        addPreTickAction {
            val player = world!!.getNearestPlayer(position.x, position.y, position.z, 32.0) { true }
            val to = player?.position() ?: position
            rotateToPoint((to - position).asRelative())
        }
    }
}