package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudEffect
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.Unpooled
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import java.util.UUID

abstract class ControlableParticleEffect(var controlUUID: UUID, val faceToPlayer: Boolean = true) : ParticleOptions {
    abstract fun getPacketCodec(): StreamCodec<FriendlyByteBuf, out ControlableParticleEffect>
    abstract fun clone(): ControlableParticleEffect
}