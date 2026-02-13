package cn.coostack.cooparticlesapi.network.particle.style

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.packet.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ControlType
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.math.PI

/** 客户端渲染和服务端处理都用这个类 */
@Deprecated("使用ParticleComposition")
abstract class ParticleGroupStyle(var visibleRange: Double = 32.0, val uuid: UUID = UUID.randomUUID()) :
    Controlable<ParticleGroupStyle>, ServerControler<ParticleGroupStyle>, Tickable<ParticleGroupStyle> {
    var world: Level? = null
    var pos: Vec3 = Vec3.ZERO
    var client = false
    var rotate = 0.0
    var axis = RelativeLocation.yAxis()
    var scale = 1.0
        set(value) {
            field = value.coerceAtLeast(0.001)
        }

    var positionDirty = false


    /**
     * 上一次更新的游戏时间
     * > 用来解决回放的时候会产生额外的粒子 或者导致缺少粒子 (额外tick)
     */

    var lastUpdatedGameTime = 0L

    /** 生成粒子样式的时间 可能会直接跳转到其他时候 然后这个时候这个参数就需要进行同步 */
    var displayedTime = 0L

    /** 自动发包同步到客户端 可能会占据大量带宽 */
    var autoToggle = false

    var displayed = false
        internal set
    var valid = true
        internal set
    internal val invokeQueue = ArrayList<ParticleGroupStyle.() -> Unit>()
    val particles = ConcurrentHashMap<UUID, Controlable<*>>()
    val particleLocations = ConcurrentHashMap<Controlable<*>, RelativeLocation>()


    /** 当粒子组合初始化时, 存储1倍缩放粒子组与原点的距离 */
    val particleDefaultLength = ConcurrentHashMap<UUID, Double>()


    abstract fun getCurrentFrames(): Map<StyleData, RelativeLocation>

    abstract fun onDisplay()

    /**
     * 服务器同步到客户端时, 执行的代码 基类已经存储的参数 如 pos, world ,rotate, axis, scale, uuid 无需同步
     * 自定义的其他参数需要同步
     *
     * 如果 autoToggle = true 则会在每个tick都会执行发包 反复调用此方法
     */
    abstract fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>>

    /**
     * 客户端接受服务器的同步时, 使用的代码
     *
     * 同步内容如下 -> 客户端创建此类时 输入的基本参数 包括了你在 writePacketArgs输入的参数 客户端在类内存在其余更改时
     * 服务器传入的参数 比如自己设定的一些其他参数值
     *
     * 无需处理以下参数 pos world rotate axis scale uuid 其余参数自行处理
     */
    abstract fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>)

    open fun beforeDisplay(styles: Map<StyleData, RelativeLocation>) {
    }

    override fun addPreTickAction(action: ParticleGroupStyle.() -> Unit): ParticleGroupStyle {
        invokeQueue.add(action)
        return this
    }

    override fun controlUUID(): UUID {
        return uuid
    }

    override fun rotateToPoint(to: RelativeLocation) {
        Math3DUtil.rotatePointsToPoint(
            particleLocations.values.toList(), to, axis
        )
        axis = to
        toggleRelative()
        if (!client) {
            // 同步到其他客户端
            change(
                mapOf(
                    "rotate_to" to ParticleControlerDataBuffers.relative(to)
                )
            )
        }
    }

    override fun rotateToWithAngle(to: RelativeLocation, angle: Double) {
        Math3DUtil.rotateAsAxis(
            particleLocations.values.toList(), axis, angle
        )
        Math3DUtil.rotatePointsToPoint(
            particleLocations.values.toList(), to, axis
        )
        axis = to
        this.rotate += angle
        if (this.rotate >= 2 * PI) {
            this.rotate -= 2 * PI
        }
        toggleRelative()
        if (!client) {
            // 同步到其他客户端
            change(
                mapOf(
                    "rotate_to" to ParticleControlerDataBuffers.relative(to),
                    "rotate_angle" to ParticleControlerDataBuffers.double(angle)
                )
            )
        }
    }

    override fun rotateAsAxis(angle: Double) {
        Math3DUtil.rotateAsAxis(
            particleLocations.values.toList(), axis, angle
        )
        this.rotate += angle
        if (this.rotate >= 2 * PI) {
            this.rotate -= 2 * PI
        }
        toggleRelative()
        if (!client) {
            // 同步到其他客户端
            change(
                mapOf(
                    "rotate_angle" to ParticleControlerDataBuffers.double(angle)
                )
            )
        }
    }

    override fun teleportTo(pos: Vec3) {
        this.pos = pos
        toggleRelative()
        if (!client) {
            // 同步到其他客户端
            change(
                mapOf(
                    "teleport" to ParticleControlerDataBuffers.vec3d(pos)
                )
            )
        }
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3(x, y, z))
    }

    override fun remove() {
        clear(false)
        if (!client) {
            // 同步到其他客户端
            ParticleStyleManager.filterVisiblePlayer(this).forEach {
                val player = world!!.getPlayerByUUID(it) as? ServerPlayer
                player ?: return@forEach
                CooParticlesServices.SERVER_NETWORK.send(
                    PacketParticleStyleS2C(
                        uuid, ControlType.REMOVE, mapOf()
                    ), player
                )
            }
        }
    }

    /** Controler需求 */
    override fun getControlObject(): ParticleGroupStyle {
        return this
    }

    /** ServerControler需求 */
    override fun getValue(): ParticleGroupStyle {
        return this
    }

    /** 当服务器出现一些不是来源于类内自发的更改时 (外部类更改) 请执行此代码 除非你开启了 autoToggle */
    fun change(toggleMethod: ParticleGroupStyle.() -> Unit, args: Map<String, ParticleControlerDataBuffer<*>>) {
        if (client) {
            return
        }
        toggleMethod(this)
        // 发包
        ParticleStyleManager.filterVisiblePlayer(this).forEach {
            val player = world!!.getPlayerByUUID(it) ?: return@forEach
            CooParticlesServices.SERVER_NETWORK.send(
                PacketParticleStyleS2C(
                    uuid, ControlType.CHANGE, args
                ), player as ServerPlayer
            )
        }
    }

    /** 当服务器出现一些不是来源于类内自发的更改时 (外部类更改) 请执行此代码 除非你开启了 autoToggle */
    fun change(args: Map<String, ParticleControlerDataBuffer<*>>) {
        change({}, args)
    }

    open fun display(pos: Vec3, world: Level) {
        if (displayed) {
            return
        }
        displayed = true
        this.displayedTime = world.gameTime
        this.lastUpdatedGameTime = world.gameTime
        this.pos = pos
        this.world = world
        this.client = world.isClientSide
        if (!client) {
            // 服务器只负责数据同步 不负责粒子生成
            onDisplay()
            return
        }
        flush()
        onDisplay()
    }

    open fun flush() {
        if (particles.isNotEmpty()) {
            clear(true)
        }
        displayParticles()
    }

    open fun toggleRelative() {
        if (!client) {
            return
        }
        val iterator = particleLocations.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val particle = entry.key
            val rl = entry.value
            particle.teleportTo(
                pos.add(
                    rl.toVector()
                )
            )
        }
        positionDirty = false
    }


    private fun spawnSingleParticle(data: StyleData, rel: RelativeLocation) {
        val displayer = data.displayerBuilder(data.uuid)
        if (displayer !is ParticleDisplayer.SingleParticleDisplayer) return
        val controler = ControlParticleManager.createControl(data.uuid)
        controler.applyInitializedAction(data.particleHandler)
        val toPos = Vec3(pos.x + rel.x, pos.y + rel.y, pos.z + rel.z)
        displayer.display(toPos, world as ClientLevel) ?: return
        data.particleControlerHandler(controler)
        // 把粒子丢回生成列表
        particles[data.uuid] = controler
        particleLocations[controler] = rel
    }

    open fun tick() {
        if (!displayed || !valid) {
            clear(false)
            return
        }
        val current = world!!.gameTime
        if (!displayed || !valid) {
            clear(false)
            return
        }
        invokeQueue.forEach {
            it(this)
        }

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val style = iterator.next()
            when (val value = style.value) {
                is ControlableParticleGroup -> {
                    value.tick()
                }

                is ParticleGroupStyle -> {
                    value.tick()
                }

                is ParticleComposition -> {
                    value.tick()
                }

                is DisplayEntity -> {
                    value.tick()
                }
            }
        }
        this.lastUpdatedGameTime = current
    }

    fun toggleScale(locations: Map<StyleData, RelativeLocation>) {
        if (!valid) {
            // remove过后 无法同步
            return
        }
        if (particleDefaultLength.isEmpty()) {
            locations.forEach {
                val uuid = it.key.uuid
                particleDefaultLength[uuid] = it.value.length()
            }
        }
        locations.forEach {
            val uuid = it.key.uuid
            val len = particleDefaultLength[uuid] ?: return@forEach
            if (len <= 0.0) {
                return@forEach
            }
            val value = it.value
            value.multiply(len * scale / value.length())
        }
    }

    open fun scale(new: Double) {
        if (new < 0.0) {
            CooParticlesConstants.logger.error("scale can not be less than zero")
            return
        }
        scale = new
        // 如果没有创建, 那么此处的环境100%是创建此对象时使用的环境
        // 多为服务端(除非有人使在Client环境创建了这个类)
        if (displayed) {
            toggleScaleDisplayed()
        }
        // 发包有效
        if (!valid) {
            // remove过后 无法同步
            return
        }
        if (!client) {
            change(mapOf("scale" to ParticleControlerDataBuffers.double(new)))
        }
    }

    protected open fun toggleScaleDisplayed() {
        particleLocations.forEach {
            val uuid = it.key.controlUUID()
            val len = particleDefaultLength[uuid]!!
            val value = it.value
            if (len in -1e-3..1e-3) return@forEach
            value.multiply(len * scale / value.length())
        }
        toggleRelative()
    }

    override fun spawn(world: Level, pos: Vec3) {
        if (world !is ServerLevel) return
        ParticleStyleManager.spawnStyle(world, pos, this)
    }

    open fun preRotateTo(map: Map<StyleData, RelativeLocation>, to: RelativeLocation) {
        Math3DUtil.rotatePointsToPoint(
            map.values.toList(), to, axis
        )
        this.axis = to
    }

    open fun preRotateAsAxis(map: Map<StyleData, RelativeLocation>, axis: RelativeLocation, angle: Double) {
        Math3DUtil.rotateAsAxis(
            map.values.toList(), axis, angle
        )
        this.axis = axis
    }

    open fun preRotateAsAxis(map: Map<StyleData, RelativeLocation>, angle: Double) {
        Math3DUtil.rotateAsAxis(
            map.values.toList(), axis, angle
        )
    }

    private fun displayParticles() {
        val locations = getCurrentFrames()
        beforeDisplay(locations)
        toggleScale(locations)
        Math3DUtil.rotateAsAxis(locations.values.toList(), axis, rotate)
        locations.forEach {
            val data = it.key
            val uuid = it.key.uuid
            val rl = it.value
            val displayer = it.key.displayerBuilder(uuid)
            if (displayer is ParticleDisplayer.SingleParticleDisplayer) {
                val controler = ControlParticleManager.createControl(uuid)
                controler.applyInitializedAction(data.particleHandler)
            }
            val toPos = Vec3(pos.x + rl.x, pos.y + rl.y, pos.z + rl.z)
            val controler = displayer.display(toPos, world as ClientLevel) ?: return@forEach
            if (controler is ParticleControler) {
                data.particleControlerHandler(controler)
            }

            particles[uuid] = controler
            particleLocations[controler] = rl
        }
    }

    override fun remove(reason: RemoveReason) {
        remove()
    }

    open internal fun clear(valid: Boolean) {
        particles.forEach {
            it.value.remove()
        }
        particles.clear()
        particleLocations.clear()
        particleDefaultLength.clear()
        this.valid = valid
    }

    open class StyleData(
        val displayerBuilder: (UUID) -> ParticleDisplayer,
    ) {
        val uuid: UUID = UUID.randomUUID()
        var particleHandler: ControlableParticle.() -> Unit = {}
        var particleControlerHandler: ParticleControler.() -> Unit = {}
        fun withParticleHandler(
            builder: ControlableParticle.() -> Unit
        ): StyleData {
            particleHandler = builder
            return this
        }

        fun withParticleControlerHandler(
            builder: ParticleControler.() -> Unit
        ): StyleData {
            particleControlerHandler = builder
            return this
        }
    }

    /** 为了提高傻逼StyleData的复用性 专门设置此类 */
    open class StyleDataBuilder() {
        private var displayerBuilder: (UUID) -> ParticleDisplayer =
            { ParticleDisplayer.withSingle(ControlableEndRodEffect(it)) }
        private val particleHandlers = mutableListOf<ControlableParticle.() -> Unit>()
        private val particleControlerHandlers = mutableListOf<ParticleControler.() -> Unit>()
        fun addParticleHandler(
            builder: ControlableParticle.() -> Unit
        ): StyleDataBuilder {
            particleHandlers.add(builder)
            return this
        }

        fun addParticleControlerHandler(
            builder: ParticleControler.() -> Unit
        ): StyleDataBuilder {
            particleControlerHandlers.add(builder)
            return this
        }

        fun clearParticleHandlers(): StyleDataBuilder {
            particleHandlers.clear()
            return this
        }

        fun clearParticleControlers(): StyleDataBuilder {
            particleControlerHandlers.clear()
            return this
        }

        fun removeHandler(index: Int): StyleDataBuilder {
            if (index in particleHandlers.indices) particleHandlers.removeAt(index)
            return this
        }

        fun removeParticleControler(index: Int): StyleDataBuilder {
            if (index in particleControlerHandlers.indices) particleControlerHandlers.removeAt(index)
            return this
        }

        fun displayer(builder: (UUID) -> ParticleDisplayer): StyleDataBuilder {
            this.displayerBuilder = builder
            return this
        }

        fun build(): StyleData = StyleData(displayerBuilder)
            .withParticleHandler {
                particleHandlers.forEach { it() }
            }.withParticleControlerHandler {
                particleControlerHandlers.forEach { it() }
            }
    }
}