package cn.coostack.cooparticlesapi.annotations.renderer.handle

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.codec.CodecFieldAccessor
import cn.coostack.cooparticlesapi.annotations.codec.CommonStreamCodec
import cn.coostack.cooparticlesapi.annotations.codec.ForgeCodecHelper
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.network.PacketByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Modifier

object RenderEntityRegistryHelper {

    fun generateCodec(randomInstance: RenderEntity): CommonStreamCodec<RenderEntity> {
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

        return CommonStreamCodec.of(
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
                    val codec: CommonStreamCodec<Any> =
                        ForgeCodecHelper.codecOf(field.genericType) as CommonStreamCodec<Any>
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
                        val codec: CommonStreamCodec<Any> =
                            ForgeCodecHelper.codecOf(field.genericType) as CommonStreamCodec<Any>
                        val value = codec.decode(buf)
                        field.set(this, value)
                    }
                }
            }
        )
    }
}
