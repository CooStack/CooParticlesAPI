package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.PI

@CooAutoRegister
class DynamicCParticleComposition(position: Vec3, world: Level? = null) :
    AutoParticleComposition(position, world) {

    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        val system = CParticleSystemManager.getOrCreateSystem(
            "composition/dynamic/$controlUUID",
            PARTICLE_COUNT,
            LAYER,
            CParticleSystemMode.SCRIPTED,
            autoReleaseWhenEmpty = true,
        ).apply {
            setOriginIfEmpty(position)
            visibleRange = this@DynamicCParticleComposition.visibleRange
            alphaCurve = CParticleCurve.of(
                0f to 0f,
                0.5f to 1f,
                1f to 0f,
            )
            curveCycleTicks = ALPHA_CYCLE_TICKS
            colorCycleTicks = COLOR_CYCLE_TICKS
            colorCycleSpatialScale = 1f
        }

        val first = PointsBuilder()
            .addPolygonInCircle(POLYGON_SIDES, POINTS_PER_EDGE, RADIUS)
        val second = PointsBuilder()
            .addPolygonInCircle(POLYGON_SIDES, POINTS_PER_EDGE, RADIUS)
            .rotateAsAxis(PI / POLYGON_SIDES, RelativeLocation.yAxis())

        return PointsBuilder()
            .addBuilder(RelativeLocation(), first)
            .addBuilder(RelativeLocation(), second)
            .createWithCompositionData {
                CompositionData()
                    .setDisplayerSupplier { uuid ->
                        ParticleDisplayer.withCParticle(uuid, LAYER, system)
                    }
                    .addCParticleInstanceInit {
                        size = PARTICLE_SIZE
                        color = Vector3f(1f, 1f, 1f)
                        alpha = 1f
                        light = 15
                        maxAge = PARTICLE_MAX_AGE
                    }
            }
    }

    override fun onDisplay() = Unit

    companion object {
        const val PARTICLE_COUNT = 65536
        private const val POLYGON_SIDES = 8
        private const val POINTS_PER_EDGE = PARTICLE_COUNT / POLYGON_SIDES / 2
        private const val RADIUS = 5.0
        private const val PARTICLE_SIZE = 0.02f
        private const val PARTICLE_MAX_AGE = 72000
        private const val ALPHA_CYCLE_TICKS = 80f
        private const val COLOR_CYCLE_TICKS = 120f
        private val LAYER = CParticleRenderLayer.TRANSLUCENT
    }
}
