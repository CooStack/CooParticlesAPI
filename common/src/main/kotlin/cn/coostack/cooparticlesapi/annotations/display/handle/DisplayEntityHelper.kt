package cn.coostack.cooparticlesapi.annotations.display.handle

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.annotations.display.DisplayField
import cn.coostack.cooparticlesapi.annotations.emitter.EmitterField
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Modifier
import java.util.UUID

object DisplayEntityHelper {
    /**
     * 你不用在重写的时候执行这个，除非你没有调用super.update()
     *
     * @param current
     * @param other
     */
    fun updateEmitter(current: DisplayEntity, other: DisplayEntity) {
        if (current::class.java != other::class.java) return
        val fields = current::class.java.declaredFields
        fields.filter {
            it.isAnnotationPresent(DisplayField::class.java) && !Modifier.isFinal(it.modifiers)
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
    fun generateCodec(randomInstance: DisplayEntity): StreamCodec<FriendlyByteBuf, DisplayEntity> {
        val type = randomInstance::class.java
        val constructor = type.getConstructor(Vec3::class.java, Level::class.java)
        return StreamCodec.of(
            { buf, display ->
                display as DisplayEntity
                DisplayEntity.encodeBase(display, buf)
                val fields =
                    type.declaredFields.filter {
                        it.isAnnotationPresent(DisplayField::class.java) && !Modifier.isFinal(
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
                            ?: throw IllegalArgumentException("存在不支持的类型 :${codecKey.name} 需要使用CodecHelper.register()进行注册类型")
                    codec.encode(buf, it.get(display))
                }
            }, { buf ->
                constructor.newInstance(Vec3.ZERO, null).apply {
                    DisplayEntity.decodeBase(this, buf)
                    val fields =
                        type.declaredFields.filter {
                            it.isAnnotationPresent(DisplayField::class.java) && !Modifier.isFinal(
                                it.modifiers
                            )
                        }
                            .sortedBy { it.name }

                    fields.forEach {
                        it.isAccessible = true
                        val codecKey = it.type
                        val codec: StreamCodec<FriendlyByteBuf, Any> =
                            CodecHelper.supposedTypes[codecKey.name] as? StreamCodec<FriendlyByteBuf, Any>
                                ?: throw IllegalArgumentException("存在不支持的类型 :${codecKey.name} 需要使用CodecHelper.register()进行注册类型")

                        val value = codec.decode(buf)
                        it.set(this, value)
                    }
                }
            }
        )
    }
}