package cn.coostack.cooparticlesapi.annotations.emitter.handle

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.annotations.emitter.EmitterField
import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.lang.reflect.Modifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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


    /**
     * 你不用在重写的时候执行这个，除非你没有调用super.update()
     *
     * @param current
     * @param other
     */
    fun updateEmitter(current: ClassParticleEmitters, other: ClassParticleEmitters) {
        if (current.getEmittersID() != other.getEmittersID()) return
        if (current::class.java != other::class.java) return
        val fields = current::class.java.declaredFields
        fields.filter {
            it.isAnnotationPresent(EmitterField::class.java) && !Modifier.isFinal(it.modifiers)
        }.forEach { field ->
            field.isAccessible = true
            field.set(current, field.get(other))
        }
    }

    /**
     * 生成编解码器
     *
     * 必须提供构造器 (pos: Vec3, world: Level?)
     *
     * @param randomInstance 任意一个emitter实例 不管有没有加入到游戏中
     * @return 这个实例按照注解的参数的编解码器
     */
    fun generateCodec(randomInstance: ClassParticleEmitters): StreamCodec<FriendlyByteBuf, ParticleEmitters> {
        val type = randomInstance::class.java
        val constructor = type.getConstructor(Vec3::class.java, Level::class.java)
        return StreamCodec.of(
            { buf, emitter ->
                emitter as ClassParticleEmitters
                ClassParticleEmitters.encodeBase(emitter, buf)
                val fields =
                    type.declaredFields.filter {
                        it.isAnnotationPresent(EmitterField::class.java) && !Modifier.isFinal(
                            it.modifiers
                        )
                    }
                        .sortedBy { it.name }

                fields.forEach {
                    it.isAccessible = true
                    // 获取对应的参数
                    val codecKey = it.type
                    val codec: StreamCodec<FriendlyByteBuf, Any> =
                        CodecHelper.supposedTypes[codecKey.name] as? StreamCodec<FriendlyByteBuf, Any>
                            ?: throw IllegalArgumentException("存在不支持的类型 :${codecKey.name} 需要使用EmitterAnnotationHandler进行注册类型")
                    codec.encode(buf, it.get(emitter))
                }
            }, { buf ->
                constructor.newInstance(Vec3.ZERO, null).apply {
                    ClassParticleEmitters.decodeBase(this, buf)
                    val fields =
                        type.declaredFields.filter {
                            it.isAnnotationPresent(EmitterField::class.java) && !Modifier.isFinal(
                                it.modifiers
                            )
                        }
                            .sortedBy { it.name }

                    fields.forEach {
                        it.isAccessible = true
                        val codecKey = it.type
                        val codec: StreamCodec<FriendlyByteBuf, Any> =
                            CodecHelper.supposedTypes[codecKey.name] as? StreamCodec<FriendlyByteBuf, Any>
                                ?: throw IllegalArgumentException("存在不支持的类型 :${codecKey.name} 需要使用EmitterAnnotationHandler进行注册类型")

                        val value = codec.decode(buf)
                        it.set(this, value)
                    }
                }
            }
        )
    }
}