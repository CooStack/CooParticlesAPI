package cn.coostack.cooparticlesapi.particles.impl

import cn.coostack.cooparticlesapi.cparticle.CParticleTextureSource
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureSourceProvider
import cn.coostack.cooparticlesapi.cparticle.textureOfBlock
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

/**
 * 使用 [state] 模型 particle icon 的可控制方块尘效果。
 *
 * Example: GPU emitter 会通过 [cparticleTextureSource] 把它放入方块图集批次。
 * Forbidden: 不要在效果对象中直接查询模型或保存 stitch 后的 UV。
 *
 * @property state 提供 particle icon、方块染色和随机裁剪范围的方块状态
 */
class ControlableFallingDustEffect(controlUUID: UUID, val state: BlockState, faceToPlayer: Boolean = true) :
    ControlableParticleEffect(controlUUID, faceToPlayer), CParticleTextureSourceProvider {
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

    /**
     * 返回与 CPU FallingDust 相同的方块外观来源。
     *
     * Example: 默认配置会应用随机 1/4 裁剪、BlockColors 和 `0.6` 亮度倍率。
     * Forbidden: 此处只描述来源，不解析客户端模型。
     *
     * @return 当前 [state] 对应的通用 BlockState 纹理来源
     */
    override fun cparticleTextureSource(): CParticleTextureSource = textureOfBlock(state)

    override fun clone(): ControlableFallingDustEffect {
        return ControlableFallingDustEffect(
            controlUUID, state, faceToPlayer
        )
    }
}
