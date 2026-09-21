package cn.coostack.cooparticlesapi.api.controler

import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.Vec3

interface SerializableData {
    fun getCodec(): ForgeStreamCodec<PacketByteBuf, out SerializableData>

    fun clone(): SerializableData

    fun createControler(
        world: ClientLevel,
        pos: Vec3,
        particleLerpProcess: Float,
        posLerpProcess: Float
    ): Controlable<*>

    fun getDisplayer(): ParticleDisplayer
}
