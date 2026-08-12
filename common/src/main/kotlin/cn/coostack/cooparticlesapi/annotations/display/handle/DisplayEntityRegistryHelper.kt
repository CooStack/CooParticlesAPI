package cn.coostack.cooparticlesapi.annotations.display.handle

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.display.DisplayEntity
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Modifier

object DisplayEntityRegistryHelper {

    /**
     * 生成编解码器
     *
     * 必须提供构造器 (pos: Vec3, world: Level?)
     *
     * @param randomInstance 任意一个displayer实例 不管有没有加入到游戏中
     * @return 这个实例按照注解的参数的编解码器
     */
    fun generateCodec(randomInstance: DisplayEntity): StreamCodec<in RegistryFriendlyByteBuf, DisplayEntity> {
        val type = randomInstance::class.java
        val constructor = type.getConstructor(Vec3::class.java, Level::class.java)
        return StreamCodec.of(
            { buf, display ->
                display as DisplayEntity
                DisplayEntity.encodeBase(display, buf)
                val fields =
                    type.declaredFields.filter {
                        it.isAnnotationPresent(CodecField::class.java) && !Modifier.isFinal(
                            it.modifiers
                        )
                    }
                        .sortedBy { it.name }

                fields.forEach {
                    it.isAccessible = true
                    // 获取对应的参数
                    @Suppress("UNCHECKED_CAST")
                    val codec: StreamCodec<RegistryFriendlyByteBuf, Any> =
                        CodecHelper.registryCodecOf(it.genericType) as StreamCodec<RegistryFriendlyByteBuf, Any>
                    codec.encode(buf, it.get(display))
                }
            }, { buf ->
                constructor.newInstance(Vec3.ZERO, null).apply {
                    DisplayEntity.decodeBase(this, buf)
                    val fields =
                        type.declaredFields.filter {
                            it.isAnnotationPresent(CodecField::class.java) && !Modifier.isFinal(
                                it.modifiers
                            )
                        }
                            .sortedBy { it.name }

                    fields.forEach {
                        it.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        val codec: StreamCodec<RegistryFriendlyByteBuf, Any> =
                            CodecHelper.registryCodecOf(it.genericType) as StreamCodec<RegistryFriendlyByteBuf, Any>

                        val value = codec.decode(buf)
                        it.set(this, value)
                    }
                }
            }
        )
    }
}
