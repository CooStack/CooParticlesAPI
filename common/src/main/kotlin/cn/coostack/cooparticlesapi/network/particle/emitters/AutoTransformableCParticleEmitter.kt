package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersRegistryHelper
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 自动生成网络 codec 并以实现类完整类名注册的 [TransformableCParticleEmitter]。
 *
 * 实现类必须标注 [CooAutoRegister]，提供公开的 `(Vec3, Level?)` 构造器，并用 [CodecField]
 * 标记需要额外同步的非 final 实例字段。基类生命周期和变换字段会自动编码。
 *
 * @param pos 发射器世界坐标
 * @param world 发射器所在世界
 */
abstract class AutoTransformableCParticleEmitter(pos: Vec3, world: Level?) :
    TransformableCParticleEmitter(pos, world) {
    final override fun getEmittersID(): String = this::class.java.name

    final override fun getCodec(): StreamCodec<RegistryFriendlyByteBuf, ParticleEmitters> {
        return ParticleEmittersRegistryHelper.generateCodec(this)
    }
}
