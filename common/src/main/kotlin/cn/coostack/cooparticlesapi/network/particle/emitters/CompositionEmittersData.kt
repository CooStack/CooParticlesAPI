package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.SerializableData
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import io.netty.buffer.Unpooled
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * SerializableData wrapper for ParticleComposition emitters entry.
 */
class CompositionEmittersData(
    var compositionType: String = "",
    var compositionData: ByteArray = ByteArray(0)
) : SerializableData {
    constructor(composition: ParticleComposition) : this() {
        setComposition(composition)
    }

    companion object {
        @JvmStatic
        val PACKET_CODEC: StreamCodec<RegistryFriendlyByteBuf, CompositionEmittersData> =
            StreamCodec.of(
                { buf, data ->
                    buf.writeUtf(data.compositionType)
                    buf.writeInt(data.compositionData.size)
                    buf.writeBytes(data.compositionData)
                },
                { buf ->
                    val type = buf.readUtf()
                    val size = buf.readInt()
                    val bytes = ByteArray(size)
                    buf.readBytes(bytes)
                    CompositionEmittersData(type, bytes)
                }
            )

        @JvmStatic
        fun fromComposition(composition: ParticleComposition): CompositionEmittersData {
            val buf = FriendlyByteBuf(Unpooled.buffer())
            composition.getCodec().encode(buf, composition)
            val data = ByteArray(buf.readableBytes())
            buf.readBytes(data)
            return CompositionEmittersData(composition::class.java.name, data)
        }
    }

    private var prepared: ParticleComposition? = null

    private fun decodeComposition(): ParticleComposition {
        val codec = ParticleCompositionManager.registeredTypes[compositionType]
            ?: throw IllegalStateException("ParticleComposition codec not registered for type: $compositionType")
        return codec.decode(FriendlyByteBuf(Unpooled.wrappedBuffer(compositionData.copyOf())))
    }

    private fun cloneComposition(composition: ParticleComposition): ParticleComposition {
        val type = composition::class.java
        val ins = runCatching {
            type.getDeclaredConstructor(Vec3::class.java, Level::class.java)
                .apply { isAccessible = true }
                .newInstance(composition.position, composition.world)
        }.getOrNull() ?: type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

        return ins.apply { update(composition) }
    }

    private fun resolveComposition(): ParticleComposition {
        return prepared ?: decodeComposition().also { prepared = it }
    }

    override fun getCodec(): StreamCodec<RegistryFriendlyByteBuf, out SerializableData> {
        return PACKET_CODEC
    }

    override fun clone(): SerializableData {
        val composition = runCatching { cloneComposition(resolveComposition()) }.getOrNull()
        return if (composition != null) {
            CompositionEmittersData(composition)
        } else {
            CompositionEmittersData(compositionType, compositionData.copyOf())
        }
    }

    override fun createControler(
        world: ClientLevel,
        pos: Vec3,
        particleLerpProcess: Float,
        posLerpProcess: Float
    ): Controlable<*> {
        val composition = resolveComposition()
        composition.world = world
        composition.setPositionWithoutToggle(pos)
        prepared = composition
        return composition
    }

    override fun getDisplayer(): ParticleDisplayer {
        val composition = resolveComposition()
        return ParticleDisplayer.withComposition(composition)
    }

    fun setComposition(composition: ParticleComposition): CompositionEmittersData {
        val clone = cloneComposition(composition)
        val data = fromComposition(clone)
        compositionType = data.compositionType
        compositionData = data.compositionData
        prepared = clone
        return this
    }
}
