package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.network.particle.ServerControler
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.network.PacketByteBuf
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
interface ParticleEmitters : ServerControler<ParticleEmitters> {
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

    /**
     * @param innerClass 是否在类内添加
     * 在类内添加的event handler不会参与codec传输
     */
    fun addEventHandler(handler: ParticleEventHandler, innerClass: Boolean)

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
    fun getCodec(): PacketCodec<PacketByteBuf, ParticleEmitters>

    override fun getValue(): ParticleEmitters {
        return this
    }

    override fun remove() {
        cancelled = true
    }

    override fun rotateParticlesAsAxis(angle: Double) {
    }

    override fun rotateParticlesToPoint(to: RelativeLocation) {
    }

    override fun rotateToWithAngle(to: RelativeLocation, angle: Double) {
    }

    override fun teleportTo(to: Vec3d) {
        pos = to
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3d(x, y, z))
    }

}