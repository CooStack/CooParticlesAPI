package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI

@CooAutoRegister
class TestSimpleParticleComposition(position: Vec3, world: Level? = null) : AutoParticleComposition(position, world) {
    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        val spacing = 1.4
        val states = listOf(
            state(),
            state {
                roll = PI / 2
            },
            state {
                cameraOption = ParticleCameraOption.AXIS_BILLBOARD
                axis = Vec3(0.0, -1.0, 0.0)
            },
            state {
                cameraOption = ParticleCameraOption.AXIS_BILLBOARD
                axis = Vec3(0.0, 0.0, 1.0)
            },
            state {
                uniformSize = false
                weightSize = 0.5f
                heightSize = 1.0f
            },
            state {
                cameraOption = ParticleCameraOption.ROTATION
                uniformSize = false
                heightSize = 1.2f
                weightSize = 1.2f
                currentYaw = 0f
                currentPitch = 0f
                currentRoll = 0f
                previewYaw = 0f
                previewPitch = 0f
                previewRoll = 0f
            },
            state {
                cameraOption = ParticleCameraOption.AXIS_BILLBOARD
                axis = Vec3(1.0, 0.0, 0.0)
                uniformSize = false
                weightSize = 0.45f
                heightSize = 1.1f
                roll = PI / 4
            },
            state {
                cameraOption = ParticleCameraOption.ROTATION
                uniformSize = false
                weightSize = 1.0f
                heightSize = 0.6f
                currentYaw = (PI / 4).toFloat()
                currentPitch = (PI / 8).toFloat()
                currentRoll = (PI / 6).toFloat()
                previewYaw = currentYaw
                previewPitch = currentPitch
                previewRoll = currentRoll
            }
        )

        val start = -((states.size - 1) * spacing) / 2.0
        return states.mapIndexed { index, data ->
            data to RelativeLocation(start + spacing * index, 0.0, 0.0)
        }.toMap()
    }

    private fun state(init: cn.coostack.cooparticlesapi.particles.ControlableParticle.() -> Unit = {}): CompositionData {
        return CompositionData().addParticleInstanceInit {
            textureSheet = CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT
            size = 0.8f
            light = 15
            particleAlpha = 1f
            init()
        }
    }

    override fun onDisplay() {
    }
}
