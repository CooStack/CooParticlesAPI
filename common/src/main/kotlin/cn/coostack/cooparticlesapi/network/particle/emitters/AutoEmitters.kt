package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersRegistryHelper
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * Auto codec + auto id version of [ClassEmitters].
 */
abstract class AutoEmitters(pos: Vec3, world: Level?) : ClassEmitters(pos, world) {
    override fun getEmittersID(): String {
        return this::class.java.name
    }

    override fun getCodec(): StreamCodec<RegistryFriendlyByteBuf, ParticleEmitters> {
        return ParticleEmittersRegistryHelper.generateCodec(this)
    }
}

