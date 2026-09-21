package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersRegistryHelper
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

abstract class AutoEmitters(pos: Vec3, world: Level?) : ClassEmitters(pos, world) {
    override fun getEmittersID(): String {
        return this::class.java.name
    }

    override fun getCodec(): ForgeStreamCodec<PacketByteBuf, ParticleEmitters> {
        return ParticleEmittersRegistryHelper.generateCodec(this)
    }
}
