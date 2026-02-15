package cn.coostack.cooparticlesapi.particles.impl

import cn.coostack.cooparticlesapi.particles.ControlableParticleEffect
import cn.coostack.cooparticlesapi.particles.CooModParticles
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.Unpooled
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import java.util.*

class ControlableFallingDustEffect(controlUUID: UUID, val state: BlockState, faceToPlayer: Boolean = true) :
    ControlableParticleEffect(controlUUID, faceToPlayer) {
    companion object {
        @JvmStatic
        val BLOCK_STATE_CODEC = Codec
            .withAlternative(
                BlockState.CODEC,
                BuiltInRegistries.BLOCK.byNameCodec(),
                Block::defaultBlockState
            )

        @JvmStatic
        val codec: MapCodec<ControlableFallingDustEffect> = RecordCodecBuilder.mapCodec {
            return@mapCodec it.group(
                Codec.BYTE_BUFFER.fieldOf("uuid").forGetter { effect ->
                    val toString = effect.controlUUID.toString()
                    val buffer = Unpooled.buffer()
                    buffer.writeBytes(toString.toByteArray())
                    buffer.nioBuffer()
                },
                Codec.BOOL.fieldOf("face_to_player").forGetter { effect ->
                    effect.faceToPlayer
                },
                BLOCK_STATE_CODEC.fieldOf("state").forGetter { effect -> effect.state }
            ).apply(it) { buf, faceToPlayer, state ->
                ControlableFallingDustEffect(
                    UUID.fromString(
                        String(buf.array())
                    ), state, faceToPlayer
                )
            }
        }

        @JvmStatic
        val packetCode: StreamCodec<FriendlyByteBuf, ControlableFallingDustEffect> = StreamCodec.of(
            { buf, effect ->
                buf.writeUUID(effect.controlUUID)
                buf.writeBoolean(effect.faceToPlayer)
                ByteBufCodecs.idMapper<BlockState>(Block.BLOCK_STATE_REGISTRY)
                    .encode(buf, effect.state)
            }, {
                val uuid = it.readUUID()
                val faceTo = it.readBoolean()
                val state = ByteBufCodecs.idMapper<BlockState>(Block.BLOCK_STATE_REGISTRY).decode(
                    it
                )
                ControlableFallingDustEffect(uuid, state, faceTo)
            }
        )
    }

    override fun getType(): ParticleType<*> {
        return CooModParticles.controlableFallingDust.get()
    }

    override fun getPacketCodec(): StreamCodec<FriendlyByteBuf, out ControlableFallingDustEffect> {
        return packetCode
    }

    override fun clone(): ControlableFallingDustEffect {
        return ControlableFallingDustEffect(
            controlUUID, state, faceToPlayer
        )
    }
}