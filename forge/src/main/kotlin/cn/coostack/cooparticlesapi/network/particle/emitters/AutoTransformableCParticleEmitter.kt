package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersRegistryHelper
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

abstract class AutoTransformableCParticleEmitter(pos: Vec3, world: Level?) :
    TransformableCParticleEmitter(pos, world) {
    final override fun getEmittersID(): String = this::class.java.name

    final override fun getCodec(): ForgeStreamCodec<PacketByteBuf, ParticleEmitters> {
        return ParticleEmittersRegistryHelper.generateCodec(this)
    }
}
