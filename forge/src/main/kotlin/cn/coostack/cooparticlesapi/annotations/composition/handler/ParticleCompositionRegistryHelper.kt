package cn.coostack.cooparticlesapi.annotations.composition.handler

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.annotations.codec.CodecFieldAccessor
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.SequencedParticleComposition
import net.minecraft.network.PacketByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object ParticleCompositionRegistryHelper {
    fun generateCodec(randomInstance: ParticleComposition): CommonStreamCodec<ParticleComposition> {
        return generateCodec(randomInstance::class.java)
    }

    fun generateCodec(type: Class<out ParticleComposition>): CommonStreamCodec<ParticleComposition> {
        val pw = runCatching { type.getConstructor(Vec3::class.java, Level::class.java) }.getOrNull()
        val wp = if (pw == null) runCatching {
            type.getConstructor(
                Level::class.java,
                Vec3::class.java
            )
        }.getOrNull() else null
        val p =
            if (pw == null && wp == null) runCatching { type.getConstructor(Vec3::class.java) }.getOrNull() else null
        val w =
            if (pw == null && wp == null && p == null) runCatching { type.getConstructor(Level::class.java) }.getOrNull() else null
        val empty =
            if (pw == null && wp == null && w == null && p == null) type.getConstructor() else null
        return CommonStreamCodec.of(
            { buf, composition ->
                if (composition is SequencedParticleComposition) {
                    SequencedParticleComposition.encodeBase(composition, buf)
                } else {
                    ParticleComposition.encodeBase(composition, buf)
                }
                val fields = CodecFieldAccessor.fields(type)
                fields.forEach {
                    it.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val codec: CommonStreamCodec<Any> =
                        CodecHelper.codecOf(CodecFieldAccessor.valueType(it)) as CommonStreamCodec<Any>
                    codec.encode(buf, CodecFieldAccessor.get(it, composition))
                }
            }, { buf ->
                val instance = when {
                    pw != null -> pw.newInstance(Vec3.ZERO, null)
                    wp != null -> wp.newInstance(null, Vec3.ZERO)
                    p != null -> p.newInstance(Vec3.ZERO)
                    w != null -> w.newInstance(null)
                    empty != null -> empty.newInstance()
                    else -> throw NullPointerException("All constructors failed")
                }
                instance.apply {
                    if (this is SequencedParticleComposition) {
                        SequencedParticleComposition.decodeBase(this, buf)
                    } else {
                        ParticleComposition.decodeBase(this, buf)
                    }
                    val fields = CodecFieldAccessor.fields(type)
                    fields.forEach {
                        it.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        val codec: CommonStreamCodec<Any> =
                            CodecHelper.codecOf(CodecFieldAccessor.valueType(it)) as CommonStreamCodec<Any>
                        val value = codec.decode(buf)
                        CodecFieldAccessor.set(it, this, value)
                    }
                }
            }
        )
    }
}
