package cn.coostack.cooparticlesapi.network.particle.data

import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

interface SerializableData {
    fun getCodec(): StreamCodec<FriendlyByteBuf, out SerializableData>

    fun clone(): SerializableData

    fun createDisplayer(): ParticleDisplayer

}