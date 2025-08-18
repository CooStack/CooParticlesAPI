package cn.coostack.cooparticlesapi.network.particle

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.packet.PacketParticleGroupS2C
import cn.coostack.cooparticlesapi.particles.control.ControlType
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * @param clientGroup 客户端渲染的类型
 * @param uuid 粒子组合唯一标识符
 * @param visibleRange 玩家可见范围 （origin)
 */
abstract class ServerParticleGroup(
    var visibleRange: Double = 32.0
) : ServerControler<ServerParticleGroup> {
    val uuid: UUID = UUID.randomUUID()
    var pos: Vec3 = Vec3.ZERO
        internal set
    var world: Level? = null
        internal set
    var valid = true
        internal set
    var canceled = false
        internal set

    /**
     * 客户端渲染视角下的生命周期
     */
    var clientTick = 0
        internal set
    var clientMaxTick = 120
        internal set
    var scale = 1.0

    /**
     * 服务器内的生命周期
     * 同步数据时不会传输给客户端
     */
    var tick = 0
    var maxTick = 120


    var axis: RelativeLocation = RelativeLocation(0.0, 1.0, 0.0)
        internal set

    /**
     * 每个tick能做的事情
     * 例如绑定实体位置 旋转等
     */
    abstract fun tick()

    /**
     * 获取自定义包的其他参数 (用于服务器传输给客户端)
     */
    abstract fun otherPacketArgs(): Map<String, ParticleControlerDataBuffer<out Any>>

    /**
     * 获取对应的客户端层Class
     * @return null 不能单独生成的类型
     */
    abstract fun getClientType(): Class<out ControlableParticleGroup>?


    /**
     * 当粒子组合在服务器创建并且发送到客户端时执行
     * 可以给客户端发送一些包修改
     */
    open fun onGroupDisplay(pos: Vec3, world: ServerLevel) {}

    /**
     * 通过doTickAlive函数导致粒子组合到达最终生命周期时执行的函数
     * 不强制重写
     */
    open fun onTickAliveDeath() {}

    /**
     * 同步后客户端层面粒子死亡 (除非没开启tickAlive)
     * 服务器处理上同样
     */
    open fun onClientViewDeath() {}

    /**
     * 可以在abstract tick中执行
     */
    fun doTickClient() {
        if (clientTick++ >= clientMaxTick) {
            onClientViewDeath()
        }
    }

    /**
     * 可以在abstract tick中执行
     */
    fun doTickAlive() {
        if (tick++ >= maxTick) {
            kill()
            onTickAliveDeath()
        }
    }

    /**
     * 不立刻同步给客户端
     * 只修改参数
     * 但是在新加入可视的玩家会应用此scale
     * 如果想要在客户端不应用服务器的scale
     * 则需要在provider的create方法中重新修改scale值
     */
    fun scaleOnServer(new: Double) {
        if (new < 0.0) {
            CooParticlesConstants.logger.error("scale must be greater than zero.")
            return
        }
        this.scale = new
    }

    /**
     * 服务器修改客户端缩放大小
     * 并且立刻同步到客户端
     */
    fun scale(new: Double) {
        if (new < 0.0) {
            CooParticlesConstants.logger.error("scale must be greater than zero.")
            return
        }
        change(
            {
                this.scale = new
            },
            mapOf(
                PacketParticleGroupS2C.PacketArgsType.SCALE.ofArgs to ParticleControlerDataBuffers.double(new)
            )
        )
    }

    /**
     * 销毁包 (在update时会删除所有的可见)
     */
    fun kill() {
        canceled = true
        world ?: return
        val visible = ServerParticleGroupManager.filterVisiblePlayer(this)
        visible.forEach {
            val player = world!!.getPlayerByUUID(it) ?: return@forEach
            // 发销毁包
            val packet = PacketParticleGroupS2C(uuid, ControlType.REMOVE, mapOf())
            CooParticlesServices.SERVER_NETWORK.send(packet, player as ServerPlayer)
        }
    }

    /**
     * 跟随玩家在线状态. 世界跟换 删除粒子
     */
    fun withPlayerStats(player: ServerPlayer) {
        if (player.hasDisconnected()) {
            kill()
        }

        if (player.level() != world) {
            kill()
        }
    }

    fun withEntityStats(entity: LivingEntity) {
        if (entity.isDeadOrDying) {
            kill()
        }

        if (entity.level() != world) {
            kill()
        }

    }

    fun setAxis(axis: Vec3) {
        change(
            { this.axis = RelativeLocation.of(axis) }, mapOf(
                PacketParticleGroupS2C.PacketArgsType.AXIS.ofArgs to ParticleControlerDataBuffers.vec3d(axis)
            )
        )
    }

    /**
     * 不发送到客户端
     */
    fun setPosOnServer(pos: Vec3) {
        this.pos = pos
    }

    /**
     * 同步旋转后的中心点和对称轴
     */
    fun setRotateToOnServer(to: RelativeLocation) {
        axis = to.normalize()
    }


    /**
     * 出现了一些控制类之外的传送
     */
    fun teleportGroupTo(pos: Vec3) {
        // 发包告知 所有可见客户端
        change(
            { this.pos = pos }, mapOf(
                PacketParticleGroupS2C.PacketArgsType.POS.ofArgs to ParticleControlerDataBuffers.vec3d(pos)
            )
        )
    }

    override fun getValue(): ServerParticleGroup {
        return this
    }

    override fun remove() {
        kill()
    }

    override fun teleportTo(to: Vec3) {
        teleportGroupTo(to)
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3(x, y, z))
    }

    /**
     * 出现了一些控制类之外的旋转
     */
    override fun rotateParticlesAsAxis(angle: Double) {
        change(
            {}, mapOf(
                PacketParticleGroupS2C.PacketArgsType.ROTATE_AXIS.ofArgs to ParticleControlerDataBuffers.double(
                    angle
                )
            )
        )
    }

    override fun rotateToWithAngle(to: RelativeLocation, angle: Double) {
        change(
            {}, mapOf(
                PacketParticleGroupS2C.PacketArgsType.ROTATE_AXIS.ofArgs to ParticleControlerDataBuffers.double(
                    angle
                ),
                PacketParticleGroupS2C.PacketArgsType.ROTATE_TO.ofArgs to ParticleControlerDataBuffers.vec3d(
                    to.toVector()
                )
            )
        )
    }

    /**
     * 出现了一些控制类之外的旋转
     */
    override fun rotateParticlesToPoint(to: RelativeLocation) {
        rotateParticlesToPoint(to.toVector())
    }

    fun rotateParticlesToPoint(to: Vec3) {
        change(
            {}, mapOf(
                PacketParticleGroupS2C.PacketArgsType.ROTATE_TO.ofArgs to ParticleControlerDataBuffers.vec3d(to)
            )
        )
    }

    fun changeTick(tick: Int) {
        change(
            { clientTick = tick }, mapOf(
                PacketParticleGroupS2C.PacketArgsType.CURRENT_TICK.ofArgs to ParticleControlerDataBuffers.int(
                    tick
                )
            )
        )
    }

    fun changeMaxTick(tick: Int) {
        change(
            { this.maxTick = tick }, mapOf(
                PacketParticleGroupS2C.PacketArgsType.MAX_TICK.ofArgs to ParticleControlerDataBuffers.int(tick)
            )
        )
    }

    /**
     * 发送行为修改包
     * @param toggleMethod 修改Server层的参数的方法 如果不用此修改方法则会导致其他客户端渲染不同步
     * @param args 此参数出现过的键 必须在otherPacketArgs方法返回的args中存在 否则任然会存在同步问题
     */
    fun change(toggleMethod: ServerParticleGroup.() -> Unit, args: Map<String, ParticleControlerDataBuffer<*>>) {
        world ?: return
        val visible = ServerParticleGroupManager.filterVisiblePlayer(this)
        toggleMethod(this)
        visible.forEach {
            val player = world!!.getPlayerByUUID(it) ?: return@forEach
            val packet = PacketParticleGroupS2C(uuid, ControlType.CHANGE, args)
            CooParticlesServices.SERVER_NETWORK.send(packet, player as ServerPlayer)
        }
    }

    fun initServerGroup(pos: Vec3, world: Level, maxTick: Int = 120) {
        this.pos = pos
        this.world = world
        this.clientMaxTick = maxTick
    }


}