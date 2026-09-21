package cn.coostack.cooparticlesapi.annotations.emitter.handle

import cn.coostack.cooparticlesapi.annotations.codec.CodecFieldAccessor
import cn.coostack.cooparticlesapi.annotations.codec.ForgeCodecHelper
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.TransformableCParticleEmitter
import net.minecraft.network.PacketByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object ParticleEmittersRegistryHelper {
    fun updateEmitter(current: ClassParticleEmitters, other: ClassParticleEmitters) {
        if (current.getEmittersID() != other.getEmittersID()) return
        ForgeCodecHelper.updateFields(current, other)
    }

    fun updateEmitter(current: ClassEmitters, other: ClassEmitters) {
        if (current.getEmittersID() != other.getEmittersID()) return
        ForgeCodecHelper.updateFields(current, other)
    }

    fun updateEmitter(current: TransformableCParticleEmitter, other: TransformableCParticleEmitter) {
        if (current.getEmittersID() != other.getEmittersID()) return
        ForgeCodecHelper.updateFields(current, other)
    }

    fun generateCodec(randomInstance: ClassParticleEmitters): CommonStreamCodec<ParticleEmitters> {
        return generateClassParticleCodec(randomInstance::class.java)
    }

    fun generateClassParticleCodec(type: Class<out ClassParticleEmitters>): CommonStreamCodec<ParticleEmitters> {
        val constructor = type.getConstructor(Vec3::class.java, Level::class.java)
        return CommonStreamCodec.of(
            { buf, emitter ->
                emitter as ClassParticleEmitters
                ClassParticleEmitters.encodeBase(emitter, buf)
                encodeFields(type, emitter, buf)
            },
            { buf ->
                constructor.newInstance(Vec3.ZERO, null).apply {
                    ClassParticleEmitters.decodeBase(this, buf)
                    decodeFields(type, this, buf)
                }
            }
        )
    }

    fun generateCodec(randomInstance: TransformableCParticleEmitter): CommonStreamCodec<ParticleEmitters> {
        return generateTransformableCParticleEmitterCodec(randomInstance::class.java)
    }

    fun generateTransformableCParticleEmitterCodec(
        type: Class<out TransformableCParticleEmitter>,
    ): CommonStreamCodec<ParticleEmitters> {
        val constructor = type.getConstructor(Vec3::class.java, Level::class.java)
        return CommonStreamCodec.of(
            { buf, emitter ->
                emitter as TransformableCParticleEmitter
                TransformableCParticleEmitter.encodeBase(emitter, buf)
                encodeFields(type, emitter, buf)
            },
            { buf ->
                constructor.newInstance(Vec3.ZERO, null).apply {
                    TransformableCParticleEmitter.decodeBase(this, buf)
                    decodeFields(type, this, buf)
                }
            },
        )
    }

    fun generateCodec(randomInstance: ClassEmitters): CommonStreamCodec<ParticleEmitters> {
        return generateClassEmittersCodec(randomInstance::class.java)
    }

    fun generateClassEmittersCodec(type: Class<out ClassEmitters>): CommonStreamCodec<ParticleEmitters> {
        val constructor = type.getConstructor(Vec3::class.java, Level::class.java)
        return CommonStreamCodec.of(
            { buf, emitter ->
                emitter as ClassEmitters
                ClassEmitters.encodeBase(emitter, buf)
                encodeFields(type, emitter, buf)
            },
            { buf ->
                constructor.newInstance(Vec3.ZERO, null).apply {
                    ClassEmitters.decodeBase(this, buf)
                    decodeFields(type, this, buf)
                }
            }
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun encodeFields(type: Class<*>, emitter: Any, buf: PacketByteBuf) {
        CodecFieldAccessor.fields(type).forEach { field ->
            val codec = ForgeCodecHelper.registryCodecOf(CodecFieldAccessor.valueType(field)) as
                    CommonStreamCodec<Any>
            codec.encode(buf, CodecFieldAccessor.get(field, emitter))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeFields(type: Class<*>, emitter: Any, buf: PacketByteBuf) {
        CodecFieldAccessor.fields(type).forEach { field ->
            val codec = ForgeCodecHelper.registryCodecOf(CodecFieldAccessor.valueType(field)) as
                    CommonStreamCodec<Any>
            CodecFieldAccessor.set(field, emitter, codec.decode(buf))
        }
    }
}
