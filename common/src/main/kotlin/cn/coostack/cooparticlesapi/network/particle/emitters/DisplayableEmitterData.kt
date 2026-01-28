package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.network.particle.data.SerializableData
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

// TODO 未完成
class DisplayableEmitterData : SerializableData {


    override fun getCodec(): StreamCodec<FriendlyByteBuf, out SerializableData> {
        TODO("Not yet implemented")
    }

    override fun clone(): SerializableData {
        TODO("Not yet implemented")
    }

    override fun createDisplayer(): ParticleDisplayer {
        TODO("Not yet implemented")
    }
}