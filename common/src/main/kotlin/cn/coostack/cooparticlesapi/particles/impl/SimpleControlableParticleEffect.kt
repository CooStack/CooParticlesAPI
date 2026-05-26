package cn.coostack.cooparticlesapi.particles.impl

import cn.coostack.cooparticlesapi.particles.ControlableParticleEffect
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.Unpooled
import net.minecraft.core.particles.ParticleType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import java.util.UUID

abstract class SimpleControlableParticleEffect(
    controlUUID: UUID,
    faceToPlayer: Boolean = true,
    private val particleTypeGetter: () -> ParticleType<*>,
    private val effectFactory: (UUID, Boolean) -> ControlableParticleEffect,
    private val packetCodecGetter: () -> StreamCodec<FriendlyByteBuf, out ControlableParticleEffect>
) : ControlableParticleEffect(controlUUID, faceToPlayer) {
    override fun getType(): ParticleType<*> {
        return particleTypeGetter()
    }

    override fun getPacketCodec(): StreamCodec<FriendlyByteBuf, out ControlableParticleEffect> {
        return packetCodecGetter()
    }

    override fun clone(): ControlableParticleEffect {
        return effectFactory(controlUUID, faceToPlayer)
    }
}

internal object SimpleControlableParticleEffectCodecs {
    fun <T : ControlableParticleEffect> mapCodec(factory: (UUID, Boolean) -> T): MapCodec<T> {
        return RecordCodecBuilder.mapCodec { instance ->
            return@mapCodec instance.group(
                Codec.BYTE_BUFFER.fieldOf("uuid").forGetter { effect ->
                    val buffer = Unpooled.buffer()
                    buffer.writeBytes(effect.controlUUID.toString().toByteArray(Charsets.UTF_8))
                    buffer.nioBuffer()
                },
                Codec.BOOL.fieldOf("face_to_player").forGetter { effect ->
                    effect.faceToPlayer
                }
            ).apply(instance) { buf, faceToPlayer ->
                val bytes = ByteArray(buf.remaining())
                buf.get(bytes)
                factory(UUID.fromString(String(bytes, Charsets.UTF_8)), faceToPlayer)
            }
        }
    }

    fun <T : ControlableParticleEffect> packetCodec(factory: (UUID, Boolean) -> T): StreamCodec<FriendlyByteBuf, T> {
        return StreamCodec.of(
            { buf, effect ->
                buf.writeUUID(effect.controlUUID)
                buf.writeBoolean(effect.faceToPlayer)
            },
            { buf -> factory(buf.readUUID(), buf.readBoolean()) }
        )
    }
}

open class SimpleControlableParticleEffectCodecProvider<T : ControlableParticleEffect>(
    factory: (UUID, Boolean) -> T
) {
    val codec: MapCodec<T> = SimpleControlableParticleEffectCodecs.mapCodec(factory)
    val packetCode: StreamCodec<FriendlyByteBuf, T> = SimpleControlableParticleEffectCodecs.packetCodec(factory)
}
