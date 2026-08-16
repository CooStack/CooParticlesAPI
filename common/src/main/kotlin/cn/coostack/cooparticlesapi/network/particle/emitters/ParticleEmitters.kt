package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.api.NetworkDirtyMarkable
import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID


/**
 * 发射单个粒子的 粒子发射器
 *
 * 单个粒子通过 ParticleEmitters 修改粒子参数
 *
 * -> 不固定数量的ParticleStyleData
 * -> 方便输入的
 */
interface ParticleEmitters : ServerControler<ParticleEmitters>, NetworkDirtyMarkable {
    var pos: Vec3
    var world: Level?
    var tick: Int

    /**
     * 当maxTick == -1时
     * 代表此粒子不会由生命周期控制
     */
    var maxTick: Int
    var delay: Int
    var uuid: UUID
    var canceled: Boolean
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

    fun spawnParticle(pos: Vec3, lerpProgress: Float)

    /**
     * 更新发射器属性状态
     * 服务器发包到客户端
     */
    fun update(emitters: ParticleEmitters)

    /**
     * 注册时传入的CODEC必须和这个完全一致
     *
     * 编解码器
     *
     * 编码粒子信息, 当前位置
     */
    fun getCodec(): StreamCodec<RegistryFriendlyByteBuf, ParticleEmitters>

    override fun getValue(): ParticleEmitters {
        return this
    }

    /**
     * 标记发射器的完整网络状态需要更新。
     *
     * Manager 会在当前服务端 tick 结束时合并发送，同一发射器一 tick 内只会更新一次。
     */
    override fun markDirty() {
        if (world?.isClientSide != true) {
            ParticleEmittersManager.enqueueDirty(this)
        }
    }

    override fun remove() {
        canceled = true
    }

    override fun spawn(world: Level, pos: Vec3) {
        if (world !is ServerLevel) return
        this.world = world
        this.pos = pos
        ParticleEmittersManager.spawnEmitters(this)
    }

    override fun isValid(): Boolean {
        return !canceled
    }

    override fun rotateAsAxis(radian: Double) {
    }

    override fun rotateToPoint(to: RelativeLocation) {
    }

    override fun rotateToWithAngle(to: RelativeLocation, radian: Double) {
    }

    override fun teleportTo(to: Vec3) {
        if (pos == to) return
        pos = to
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3(x, y, z))
    }

}
