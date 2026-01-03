package cn.coostack.cooparticlesapi.test.options.particle.style

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleProvider
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.RGBImagePointBuilder
import net.minecraft.resources.ResourceLocation
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
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test/magic_axe.png")
        ).step(0.08).scale(1.0).build().forEach {
            val rgba = it.value
            res[StyleData { it ->
                ParticleDisplayer.withSingle(ControlableEndRodEffect(it))
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
            rotateAsAxis(PI / 32)
        }
    }

    override fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>> {
        return mapOf()
    }

    override fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
    }
}