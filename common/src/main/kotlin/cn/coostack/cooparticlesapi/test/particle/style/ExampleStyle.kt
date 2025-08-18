package cn.coostack.cooparticlesapi.test.particle.style

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleProvider
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudEffect
import cn.coostack.cooparticlesapi.test.particle.style.sub.MagicSubStyle
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.client.particle.ParticleRenderType
import java.util.UUID
import kotlin.math.PI

class ExampleStyle(val bindPlayer: UUID, uuid: UUID = UUID.randomUUID()) :
    ParticleGroupStyle(16.0, uuid) {
    class Provider : ParticleStyleProvider {
        override fun createStyle(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ParticleGroupStyle {
            val player = args["bind_player"]!!.loadedValue as UUID
            return ExampleStyle(player, uuid)
        }
    }


    val maxScaleTick = 60
    var scaleTick = 0
    val maxTick = 240
    var current = 0
    var angleSpeed = PI / 72

    init {
        scale = 1.0 / maxScaleTick
    }

    override fun getCurrentFrames(): Map<StyleData, RelativeLocation> {
        val res = mutableMapOf<StyleData, RelativeLocation>().apply {
            putAll(
                PointsBuilder()
                    .addDiscreteCircleXZ(8.0, 720, 10.0)
                    .createWithStyleData {
                        StyleData { ParticleDisplayer.withSingle(ControlableCloudEffect(it)) }
                            .withParticleHandler {
                                colorOfRGB(127, 139, 175)
                                this.scale(1.5f)
                                textureSheet = ParticleRenderType.PARTICLE_SHEET_LIT
                            }
                    })
            putAll(
                PointsBuilder()
                    .addCircle(6.0, 4)
                    .pointsOnEach { it.y -= 12.0 }
                    .addCircle(6.0, 4)
                    .pointsOnEach { it.y += 6.0 }
                    .createWithStyleData {
                        StyleData {
                            ParticleDisplayer.withStyle(
                                MagicSubStyle(it, bindPlayer, 1)
                            )
                        }
                    }
            )
        }
        return res
    }


    fun changeStyles() {
        particles.values.filter { it is MagicSubStyle }
            .forEach {
                it as MagicSubStyle
                it.rotateSpeed = -10
            }
    }

    override fun onDisplay() {
        autoToggle = true
        addPreTickAction {
            if (scaleTick++ >= maxScaleTick) {
                return@addPreTickAction
            }
            scale(scale + 1.0 / maxScaleTick)
        }
        addPreTickAction {
            current++
            if (current >= maxTick) {
                remove()
            }
            val player = world!!.getPlayerByUUID(bindPlayer) ?: return@addPreTickAction
            teleportTo(player.position())
            rotateParticlesAsAxis(angleSpeed)
        }
    }

    override fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>> {
        val res = mutableMapOf<String, ParticleControlerDataBuffer<*>>(
            "current" to ParticleControlerDataBuffers.int(current),
            "angle_speed" to ParticleControlerDataBuffers.double(angleSpeed),
            "bind_player" to ParticleControlerDataBuffers.uuid(bindPlayer),
            "scaleTick" to ParticleControlerDataBuffers.int(scaleTick),
        )
        particles.values.filter {
            it is MagicSubStyle
        }.forEach {
            it as MagicSubStyle
            val t = ParticleControlerDataBuffers.nested(it.writePacketArgs())
            res[it.uuid.toString()] = t
        }
        return res
    }

    override fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
        if (args.containsKey("current")) {
            current = args["current"]!!.loadedValue as Int
        }
        if (args.containsKey("angle_speed")) {
            angleSpeed = args["angle_speed"]!!.loadedValue as Double
        }
        if (args.containsKey("scaleTick")) {
            scaleTick = args["scaleTick"]!!.loadedValue as Int
        }

        args.forEach { key, value ->
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: return@forEach
            val style = particles[uuid] ?: return@forEach
            if (style is MagicSubStyle) {
                style.readPacketArgs(value.loadedValue!! as Map<String, ParticleControlerDataBuffer<*>>)
            }
        }

    }
}