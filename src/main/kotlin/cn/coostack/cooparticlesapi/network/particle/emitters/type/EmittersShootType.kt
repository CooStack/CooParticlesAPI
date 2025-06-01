package cn.coostack.cooparticlesapi.network.particle.emitters.type

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d

interface EmittersShootType {
    /**
     * 获取id 用于序列化
     */
    fun getID(): String

    /**
     * 用于序列化发射类型
     */
    fun getCodec(): PacketCodec<PacketByteBuf, EmittersShootType>

    /**
     * 获取生成粒子的位置
     * @param origin 添加offset后的位置
     * @param tick 发射器当前tick
     * @param count 粒子数量
     * @return 可能返回多个粒子
     */
    fun getPositions(origin: Vec3d, tick: Int, count: Int): List<Vec3d>

    /**
     * 获取粒子的初始方向
     * @param enter 输入的参数方向
     * @param pos 粒子生成位置
     * @param origin 粒子发射器的位置
     */
    fun getDefaultDirection(enter: Vec3d, tick: Int, pos: Vec3d, origin: Vec3d): Vec3d
}