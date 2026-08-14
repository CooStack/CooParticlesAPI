package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersRegistryHelper
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 自动生成网络 codec 并以实现类完整类名注册的 [ClassEmitters]。
 *
 * 实现类需标注 [CooAutoRegister]。普通同步字段可以使用 [CodecField]；
 * 会在运行时修改的 Kotlin 属性建议使用 `var value by dirty(initialValue)`，
 * 赋值变化时会自动调用 [markDirty]，不需要再添加 [CodecField]。
 *
 * @param pos 发射器生成位置
 * @param world 发射器生效世界
 */
abstract class AutoEmitters(pos: Vec3, world: Level?) : ClassEmitters(pos, world) {
    override fun getEmittersID(): String {
        return this::class.java.name
    }

    override fun getCodec(): StreamCodec<RegistryFriendlyByteBuf, ParticleEmitters> {
        return ParticleEmittersRegistryHelper.generateCodec(this)
    }
}
