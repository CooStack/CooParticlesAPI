package cn.coostack.cooparticlesapi.annotations.display.handle

import cn.coostack.cooparticlesapi.annotations.codec.CodecFieldAccessor
import cn.coostack.cooparticlesapi.annotations.codec.CommonStreamCodec
import cn.coostack.cooparticlesapi.annotations.codec.ForgeCodecHelper
import cn.coostack.cooparticlesapi.display.DisplayEntity
import net.minecraft.network.PacketByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object DisplayEntityRegistryHelper {

    fun generateCodec(randomInstance: DisplayEntity): CommonStreamCodec<DisplayEntity> {
        val type = randomInstance::class.java
        val constructor = type.getConstructor(Vec3::class.java, Level::class.java)
        return CommonStreamCodec.of(
            { buf, display ->
                display as DisplayEntity
                DisplayEntity.encodeBase(display, buf)
                val fields = CodecFieldAccessor.fields(type)

                fields.forEach {
                    it.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val codec: CommonStreamCodec<Any> =
                        ForgeCodecHelper.registryCodecOf(CodecFieldAccessor.valueType(it)) as CommonStreamCodec<Any>
                    codec.encode(buf, CodecFieldAccessor.get(it, display))
                }
            }, { buf ->
                constructor.newInstance(Vec3.ZERO, null).apply {
                    DisplayEntity.decodeBase(this, buf)
                    val fields = CodecFieldAccessor.fields(type)

                    fields.forEach {
                        it.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        val codec: CommonStreamCodec<Any> =
                            ForgeCodecHelper.registryCodecOf(CodecFieldAccessor.valueType(it)) as CommonStreamCodec<Any>

                        val value = codec.decode(buf)
                        CodecFieldAccessor.set(it, this, value)
                    }
                }
            }
        )
    }
}
