package cn.coostack.cooparticlesapi.annotations.emitter.handle

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Modifier

/**
 * 注解处理器
 * 用于获取对应类型的CODEC
 * 通过反射对参数进行直接修改
 *
 * 这里需要他的具体类型
 *
 * 默认兼容基本数据类型 其他自定义类型需要在这里注册CODEC
 *
 * 这样在生成CODEC编解码器的时候就会自动引用内容
 *
 * @author CooStack
 */
object ParticleEmittersHelper {
    fun updateEmitter(current: ClassParticleEmitters, other: ClassParticleEmitters) {
        if (current.getEmittersID() != other.getEmittersID()) return
        CodecHelper.updateFields(current, other)
    }

    fun updateEmitter(current: ClassEmitters, other: ClassEmitters) {
        if (current.getEmittersID() != other.getEmittersID()) return
        CodecHelper.updateFields(current, other)
    }

    fun generateCodec(randomInstance: ClassParticleEmitters): StreamCodec<RegistryFriendlyByteBuf, ParticleEmitters> {
        val type = randomInstance::class.java
        val constructor = type.getConstructor(Vec3::class.java, Level::class.java)
        return StreamCodec.of(
            { buf, emitter ->
                emitter as ClassParticleEmitters
                ClassParticleEmitters.encodeBase(emitter, buf)
                val fields = codecFields(type)
                fields.forEach { field ->
                    field.isAccessible = true
                    val codec = codecByType(field.type)
                    codec.encode(buf, field.get(emitter))
                }
            },
            { buf ->
                constructor.newInstance(Vec3.ZERO, null).apply {
                    ClassParticleEmitters.decodeBase(this, buf)
                    val fields = codecFields(type)
                    fields.forEach { field ->
                        field.isAccessible = true
                        val codec = codecByType(field.type)
                        val value = codec.decode(buf)
                        field.set(this, value)
                    }
                }
            }
        )
    }

    fun generateCodec(randomInstance: ClassEmitters): StreamCodec<RegistryFriendlyByteBuf, ParticleEmitters> {
        val type = randomInstance::class.java
        val constructor = type.getConstructor(Vec3::class.java, Level::class.java)
        return StreamCodec.of(
            { buf, emitter ->
                emitter as ClassEmitters
                ClassEmitters.encodeBase(emitter, buf)
                val fields = codecFields(type)
                fields.forEach { field ->
                    field.isAccessible = true
                    val codec = codecByType(field.type)
                    codec.encode(buf, field.get(emitter))
                }
            },
            { buf ->
                constructor.newInstance(Vec3.ZERO, null).apply {
                    ClassEmitters.decodeBase(this, buf)
                    val fields = codecFields(type)
                    fields.forEach { field ->
                        field.isAccessible = true
                        val codec = codecByType(field.type)
                        val value = codec.decode(buf)
                        field.set(this, value)
                    }
                }
            }
        )
    }

    private fun codecFields(type: Class<*>): List<java.lang.reflect.Field> {
        return type.declaredFields
            .filter { it.isAnnotationPresent(CodecField::class.java) && !Modifier.isFinal(it.modifiers) }
            .sortedBy { it.name }
    }

    @Suppress("UNCHECKED_CAST")
    private fun codecByType(type: Class<*>): StreamCodec<FriendlyByteBuf, Any> {
        return CodecHelper.supposedTypes[type.name] as? StreamCodec<FriendlyByteBuf, Any>
            ?: throw IllegalArgumentException(
                "Unsupported codec type: ${type.name}, please register it in CodecHelper"
            )
    }
}
