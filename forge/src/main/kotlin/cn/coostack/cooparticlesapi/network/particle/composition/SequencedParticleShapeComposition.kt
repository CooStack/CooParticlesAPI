package cn.coostack.cooparticlesapi.network.particle.composition

import net.minecraft.network.PacketByteBuf
import net.minecraft.world.phys.Vec3
import java.util.UUID

class SequencedParticleShapeComposition(uuid: UUID) : ParticleShapeComposition(uuid) {
    override fun getCodec(): CommonStreamCodec<ParticleComposition> {
        TODO("Not yet implemented")
    }
}
