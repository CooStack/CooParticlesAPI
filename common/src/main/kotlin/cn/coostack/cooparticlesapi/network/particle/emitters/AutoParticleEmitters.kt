package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersRegistryHelper
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 防止没必要的手动调用-写了一个这个
 *
 * 使用时需给类注解 EmitterAutoRegister
 *
 * 使用此类必须用 CodecField注解非final数据用于同步到客户端
 *
 * @see cn.coostack.cooparticlesapi.annotations.emitter.EmitterAutoRegister
 * @see cn.coostack.cooparticlesapi.annotations.emitter.CodecField
 * @see ClassParticleEmitters
 * @see ParticleEmittersRegistryHelper
 * @constructor 你的实现必须提供空构造方法 或者 (Vec3,Level?) 构造方法
 *
 * @param pos 发射器生成位置
 * @param world 发射器生效世界
 */
abstract class AutoParticleEmitters(pos: Vec3, world: Level?) : ClassParticleEmitters(pos, world) {
    override fun getEmittersID(): String {
        return this::class.java.name
    }

    override fun getCodec(): StreamCodec<RegistryFriendlyByteBuf, ParticleEmitters> {
        return ParticleEmittersRegistryHelper.generateCodec(this)
    }
}