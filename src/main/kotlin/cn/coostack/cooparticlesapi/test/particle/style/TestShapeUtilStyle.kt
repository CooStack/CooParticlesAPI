package cn.coostack.cooparticlesapi.test.particle.style

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleProvider
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.impl.TestEndRodEffect
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.FourierSeriesBuilder
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import cn.coostack.cooparticlesapi.utils.builder.RGBImagePointBuilder
import net.minecraft.util.Identifier
import java.util.UUID
import kotlin.math.PI

class TestShapeUtilStyle(uuid: UUID = UUID.randomUUID()) :
    ParticleGroupStyle(256.0, uuid) {


    class Provider : ParticleStyleProvider {
        override fun createStyle(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ParticleGroupStyle {
            return TestShapeUtilStyle(uuid)
        }
    }

    override fun getCurrentFrames(): Map<StyleData, RelativeLocation> {
        val res = HashMap<StyleData, RelativeLocation>()

        RGBImagePointBuilder(
            Identifier.of(CooParticleAPI.MOD_ID, "test/magic_axe.png")
        ).step(0.08).scale(1.0).build().forEach {
            val rgba = it.value
            res[StyleData { it ->
                ParticleDisplayer.withSingle(TestEndRodEffect(it))
            }.withParticleHandler {
                colorOfRGBA(rgba)
                size = 0.1f
            }] = it.key
        }

        return res
    }

    override fun beforeDisplay(styles: Map<StyleData, RelativeLocation>) {
        preRotateAsAxis(styles, axis, PI / 4)
        styles.onEach {
            it.value.z -= 2.0
        }
    }

    override fun onDisplay() {
//        axis = RelativeLocation(0.0, 0.0, -1.0)
        addPreTickAction {
            rotateParticlesAsAxis(PI / 32)
        }
    }

    override fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>> {
        return mapOf()
    }

    override fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
    }
}