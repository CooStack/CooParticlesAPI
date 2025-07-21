package cn.coostack.cooparticlesapi.network.particle.style

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.packet.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.network.particle.ServerControler
import cn.coostack.cooparticlesapi.particles.Controlable
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ControlType
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.particles.impl.TestEndRodEffect
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.world.ClientWorld
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.swing.text.Style
import kotlin.collections.set
import kotlin.math.PI

/**
 * 客户端渲染和服务端处理都用这个类
 *
 * 由于模组作者不想每次都要同步客户端和服务器时在两个地方写相同的代码
 * 于是创造出此类(当初为什么没有想到555)
 */
abstract class ParticleGroupStyle(var visibleRange: Double = 32.0, val uuid: UUID = UUID.randomUUID()) :
    Controlable<ParticleGroupStyle>, ServerControler<ParticleGroupStyle> {
    var world: World? = null
    var pos: Vec3d = Vec3d.ZERO
    var client = false
    var rotate = 0.0
    var axis = RelativeLocation.yAxis()
    var scale = 1.0
        set(value) {
            field = value.coerceAtLeast(0.001)
        }

    /**
     * 自动发包同步到客户端
     * 可能会占据大量带宽
     */
    var autoToggle = false

    var displayed = false
        internal set
    var valid = true
        internal set
    internal val invokeQueue = ArrayList<ParticleGroupStyle.() -> Unit>()
    val particles = ConcurrentHashMap<UUID, Controlable<*>>()
    val particleLocations = ConcurrentHashMap<Controlable<*>, RelativeLocation>()

    /**
     * 当粒子组合初始化时, 存储1倍缩放粒子组与原点的距离
     */
    val particleDefaultLength = ConcurrentHashMap<UUID, Double>()

    abstract fun getCurrentFrames(): Map<StyleData, RelativeLocation>

    abstract fun onDisplay()

    /**
     * 服务器同步到客户端时, 执行的代码
     * 基类已经存储的参数
     * 如 pos, world ,rotate, axis, scale, uuid 无需同步
     * 自定义的其他参数需要同步
     *
     * 如果 autoToggle = true
     * 则会在每个tick都会执行发包 反复调用此方法
     */
    abstract fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>>

    /**
     * 客户端接受服务器的同步时, 使用的代码
     *
     * 同步内容如下 -> 客户端创建此类时 输入的基本参数 包括了你在 writePacketArgs输入的参数
     *               客户端在类内存在其余更改时 服务器传入的参数 比如自己设定的一些其他参数值
     *
     * 无需处理以下参数
     * pos world rotate axis scale uuid
     * 其余参数自行处理
     */
    abstract fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>)

    open fun beforeDisplay(styles: Map<StyleData, RelativeLocation>) {
    }

    fun addPreTickAction(action: ParticleGroupStyle.() -> Unit) {
        invokeQueue.add(action)
    }

    override fun controlUUID(): UUID {
        return uuid
    }

    override fun rotateParticlesToPoint(to: RelativeLocation) {
        Math3DUtil.rotatePointsToPoint(
            particleLocations.values.toList(), to, axis
        )
        axis = to
        toggleRelative()

        if (!client && !autoToggle) {
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
        if (!client && !autoToggle) {
            // 同步到其他客户端
            change(
                mapOf(
                    "rotate_to" to ParticleControlerDataBuffers.relative(to),
                    "rotate_angle" to ParticleControlerDataBuffers.double(angle)
                )
            )
        }
    }

    override fun rotateParticlesAsAxis(angle: Double) {
        Math3DUtil.rotateAsAxis(
            particleLocations.values.toList(), axis, angle
        )
        this.rotate += angle
        if (this.rotate >= 2 * PI) {
            this.rotate -= 2 * PI
        }
        toggleRelative()
        if (!client && !autoToggle) {
            // 同步到其他客户端
            change(
                mapOf(
                    "rotate_angle" to ParticleControlerDataBuffers.double(angle)
                )
            )
        }
    }

    override fun teleportTo(pos: Vec3d) {
        this.pos = pos
        toggleRelative()
        if (!client && !autoToggle) {
            // 同步到其他客户端
            change(
                mapOf(
                    "teleport" to ParticleControlerDataBuffers.vec3d(pos)
                )
            )
        }
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3d(x, y, z))
    }

    override fun remove() {
        clear(false)
        if (!client) {
            // 同步到其他客户端
            ParticleStyleManager.filterVisiblePlayer(this).forEach {
                val player = world!!.getPlayerByUuid(it) as? ServerPlayerEntity
                player ?: return@forEach
                ServerPlayNetworking.send(
                    player, PacketParticleStyleS2C(
                        uuid, ControlType.REMOVE, mapOf()
                    )
                )
            }
        }
    }

    /**
     * Controler需求
     */
    override fun getControlObject(): ParticleGroupStyle {
        return this
    }

    /**
     * ServerControler需求
     */
    override fun getValue(): ParticleGroupStyle {
        return this
    }

    /**
     * 当服务器出现一些不是来源于类内自发的更改时 (外部类更改) 请执行此代码
     * 除非你开启了 autoToggle
     */
    fun change(toggleMethod: ParticleGroupStyle.() -> Unit, args: Map<String, ParticleControlerDataBuffer<*>>) {
        if (autoToggle || client) {
            return
        }
        toggleMethod(this)
        // 发包
        ParticleStyleManager.filterVisiblePlayer(this).forEach {
            val player = world!!.getPlayerByUuid(it) ?: return@forEach
            ServerPlayNetworking.send(
                player as ServerPlayerEntity, PacketParticleStyleS2C(
                    uuid, ControlType.CHANGE, args
                )
            )
        }
    }

    /**
     * 当服务器出现一些不是来源于类内自发的更改时 (外部类更改) 请执行此代码
     * 除非你开启了 autoToggle
     */
    fun change(args: Map<String, ParticleControlerDataBuffer<*>>) {
        change({}, args)
    }

    open fun display(pos: Vec3d, world: World) {
        if (displayed) {
            return
        }
        displayed = true
        this.pos = pos
        this.world = world
        this.client = world.isClient
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
        for (entry in particleLocations) {
            val particle = entry.key
            val rl = entry.value
            particle.teleportTo(
                pos.add(
                    rl.toVector()
                )
            )
        }
    }


    open fun tick() {
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
            val value = style.value

            when (value) {
                is ControlableParticleGroup -> {
                    value.tick()
                }

                is ParticleGroupStyle -> {
                    value.tick()
                }
            }

        }
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
            CooParticleAPI.logger.error("scale can not be less than zero")
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
                controler.initInvoker = data.particleHandler
            }
            val toPos = Vec3d(pos.x + rl.x, pos.y + rl.y, pos.z + rl.z)
            val controler = displayer.display(toPos, world as ClientWorld) ?: return@forEach
            if (controler is ParticleControler) {
                data.particleControlerHandler(controler)
            }

            particles[uuid] = controler
            particleLocations[controler] = rl
        }
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

    /**
     * 为了提高傻逼StyleData的复用性 专门设置此类
     */
    open class StyleDataBuilder() {
        private var displayerBuilder: (UUID) -> ParticleDisplayer =
            { ParticleDisplayer.withSingle(TestEndRodEffect(it)) }
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