package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleProvider
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import cn.coostack.cooparticlesapi.utils.helper.buffer.ControlableBuffer
import cn.coostack.cooparticlesapi.utils.helper.buffer.ControlableBufferHelper
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.PI

class TestDisplayerStyle(uuid: UUID = UUID.randomUUID()) :
    ParticleGroupStyle(256.0, uuid) {
    class Provider : ParticleStyleProvider {
        override fun createStyle(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ParticleGroupStyle {
            return TestDisplayerStyle(uuid)
        }
    }

    @ControlableBuffer("age")
    var tick = 0

    override fun getCurrentFrames(): Map<StyleData, RelativeLocation> {
        return PointsBuilder()
            .addLine(Vec3.ZERO, Vec3(0.0, 50.0, 0.0), 10)
            .addCircle(10.0, 8)
            .createWithStyleData {
                StyleDataBuilder()
                    .displayer {
                        ParticleDisplayer.withDisplayEntity(
                            TestBlockDisplayEntity(Vec3.ZERO, null)
                                .apply {
                                    this.controlUUID = it
                                }
                        )
                    }
                    .build()
            }
    }

    override fun onDisplay() {
        addPreTickAction {
            if (tick++ > 1000) {
                remove()
            }
            rotateAsAxis(PI / 64)
        }
    }

    override fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>> {
        return ControlableBufferHelper.getPairs(this)
    }

    override fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
        ControlableBufferHelper.setPairs(this, args)
    }
}