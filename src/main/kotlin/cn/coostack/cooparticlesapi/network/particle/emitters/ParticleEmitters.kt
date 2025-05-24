package cn.coostack.cooparticlesapi.network.particle.emitters

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.UUID


/**
 * 发射单个粒子的 粒子发射器
 *
 * 单个粒子通过 ParticleEmitters 修改粒子参数
 *
 * -> 不固定数量的ParticleStyleData
 * -> 方便输入的
 *
 *
 * TODO BUGS 当maxTick = 1时 会有概率不显示 (应该是tick == maxTick的状态优先被同步过去了)
 */
interface ParticleEmitters {
    var pos: Vec3d
    var world: World?
    var tick: Int

    /**
     * 当maxTick == -1时
     * 代表此粒子不会由生命周期控制
     */
    var maxTick: Int
    var delay: Int
    var uuid: UUID
    var cancelled: Boolean
    var playing: Boolean
    fun getEmittersID(): String

    /**
     * 发射粒子
     * 服务器发包
     * 客户端发射
     */
    fun start()

    fun stop()

    fun tick()

    fun spawnParticle()

    /**
     * 更新发射器属性状态
     * 服务器发包到客户端
     */
    fun update(emitters: ParticleEmitters)

    /**
     * 编解码器
     * 编码粒子信息, 当前位置
     */
    fun getCodec(): PacketCodec<RegistryByteBuf, ParticleEmitters>
}