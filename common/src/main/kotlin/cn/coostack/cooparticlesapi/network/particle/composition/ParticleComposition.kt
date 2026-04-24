package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.helper.impl.composition.CompositionStatusHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI

/**
 * 因为不想写太多的ControlableBuffer， 而改用Codec自动生成数据 （同时避免写一万个Provider内部类）
 *
 * 所以重构ParticleStyle
 *
 * 首先强制 auto update
 *
 * 使用自动注册
 * @see cn.coostack.cooparticlesapi.annotations.composition.ParticleCompositionRegister
 */
abstract class ParticleComposition : ServerControler<ParticleComposition>,
    Controlable<ParticleComposition>, Tickable<ParticleComposition> {
    companion object {
        @JvmStatic
        fun encodeBase(data: ParticleComposition, buf: FriendlyByteBuf) {
            buf.writeUUID(data.controlUUID)
            buf.writeDouble(data.visibleRange)
            buf.writeBoolean(data.canceled)
            buf.writeVec3(data.position)
            buf.writeVec3(data.axis.toVector())
            buf.writeDouble(data.scale)
            buf.writeDouble(data.roll)
            buf.writeInt(data.status.displayStatus)
            buf.writeInt(data.status.closedInternal)
            buf.writeInt(data.status.current)
        }

        @JvmStatic
        fun decodeBase(instance: ParticleComposition, buf: FriendlyByteBuf) {
            instance.apply {
                controlUUID = buf.readUUID()
                visibleRange = buf.readDouble()
                canceled = buf.readBoolean()
                position = buf.readVec3()
                axis = buf.readVec3().asRelative()
                scale = buf.readDouble()
                roll = buf.readDouble()
                status.setStatus(buf.readInt())
                status.closedInternal = buf.readInt()
                status.updateCurrent(buf.readInt())
            }
        }
    }

    constructor(pos: Vec3, world: Level?) {
        this.position = pos
        this.world = world
    }

    constructor(world: Level) {
        this.world = world
    }

    constructor(world: Level, pos: Vec3) {
        this.world = world
        this.position = pos
    }


    var position: Vec3 = Vec3.ZERO
        internal set
    var world: Level? = null
        internal set

    /**
     * 粒子可视范围
     */
    var visibleRange = 256.0

    var scale = 1.0
        private set
    var client = false
        protected set

    var displayed = false
        protected set

    var canceled = false

    /**
     * 如果作为子 composition输入就一定要修改这个
     */
    var controlUUID = UUID.randomUUID()

    var axis = RelativeLocation.yAxis()

    var roll = 0.0

    val particles = ConcurrentHashMap<UUID, Controlable<*>>()

    /**
     * 排除掉 SingleParticleDisplayer 从而减少遍历次数
     */
    val controlerTicks = HashSet<Tickable<*>>()

    val particleLocations = ConcurrentHashMap<Controlable<*>, RelativeLocation>()

    val status = CompositionStatusHelper()

    /** 当粒子组合初始化时, 存储1倍缩放粒子组与原点的距离 */
    val particleDefaultLength = ConcurrentHashMap<UUID, Double>()

    // 防止频繁的toList造成的性能浪费

    internal val invokeQueue = ArrayList<ParticleComposition.() -> Unit>()
    protected val particleRotatedLocations = ArrayList<RelativeLocation>()

    abstract fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleComposition>

    abstract fun getParticles(): Map<CompositionData, RelativeLocation>

    abstract fun onDisplay()

    /**
     * 快捷设置 链式调用
     *
     * @param interval
     * @return
     */
    fun setDisabledInterval(interval: Int): ParticleComposition {
        this.status.closedInternal = interval
        return this
    }

    open fun beforeDisplay(map: Map<CompositionData, RelativeLocation>) {}

    override fun tick() {
        if (canceled || !displayed) {
            return
        }
        if (client) {
            Minecraft.getInstance().player?.let {
                if (it.position().distanceTo(position) > visibleRange) {
                    remove()
                    return
                }
            }
        }

        invokeQueue.forEach { it() }
        controlerTicks.forEach {
            it.tick()
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
        if (!canceled) {
            // remove过后 无法同步
            return
        }
    }

    open fun preRotateTo(map: Map<CompositionData, RelativeLocation>, to: RelativeLocation) {
        Math3DUtil.rotatePointsToPoint(
            map.values.toList(), to, axis
        )
        this.axis.copyFrom(to)
    }

    open fun preRotateAsAxis(map: Map<CompositionData, RelativeLocation>, axis: RelativeLocation, angle: Double) {
        Math3DUtil.rotateAsAxis(
            map.values.toList(), axis, angle
        )
        this.axis.copyFrom(axis)
    }

    open fun preRotateAsAxis(map: Map<CompositionData, RelativeLocation>, angle: Double) {
        Math3DUtil.rotateAsAxis(
            map.values.toList(), axis, angle
        )
    }

    protected fun toggleScaleDisplayed() {
        if (!displayed) {
            return
        }
        for (it in particleLocations) {
            val uuid = it.key.controlUUID()
            val len = particleDefaultLength[uuid]!!
            val value = it.value
            if (len in -1e-3..1e-3) continue
            value.multiply(len * scale / value.length())
        }
        toggleRelative()
    }

    open fun update(other: ParticleComposition) {
        this.visibleRange = other.visibleRange
        this.position = other.position
        this.canceled = other.canceled
        this.roll = other.roll
        this.controlUUID = other.controlUUID
        this.axis.copyFrom(other.axis)
        this.status.setStatus(other.status.displayStatus)
        this.status.closedInternal = other.status.closedInternal
        this.status.updateCurrent(other.status.current)
        CodecHelper.updateFields(this, other)
    }

    override fun addPreTickAction(action: ParticleComposition.() -> Unit): ParticleComposition {
        invokeQueue.add(action)
        return this
    }

    open fun clear(cancel: Boolean) {
        particles.forEach {
            it.value.remove()
        }
        controlerTicks.clear()
        particles.clear()
        particleLocations.clear()
        particleRotatedLocations.clear()
        particleDefaultLength.clear()
        this.canceled = cancel
    }

    open fun display() {
        if (displayed) {
            return
        }
        displayed = true
        this.client = world!!.isClientSide
        // 在服务器需要用来更新粒子个数 所以需要参与一次计算
        flush()
        status.loadControler(this)
        status.initHelper()
        if (!client) {
            // 服务器只负责数据同步 不负责粒子生成
            onDisplay()
            return
        }
        onDisplay()
    }

    fun toggleScale(locations: Map<CompositionData, RelativeLocation>) {
        if (canceled) {
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

    open fun flush() {
        if (particles.isNotEmpty()) {
            clear(false)
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
            val rel = entry.value
            // 减少一倍的new Vec3
            particle.teleportTo(
                position.add(rel.x, rel.y, rel.z)
            )
        }
    }


    override fun teleportTo(to: Vec3) {
        position = to
        toggleRelative()
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3(x, y, z))
    }

    override fun rotateToPoint(to: RelativeLocation) {
        if (!client) {
            axis.copyFrom(to)
            return
        }
        Math3DUtil.rotatePointsToPoint(
            particleRotatedLocations, to, axis
        )
        axis.copyFrom(to)
        toggleRelative()
    }

    override fun rotateToWithAngle(to: RelativeLocation, radian: Double) {
        this.roll += radian
        if (this.roll >= 2 * PI) {
            this.roll -= 2 * PI
        } else if (this.roll <= -2 * PI) {
            this.roll += 2 * PI
        }

        if (!client) {
            axis.copyFrom(to)
            return
        }
        Math3DUtil.rotateToWithRoll(
            particleRotatedLocations, axis, to, radian
        )
        axis.copyFrom(to)

        toggleRelative()
    }

    override fun rotateAsAxis(radian: Double) {
        this.roll += radian
        if (this.roll >= 2 * PI) {
            this.roll -= 2 * PI
        } else if (this.roll <= -2 * PI) {
            this.roll += 2 * PI
        }
        if (!client) {
            return
        }
        Math3DUtil.rotateAsAxis(
            particleRotatedLocations, axis, radian
        )
        toggleRelative()
    }

    override fun remove() {
        clear(true)
    }

    override fun remove(reason: RemoveReason) {
        remove()
    }

    override fun spawn(world: Level, pos: Vec3) {
        this.world = world
        this.position = pos
        // display
        ParticleCompositionManager.spawn(this)
    }

    override fun getValue(): ParticleComposition {
        return this
    }

    override fun controlUUID(): UUID {
        return controlUUID
    }

    override fun getControlObject(): ParticleComposition {
        return this
    }

    override fun isValid(): Boolean {
        return !canceled
    }

    protected open fun displayEntry(data: CompositionData, pos: RelativeLocation) {
        val uuid = data.uuid
        val displayer = data.displayerBuilder(uuid)
        if (displayer is ParticleDisplayer.SingleParticleDisplayer) {
            val controler = ControlParticleManager.createControl(uuid)
            controler.applyInitializedAction {
                for (function in data.singleParticleHandlers) {
                    function(this)
                }
            }
        }
        val toPos = position.add(pos.x, pos.y, pos.z)
        val controler = displayer.display(toPos, world as ClientLevel) ?: return
        if (controler is ParticleControler) {
            data.particleControlerHandlers.forEach { handler ->
                handler(controler)
            }
        }
        if (controler is Tickable<*>) {
            controlerTicks.add(controler)
        }
        particleRotatedLocations.add(pos)
        particles[uuid] = controler
        particleLocations[controler] = pos
    }


    protected open fun displayParticles() {
        if (!client) {
            return
        }
        val locations = getParticles()
        beforeDisplay(locations)
        toggleScale(locations)
        Math3DUtil.rotateAsAxis(locations.values.toList(), axis, roll)
        locations.forEach {
            displayEntry(it.key, it.value)
        }
    }

    open fun clone(): ParticleComposition {
        val new = runCatching {
            this::class.java.getDeclaredConstructor(Vec3::class.java, Level::class.java)
                .apply { isAccessible = true }
                .newInstance(Vec3.ZERO, null)
        }.getOrNull() ?: this::class.java.getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance()
        new.world = world
        new.update(this)
        return new
    }
}