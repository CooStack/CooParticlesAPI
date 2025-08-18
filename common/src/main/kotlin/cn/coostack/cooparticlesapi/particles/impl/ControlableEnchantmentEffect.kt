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

class ControlableEnchantmentEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    ControlableParticleEffect(controlUUID, faceToPlayer) {
    companion object {
        @JvmStatic
        val codec: MapCodec<ControlableEnchantmentEffect> = RecordCodecBuilder.mapCodec {
            return@mapCodec it.group(
                Codec.BYTE_BUFFER.fieldOf("uuid").forGetter { effect ->
                    val toString = effect.controlUUID.toString()
                    val buffer = Unpooled.buffer()
                    buffer.writeBytes(toString.toByteArray())
                    buffer.nioBuffer()
                }, Codec.BOOL.fieldOf("face_to_player").forGetter { effect ->
                    effect.faceToPlayer
                }
            ).apply(it) { buf, b ->
                ControlableEnchantmentEffect(
                    UUID.fromString(
                        String(buf.array())
                    ), b
                )
            }
        }

        @JvmStatic
        val packetCode: StreamCodec<FriendlyByteBuf, ControlableEnchantmentEffect> = StreamCodec.of(
            { buf, effect ->
                buf.writeUUID(effect.controlUUID)
                buf.writeBoolean(effect.faceToPlayer)
            }, {
                ControlableEnchantmentEffect(it.readUUID(), it.readBoolean())
            }
        )
    }


    override fun getType(): ParticleType<*> {
        return CooModParticles.enchantment.get()
    }

    override fun getPacketCodec(): StreamCodec<FriendlyByteBuf, out ControlableParticleEffect> {
        return packetCode
    }

    override fun clone(): ControlableParticleEffect {
        return ControlableEnchantmentEffect(controlUUID, faceToPlayer)
    }
}