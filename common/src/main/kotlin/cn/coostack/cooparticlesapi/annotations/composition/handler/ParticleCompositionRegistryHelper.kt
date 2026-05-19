package cn.coostack.cooparticlesapi.annotations.composition.handler

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.SequencedParticleComposition
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Modifier

object ParticleCompositionRegistryHelper {
    /**
     * 生成编解码器
     *
     * 必须提供构造器 (pos: Vec3, world: Level?)
     *
     * @param randomInstance 任意一个composition实例 不管有没有加入到游戏中
     * @return 这个实例按照注解的参数的编解码器
     */
    fun generateCodec(randomInstance: ParticleComposition): StreamCodec<FriendlyByteBuf, ParticleComposition> {
        return generateCodec(randomInstance::class.java)
    }

    fun generateCodec(type: Class<out ParticleComposition>): StreamCodec<FriendlyByteBuf, ParticleComposition> {
        val constructor = type.getConstructor(Vec3::class.java, Level::class.java)
        return StreamCodec.of(
            { buf, composition ->
                if (composition is SequencedParticleComposition) {
                    SequencedParticleComposition.encodeBase(composition, buf)
                } else {
                    ParticleComposition.encodeBase(composition, buf)
                }
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
                    val codec: StreamCodec<FriendlyByteBuf, Any> =
                        CodecHelper.codecOf(it.genericType) as StreamCodec<FriendlyByteBuf, Any>
                    codec.encode(buf, it.get(composition))
                }
            }, { buf ->
                constructor.newInstance(Vec3.ZERO, null).apply {
                    if (this is SequencedParticleComposition) {
                        SequencedParticleComposition.decodeBase(this, buf)
                    } else {
                        ParticleComposition.decodeBase(this, buf)
                    }
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
                        val codec: StreamCodec<FriendlyByteBuf, Any> =
                            CodecHelper.codecOf(it.genericType) as StreamCodec<FriendlyByteBuf, Any>

                        val value = codec.decode(buf)
                        it.set(this, value)
                    }
                }
            }
        )
    }
}
