package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.annotations.composition.handler.ParticleCompositionHelper
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.apache.http.MethodNotSupportedException

class ShapeParticleComposition(position: Vec3, world: Level? = null) : ParticleComposition(position, world) {
    /**
     * CLIENT ONLY!!
     *
     * @return
     */
    override fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleComposition> {
        throw MethodNotSupportedException("client only")
    }

    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        TODO("Not yet implemented")
    }

    override fun onDisplay() {
    }
}