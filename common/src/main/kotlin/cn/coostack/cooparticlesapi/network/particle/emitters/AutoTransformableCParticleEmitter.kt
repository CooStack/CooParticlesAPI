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
 * 实现类必须标注 [CooAutoRegister]，并提供公开的 `(Vec3, Level?)` 构造器。
 * 普通同步字段可以使用 [CodecField]；会在运行时修改的 Kotlin 属性建议使用
 * `var value by dirty(initialValue)`，赋值变化时会自动调用 [markDirty]，不需要再添加 [CodecField]。
 * 基类生命周期和变换字段会自动编码。
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
