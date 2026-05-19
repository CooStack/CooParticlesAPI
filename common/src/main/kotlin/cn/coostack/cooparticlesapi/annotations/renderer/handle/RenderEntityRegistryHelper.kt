package cn.coostack.cooparticlesapi.annotations.renderer.handle

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Modifier

object RenderEntityRegistryHelper {
    /**
     * 生成编解码器
     *
     * 必须提供构造器 (Level?, Vec3) 或无参构造
     *
     * @param randomInstance 任意一个 RenderEntity 实例
     * @return 这个实例按照注解的参数的编解码器
     */
    fun generateCodec(randomInstance: RenderEntity): StreamCodec<FriendlyByteBuf, RenderEntity> {
        val type = randomInstance::class.java
        val noArgCtor = runCatching { type.getConstructor() }.getOrNull()
        val levelVecCtor = runCatching { type.getConstructor(Level::class.java, Vec3::class.java) }.getOrNull()
        val factory = when {
            noArgCtor != null -> {
                { noArgCtor.newInstance() as RenderEntity }
            }
            levelVecCtor != null -> {
                { levelVecCtor.newInstance(null, Vec3.ZERO) as RenderEntity }
            }
            else -> {
                throw IllegalStateException(
                    "RenderEntity requires public no-arg or (Level, Vec3) constructor: ${type.name}"
                )
            }
        }

        return StreamCodec.of(
            { buf, entity ->
                RenderEntity.encodeBase(buf, entity)
                val fields = type.declaredFields.filter {
                    it.isAnnotationPresent(CodecField::class.java) &&
                        !Modifier.isFinal(it.modifiers) &&
                        !Modifier.isStatic(it.modifiers)
                }.sortedBy { it.name }

                fields.forEach { field ->
                    field.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val codec: StreamCodec<FriendlyByteBuf, Any> =
                        CodecHelper.codecOf(field.genericType) as StreamCodec<FriendlyByteBuf, Any>
                    codec.encode(buf, field.get(entity))
                }
            },
            { buf ->
                factory().apply {
                    RenderEntity.decodeBase(buf, this)
                    val fields = type.declaredFields.filter {
                        it.isAnnotationPresent(CodecField::class.java) &&
                            !Modifier.isFinal(it.modifiers) &&
                            !Modifier.isStatic(it.modifiers)
                    }.sortedBy { it.name }

                    fields.forEach { field ->
                        field.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        val codec: StreamCodec<FriendlyByteBuf, Any> =
                            CodecHelper.codecOf(field.genericType) as StreamCodec<FriendlyByteBuf, Any>
                        val value = codec.decode(buf)
                        field.set(this, value)
                    }
                }
            }
        )
    }
}
