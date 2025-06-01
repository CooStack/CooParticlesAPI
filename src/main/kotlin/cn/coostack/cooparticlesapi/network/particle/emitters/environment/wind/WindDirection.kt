package cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind

import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d

interface WindDirection {
    /**
     * 风向
     * 如果relative 为true 则此选项作废
     * direction.length() 代表风速大小
     * 如果设置为0向量 则代表没有风
     * relative为true 时代表粒子相对发射器位置为风向
     */
    var direction: Vec3d
    var relative: Boolean
    /**
     *  relative 为false时此功能不启用
     * 若relative为true
     * 则此参数代表风力大小
     * 表达式采用EvalEx3.5.0
     * 提供的变量 l (代表粒子与发射器的实际距离)
     * 返回值是风速大小(direction.length())
     */
    var windSpeedExpress: String

    fun loadEmitters(emitters: ParticleEmitters): WindDirection

    fun hasLoadedEmitters(): Boolean

    fun getID(): String
    /**
     * 获得当前位置的风速向量
     */
    fun getWind(particlePos: Vec3d): Vec3d
    /**
     * 粒子位置是否在可操作范围内
     */
    fun inRange(pos: Vec3d): Boolean

    fun getCodec(): PacketCodec<PacketByteBuf, WindDirection>
}