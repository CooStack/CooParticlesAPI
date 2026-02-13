package cn.coostack.cooparticlesapi.api.controler

import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3

interface SerializableData {
    fun getCodec(): StreamCodec<FriendlyByteBuf, out SerializableData>

    fun clone(): SerializableData

    /**
     * 赋值基本参数
     *
     * @param world
     * @param pos
     * @param particleLerpProcess
     * @param posLerpProcess
     */
    fun createControler(
        world: ClientLevel,
        pos: Vec3,
        particleLerpProcess: Float,
        posLerpProcess: Float
    ): Controlable<*>

    /**
     * 获取播放器
     *
     * @return
     */
    fun getDisplayer(): ParticleDisplayer

}