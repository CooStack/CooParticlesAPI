package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.annotations.composition.handler.ParticleCompositionRegistryHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

abstract class AutoSequencedParticleComposition(position: Vec3, world: Level? = null) :
    SequencedParticleComposition(position, world) {

    override fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleComposition> {
        return ParticleCompositionRegistryHelper.generateCodec(this)
    }

}