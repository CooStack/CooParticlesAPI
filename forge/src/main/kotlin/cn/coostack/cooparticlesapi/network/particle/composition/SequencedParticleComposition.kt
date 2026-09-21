package cn.coostack.cooparticlesapi.network.particle.composition

import net.minecraft.network.PacketByteBuf
import net.minecraft.world.phys.Vec3

class SequencedParticleComposition(position: Vec3, world: Level? = null) : ParticleComposition(position, world) {
    constructor(world: Level?) : this(Vec3.ZERO, world)
    constructor(world: Level?, pos: Vec3) : this(pos, world)

    override fun getCodec(): CommonStreamCodec<ParticleComposition> {
        TODO("Not yet implemented")
    }
}
