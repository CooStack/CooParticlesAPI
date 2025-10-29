package cn.coostack.cooparticlesapi.particles.impl

import cn.coostack.cooparticlesapi.particles.ControlableParticleEffect
import cn.coostack.cooparticlesapi.particles.CooModParticles
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.Unpooled
import net.minecraft.core.particles.ParticleType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import java.util.UUID

class ControlableEndRodEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    ControlableParticleEffect(controlUUID, faceToPlayer) {
    companion object {
        @JvmStatic
        val codec: MapCodec<ControlableEndRodEffect> = RecordCodecBuilder.mapCodec {
            return@mapCodec it.group(
                Codec.BYTE_BUFFER.fieldOf("uuid").forGetter { effect ->
                    val toString = effect.controlUUID.toString()
                    val buffer = Unpooled.buffer()
                    buffer.writeBytes(toString.toByteArray())
                    buffer.nioBuffer()
                },
                Codec.BOOL.fieldOf("face_to_player").forGetter { effect ->
                    effect.faceToPlayer
                }
            ).apply(it) { buf, faceToPlayer ->
                ControlableEndRodEffect(
                    UUID.fromString(
                        String(buf.array())
                    ), faceToPlayer
                )
            }
        }

        @JvmStatic
        val packetCode: StreamCodec<FriendlyByteBuf, ControlableEndRodEffect> = StreamCodec.of(
            { buf, effect ->
                buf.writeUUID(effect.controlUUID)
                buf.writeBoolean(effect.faceToPlayer)
            }, {
                ControlableEndRodEffect(it.readUUID(), it.readBoolean())
            }
        )
    }

    override fun getType(): ParticleType<*> {
        return CooModParticles.controlableEndRod.get()
    }

    override fun getPacketCodec(): StreamCodec<FriendlyByteBuf, out ControlableParticleEffect> {
        return packetCode
    }

    override fun clone(): ControlableParticleEffect {
        return ControlableEndRodEffect(
            controlUUID, faceToPlayer
        )
    }
}