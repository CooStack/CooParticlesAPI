package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.api.NetworkDirtyMarkable
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleTransitionMode
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.compat.CParticleControlable
import cn.coostack.cooparticlesapi.cparticle.compat.CParticleDisplayer
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.helper.impl.composition.CParticleCompositionAlphaHelper
import cn.coostack.cooparticlesapi.utils.helper.impl.composition.CompositionStatusHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3fc
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
    Controlable<ParticleComposition>, Tickable<ParticleComposition>, NetworkDirtyMarkable {
    companion object {
        @JvmStatic
        fun encodeBase(data: ParticleComposition, buf: FriendlyByteBuf) {
            buf.writeUUID(data.controlUUID)
            buf.writeDouble(data.visibleRange)
            buf.writeBoolean(data.canceled)
            buf.writeVec3(data.position)
            buf.writeVec3(data.axis.toVector())
            buf.writeDouble(data.scale)
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
        protected set
    var world: Level? = null
        internal set

    /**
     * 粒子可视范围
     */
    var visibleRange = 256.0
        set(value) {
            if (field == value) return
            field = value
            markNetworkStateDirty()
        }

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
    internal val postInvokeQueue = ArrayList<ParticleComposition.() -> Unit>()
    protected val particleRotatedLocations = ArrayList<RelativeLocation>()
    private val managedCParticleSystems = LinkedHashSet<CParticleSystem>()
    private val referencedCParticleSystems = LinkedHashMap<CParticleSystem, Int>()
    private val cParticleContainers = LinkedHashSet<Controlable<*>>()
    private val cParticleSystemConfigurations =
        LinkedHashMap<CParticleRenderLayer?, ArrayList<CParticleSystem.() -> Unit>>()
    private val cParticleLinearTransform = Matrix4f()
    private val cParticleRenderTransform = Matrix4f()
    private var cParticleCapacityHint = 1
    private var managedCParticleCount = 0
    private var gpuTransformActive = false
    private var cParticleAppliedScale = 1.0
    private var cParticleScaleCollapsed = false
    private var networkStateDirty = true
    private var networkFullDirty = true
    private var lastNetworkStatus = status.displayStatus
    private var lastNetworkStatusInterval = status.closedInternal

    /**
     * 标记此 Composition 的完整网络状态需要重新同步。
     *
     * 自定义同步字段优先使用 `var value by dirty(initial)`；普通 `@CodecField` 修改后需要显式调用本方法。
     */
    override fun markDirty() {
        if (world?.isClientSide != true) {
            networkFullDirty = true
            networkStateDirty = true
        }
    }

    internal fun markNetworkStateDirty() {
        if (world?.isClientSide != true) {
            networkStateDirty = true
        }
    }

    internal fun consumeNetworkFullDirty(): Boolean {
        val dirty = networkFullDirty
        networkFullDirty = false
        return dirty
    }

    internal fun hasNetworkFullDirty(): Boolean = networkFullDirty

    internal fun hasNetworkStateDirty(): Boolean {
        updateNetworkStatusDirty()
        return networkStateDirty
    }

    internal fun consumeNetworkStateDirty(): Boolean {
        updateNetworkStatusDirty()
        val dirty = networkStateDirty
        networkStateDirty = false
        return dirty
    }

    private fun updateNetworkStatusDirty() {
        if (lastNetworkStatus != status.displayStatus ||
            lastNetworkStatusInterval != status.closedInternal
        ) {
            networkStateDirty = true
            lastNetworkStatus = status.displayStatus
            lastNetworkStatusInterval = status.closedInternal
        }
    }

    internal fun applyRemoteState(
        position: Vec3,
        visibleRange: Double,
        scale: Double,
        displayStatus: Int,
        closedInterval: Int,
        current: Int,
    ) {
        this.visibleRange = visibleRange
        if (this.position != position) teleportTo(position)
        if (this.scale != scale) scale(scale)
        status.setStatus(displayStatus)
        status.closedInternal = closedInterval
        status.updateCurrent(current)
    }

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
        markNetworkStateDirty()
        return this
    }

    /**
     * 配置此 composition 自动创建的所有 CParticle systems。
     * 名称、容量、渲染层、模式和生命周期由 composition 管理。
     */
    fun configureCParticleSystem(configure: CParticleSystem.() -> Unit): ParticleComposition {
        return setCParticleSystemConfiguration(null, configure)
    }

    /** 只配置指定渲染层的 system。 */
    fun configureCParticleSystem(
        layer: CParticleRenderLayer,
        configure: CParticleSystem.() -> Unit,
    ): ParticleComposition {
        return setCParticleSystemConfiguration(layer, configure)
    }

    private fun setCParticleSystemConfiguration(
        layer: CParticleRenderLayer?,
        configure: CParticleSystem.() -> Unit,
    ): ParticleComposition {
        cParticleSystemConfigurations.getOrPut(layer) { ArrayList() }.add(configure)
        managedCParticleSystems.forEach { system ->
            if (layer == null || system.layer == layer) configure(system)
        }
        return this
    }

    /** 返回此 composition 在指定层自动创建的 system；尚未显示时返回 null。 */
    fun getCParticleSystem(layer: CParticleRenderLayer): CParticleSystem? {
        return managedCParticleSystems.firstOrNull { !it.released && it.layer == layer }
    }

    /** 返回此 composition 当前持有的 systems 快照。 */
    fun getCParticleSystems(): List<CParticleSystem> {
        removeReleasedCParticleSystems()
        return referencedCParticleSystems.keys.toList()
    }

    internal fun getCParticleContainers(): List<Controlable<*>> =
        cParticleContainers.toList()

    protected fun registerCParticleNode(controler: Controlable<*>) {
        when (controler) {
            is CParticleControlable -> {
                removeReleasedCParticleSystems()
                referencedCParticleSystems.merge(controler.system, 1, Int::plus)
                controler.system.visibleRange = Double.MAX_VALUE
                if (controler.system in managedCParticleSystems) {
                    managedCParticleCount++
                }
            }

            is ParticleComposition,
            is ParticleGroupStyle,
            is ControlableParticleGroup -> cParticleContainers.add(controler)
        }
    }

    protected fun unregisterCParticleNode(controler: Controlable<*>) {
        when (controler) {
            is CParticleControlable -> {
                val count = referencedCParticleSystems[controler.system] ?: return
                if (count <= 1) referencedCParticleSystems.remove(controler.system)
                else referencedCParticleSystems[controler.system] = count - 1
                if (controler.system in managedCParticleSystems) {
                    managedCParticleCount = (managedCParticleCount - 1).coerceAtLeast(0)
                }
            }

            is ParticleComposition,
            is ParticleGroupStyle,
            is ControlableParticleGroup -> cParticleContainers.remove(controler)
        }
    }

    private fun removeReleasedCParticleSystems() {
        managedCParticleSystems.removeIf { it.released }
        referencedCParticleSystems.keys.removeIf { it.released }
        managedCParticleCount = referencedCParticleSystems.entries.sumOf { (system, count) ->
            if (system in managedCParticleSystems) count else 0
        }
    }

    /**
     * 对当前 composition 创建的所有 CParticle systems 播放同一段 GPU 视觉过渡。
     *
     * Example: `playCParticleVisualTransition(20f, scaleCurve = curve)` 会统一缩放当前 systems。
     * Forbidden: 不要用它修改粒子生成时的基础尺寸。
     *
     * @param durationTicks 过渡时长，单位 tick
     * @param alphaCurve 不透明度倍率曲线
     * @param scaleCurve 等比缩放倍率曲线
     * @param colorFrom 可选起始颜色
     * @param colorTo 可选结束颜色
     * @param mode 过渡结束后的行为
     * @return 当前 composition
     */
    @JvmOverloads
    fun playCParticleVisualTransition(
        durationTicks: Float,
        alphaCurve: CParticleCurve? = null,
        scaleCurve: CParticleCurve? = null,
        colorFrom: Vector3fc? = null,
        colorTo: Vector3fc? = null,
        mode: CParticleTransitionMode = CParticleTransitionMode.HOLD_END,
    ): ParticleComposition {
        return playCParticleVisualTransitionInternal(
            durationTicks,
            alphaCurve,
            scaleCurve,
            colorFrom,
            colorTo,
            mode,
            restart = false,
        )
    }

    /**
     * 播放 composition 级 GPU 视觉过渡，并允许强制重新开始相同配置。
     *
     * Example: `playCParticleVisualTransition(20f, true, scaleCurve = curve)` 会重置进度。
     * Forbidden: 不要用它修改粒子生成时的基础尺寸。
     *
     * @param durationTicks 过渡时长，单位 tick
     * @param restart 是否强制从头播放
     * @param alphaCurve 不透明度倍率曲线
     * @param scaleCurve 等比缩放倍率曲线
     * @param colorFrom 可选起始颜色
     * @param colorTo 可选结束颜色
     * @param mode 过渡结束后的行为
     * @return 当前 composition
     */
    @JvmOverloads
    fun playCParticleVisualTransition(
        durationTicks: Float,
        restart: Boolean,
        alphaCurve: CParticleCurve? = null,
        scaleCurve: CParticleCurve? = null,
        colorFrom: Vector3fc? = null,
        colorTo: Vector3fc? = null,
        mode: CParticleTransitionMode = CParticleTransitionMode.HOLD_END,
    ): ParticleComposition {
        return playCParticleVisualTransitionInternal(
            durationTicks,
            alphaCurve,
            scaleCurve,
            colorFrom,
            colorTo,
            mode,
            restart,
        )
    }

    /**
     * 把同一组过渡参数分发给当前 composition 管理的 systems。
     *
     * Example: 公开重载通过本方法统一传递 [restart]。
     * Forbidden: 不要在这里缓存已经释放的 system。
     *
     * @param durationTicks 过渡时长，单位 tick
     * @param alphaCurve 不透明度倍率曲线
     * @param scaleCurve 等比缩放倍率曲线
     * @param colorFrom 可选起始颜色
     * @param colorTo 可选结束颜色
     * @param mode 过渡结束后的行为
     * @param restart 是否强制从头播放
     * @return 当前 composition
     */
    private fun playCParticleVisualTransitionInternal(
        durationTicks: Float,
        alphaCurve: CParticleCurve?,
        scaleCurve: CParticleCurve?,
        colorFrom: Vector3fc?,
        colorTo: Vector3fc?,
        mode: CParticleTransitionMode,
        restart: Boolean,
    ): ParticleComposition {
        getCParticleSystems().forEach { system ->
            system.playVisualTransition(
                durationTicks = durationTicks,
                restart = restart,
                alphaCurve = alphaCurve,
                scaleCurve = scaleCurve,
                colorFrom = colorFrom,
                colorTo = colorTo,
                mode = mode,
            )
        }
        return this
    }

    /** 停止当前 composition 的 CParticle GPU 视觉过渡。 */
    @JvmOverloads
    fun stopCParticleVisualTransition(reset: Boolean = false): ParticleComposition {
        getCParticleSystems().forEach { it.stopVisualTransition(reset) }
        return this
    }

    /**
     * 扫描此 composition 的完整运行时子树，为所有 CParticle systems 播放同一段 GPU alpha 过渡。
     * 子树包括嵌套 composition、ParticleGroupStyle 和 ControlableParticleGroup。
     * 过渡曲线会覆盖粒子实例的 alpha，再与粒子、system 和普通视觉过渡的 alpha 曲线相乘。
     *
     * @param durationTicks 过渡时长，单位为 tick，必须为有限正数
     * @param alphaCurve 用于覆盖粒子实例 alpha 的曲线
     * @param mode 过渡结束后的行为
     * @param restart 是否强制替换相同配置
     * @return 当前 composition
     * @throws IllegalArgumentException 当 [durationTicks] 不是有限正数时抛出
     */
    @JvmOverloads
    fun playCParticleAlphaTransition(
        durationTicks: Float,
        alphaCurve: CParticleCurve,
        mode: CParticleTransitionMode = CParticleTransitionMode.HOLD_END,
        restart: Boolean = false,
    ): ParticleComposition {
        return CParticleCompositionAlphaHelper.play(
            composition = this,
            durationTicks = durationTicks,
            alphaCurve = alphaCurve,
            mode = mode,
            restart = restart,
        )
    }

    /** 停止此 composition 完整运行时子树中的 CParticle alpha 过渡。 */
    @JvmOverloads
    fun stopCParticleAlphaTransition(reset: Boolean = false): ParticleComposition {
        return CParticleCompositionAlphaHelper.stop(this, reset)
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
        val tickIterator = controlerTicks.iterator()
        while (tickIterator.hasNext()) {
            val controler = tickIterator.next()
            if (controler is CParticleControlable && !controler.valid) {
                tickIterator.remove()
                continue
            }
            controler.tick()
            if (controler is CParticleControlable && !controler.valid) {
                tickIterator.remove()
            }
        }
        postInvokeQueue.forEach { it() }
    }

    open fun scale(new: Double) {
        if (new < 0.0) {
            CooParticlesConstants.logger.error("scale can not be less than zero")
            return
        }
        if (scale == new) return
        scale = new
        markNetworkStateDirty()
        // 如果没有创建, 那么此处的环境100%是创建此对象时使用的环境
        // 多为服务端(除非有人使在Client环境创建了这个类)
        if (displayed) {
            if (gpuTransformActive) {
                applyGpuScale(new)
            } else {
                toggleScaleDisplayed()
            }
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

    protected open fun toggleScaleDisplayed() {
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
        val newAxis = other.axis.clone()

        this.visibleRange = other.visibleRange
        if (this.position != other.position) {
            teleportTo(other.position)
        }
        this.canceled = other.canceled

        this.controlUUID = other.controlUUID
        if (!client || !displayed) {
            this.axis.copyFrom(newAxis)
        }
        this.status.setStatus(other.status.displayStatus)
        this.status.closedInternal = other.status.closedInternal
        this.status.updateCurrent(other.status.current)
        CodecHelper.updateFields(this, other)
    }

    override fun addPreTickAction(action: ParticleComposition.() -> Unit): ParticleComposition {
        invokeQueue.add(action)
        return this
    }

    override fun addPreTickActionPost(action: ParticleComposition.() -> Unit): ParticleComposition {
        postInvokeQueue.add(action)
        return this
    }

    /**
     * 清除当前显示内容，并按 [cancel] 决定是否结束本次生命周期。
     *
     * 示例：`clear(true)` 会销毁子节点并从客户端活动计数中移除本实例。
     * 禁止用 `clear(false)` 表示最终移除，因为该模式用于保留 displayed 状态后重新生成内容。
     *
     * @param cancel `true` 表示结束生命周期，`false` 表示仅刷新显示内容
     */
    open fun clear(cancel: Boolean) {
        particles.forEach {
            it.value.remove()
        }
        resetGpuTransformState()
        controlerTicks.clear()
        particles.clear()
        particleLocations.clear()
        particleRotatedLocations.clear()
        particleDefaultLength.clear()
        this.canceled = cancel
        if (cancel) {
            displayed = false
            ParticleCompositionManager.setClientLoaded(this, false)
        }
    }

    /**
     * 在服务端重新生成 Composition 前重置旧生命周期状态。
     *
     * 示例：同一实例再次调用 `spawn(...)` 时先执行本方法，避免沿用 canceled 状态。
     * 禁止在仍需保留当前客户端显示内容时调用。
     */
    internal fun resetLifecycleForSpawn() {
        ParticleCompositionManager.setClientLoaded(this, false)
        canceled = false
        displayed = false
        networkStateDirty = true
        networkFullDirty = true
        lastNetworkStatus = status.displayStatus
        lastNetworkStatusInterval = status.closedInternal
    }

    /**
     * 根据当前世界进入服务端同步或客户端显示生命周期。
     *
     * 示例：客户端直接显示的嵌套 Composition 会在此登记为活动实例。
     * 禁止在 [world] 尚未设置时调用。
     */
    open fun display() {
        if (displayed) {
            return
        }
        displayed = true
        this.client = world!!.isClientSide
        if (client) {
            ParticleCompositionManager.setClientLoaded(this, true)
        }
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
        if (gpuTransformActive) {
            syncGpuTransform()
            return
        }
        val staleControls = ArrayList<Controlable<*>>()
        val iterator = particleLocations.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val particle = entry.key
            val rel = entry.value
            if (particle is ParticleControler && !particle.isBound) {
                staleControls.add(particle)
                continue
            }
            // 减少一倍的new Vec3
            try {
                particle.teleportTo(
                    position.add(rel.x, rel.y, rel.z)
                )
            } catch (error: IllegalStateException) {
                if (particle is ParticleControler && !particle.isBound) {
                    staleControls.add(particle)
                    continue
                }
                throw error
            }
        }
        staleControls.forEach { removeDisplayedControl(it) }
    }

    private fun removeDisplayedControl(control: Controlable<*>) {
        unregisterCParticleNode(control)
        control.remove(RemoveReason.QUEUE)
        if (control is Tickable<*>) {
            controlerTicks.remove(control)
        }
        particleLocations.remove(control)
        particles.remove(control.controlUUID())
        particleDefaultLength.remove(control.controlUUID())
        particleRotatedLocations.clear()
        particleLocations.values.forEach { particleRotatedLocations.add(it) }
    }

    override fun teleportTo(to: Vec3) {
        if (position == to) return
        position = to
        markNetworkStateDirty()
        toggleRelative()
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3(x, y, z))
    }

    override fun rotateToPoint(to: RelativeLocation) {
        if (!client) {
            axis.copyFrom(to)
            ParticleCompositionManager.sendRotate(this, to, 0.0)
            return
        }
        if (gpuTransformActive) {
            applyGpuRotationTo(axis, to, 0.0)
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
            ParticleCompositionManager.sendRotate(this, to, radian)
            return
        }
        if (gpuTransformActive) {
            applyGpuRotationTo(axis, to, radian)
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
            ParticleCompositionManager.sendRotate(this, null, radian)
            return
        }
        if (gpuTransformActive) {
            applyGpuAxisRotation(axis, radian)
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

    fun setPositionWithoutToggle(pos: Vec3) {
        this.position = pos
    }

    internal fun applyRemoteRotation(to: RelativeLocation?, radian: Double) {
        if (!client || !displayed) {
            to?.let { axis.copyFrom(it) }
            return
        }
        if (gpuTransformActive) {
            if (to == null) {
                applyGpuAxisRotation(axis, radian)
            } else {
                applyGpuRotationTo(axis, to, radian)
                axis.copyFrom(to)
            }
            return
        }
        if (particleRotatedLocations.isEmpty()) {
            to?.let { axis.copyFrom(it) }
            return
        }
        if (to == null) {
            Math3DUtil.rotateAsAxis(particleRotatedLocations, axis, radian)
        } else {
            Math3DUtil.rotateToWithRoll(particleRotatedLocations, axis, to, radian)
            axis.copyFrom(to)
        }
        toggleRelative()
    }

    /**
     * 显示一个相对坐标对应的粒子节点。
     *
     * 示例：普通粒子以 [position] 为原点，启用整组变换的 managed CParticle 先计算实际渲染坐标。
     * 禁止把 system 局部坐标直接当作世界坐标，否则光照和位置相关纹理会从错误位置采样。
     *
     * @param data 粒子显示方式和初始化动作
     * @param pos 已完成初始缩放与旋转的相对坐标
     */
    protected open fun displayEntry(data: CompositionData, pos: RelativeLocation) {
        val uuid = data.uuid
        val displayer = data.displayerBuilder(uuid)
        val managedCParticleSystem = if (displayer is CParticleDisplayer) {
            data.cParticleHandlers.forEach(displayer::applyParticleInit)
            bindManagedSystem(displayer)
        } else null
        if (displayer is ParticleDisplayer.SingleParticleDisplayer) {
            val controler = ControlParticleManager.createControl(uuid)
            controler.applyInitializedAction {
                for (function in data.singleParticleHandlers) {
                    function(this)
                }
            }
        }
        val toPos = resolveCParticleSpawnPosition(pos, managedCParticleSystem)
        val clientWorld = world as ClientLevel
        val controler = if (displayer is CParticleDisplayer) {
            displayer.display(
                toPos,
                clientWorld,
                resolveCParticleStoragePosition(pos, managedCParticleSystem),
            )
        } else {
            displayer.display(toPos, clientWorld)
        } ?: let {
            CooParticlesConstants.logger.error("display生成了null 错误target类型 ${displayer::class.java.name}")
            return
        }
        if (controler is ParticleControler) {
            data.particleControlerHandlers.forEach { handler ->
                handler(controler)
            }
        }
        if (controler is CParticleControlable) {
            data.cParticleControlerHandlers.forEach { handler ->
                handler(controler)
            }
        }
        registerCParticleNode(controler)
        if (controler is CParticleControlable && controler.hasTickActions) {
            controlerTicks.add(controler)
        } else if (controler is Tickable<*> && controler !is CParticleControlable) {
            controlerTicks.add(controler)
        }
        trackDisplayedParticleLocation(pos)
        particles[uuid] = controler
        particleLocations[controler] = pos
    }


    protected open fun displayParticles() {
        if (!client) {
            return
        }
        val locations = getParticles()
        prepareGpuComposition(locations.size)
        beforeDisplay(locations)
        toggleScale(locations)
        Math3DUtil.rotatePointsToPoint(locations.values.toList(), axis, RelativeLocation.yAxis())
        Math3DUtil.rotateAsAxis(locations.values.toList(), axis, roll)
        locations.forEach {
            displayEntry(it.key, it.value)
        }
        refreshGpuTransformMode()
    }

    protected fun prepareGpuComposition(particleCount: Int) {
        cParticleCapacityHint = particleCount.coerceAtLeast(1)
        cParticleAppliedScale = if (scale > 1e-7) scale else 1.0
        cParticleScaleCollapsed = scale <= 1e-7
    }

    protected open fun trackDisplayedParticleLocation(pos: RelativeLocation) {
        particleRotatedLocations.add(pos)
    }

    /**
     * 为未绑定的 CParticle displayer 取得当前 composition 专用的 system。
     *
     * 示例：同一 composition、渲染层和纹理绑定会复用同一个 system。
     * 禁止覆盖调用方已经绑定的 system；这种情况返回 `null` 并保留原有行为。
     *
     * @param displayer 待绑定的 GPU 粒子显示器
     * @return 本次绑定的 managed system；无法绑定或已有外部绑定时返回 `null`
     */
    private fun bindManagedSystem(displayer: CParticleDisplayer): CParticleSystem? {
        if (displayer.hasBoundSystem) return null
        val layerName = displayer.layer.name.lowercase()
        val name = "composition/$controlUUID/$layerName"
        val resolvedTextures = displayer.resolveTexturesAt(position)
        if (!resolvedTextures.isValid) return null
        val bindingKey = resolvedTextures.base.bindingKey
        val maskBindingKey = resolvedTextures.mask?.bindingKey
        val existing = CParticleSystemManager.getSystem(
            name,
            CParticleSystemMode.SCRIPTED,
            displayer.layer,
            bindingKey,
            maskBindingKey,
        )
        if (existing != null && existing.capacity < cParticleCapacityHint) {
            CParticleSystemManager.removeSystem(
                name,
                CParticleSystemMode.SCRIPTED,
                displayer.layer,
                bindingKey,
                maskBindingKey,
            )
        }
        val target = CParticleSystemManager.getOrCreateSystem(
            name,
            cParticleCapacityHint,
            displayer.layer,
            CParticleSystemMode.SCRIPTED,
            bindingKey,
            autoReleaseWhenEmpty = true,
            maskTextureBindingKey = maskBindingKey,
        )
        if (!displayer.bindSystemIfAbsent(target)) return null
        target.setOriginIfEmpty(position)
        target.visibleRange = Double.MAX_VALUE
        cParticleSystemConfigurations[null]?.forEach { it(target) }
        cParticleSystemConfigurations[displayer.layer]?.forEach { it(target) }
        managedCParticleSystems.add(target)
        if (gpuTransformActive) {
            syncGpuTransform()
        }
        return target
    }

    /**
     * 计算粒子显示与环境采样使用的世界坐标。
     *
     * 示例：整组变换启用后，序列后续粒子使用 system 当前矩阵换算真实渲染位置。
     * 禁止在这里返回槽位局部坐标；[CParticleSystem] 会在写入时自行逆变换。
     *
     * @param pos composition 中保存的相对坐标
     * @param managedSystem 当前 composition 管理的 GPU system，普通显示器传 `null`
     * @return 写入显示器的世界坐标
     */
    internal fun resolveCParticleSpawnPosition(
        pos: RelativeLocation,
        managedSystem: CParticleSystem?,
    ): Vec3 {
        if (!gpuTransformActive || managedSystem == null) {
            return position.add(pos.x, pos.y, pos.z)
        }
        val transformed = managedSystem.groupTransform.transformPosition(
            Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
        )
        return managedSystem.origin.add(
            transformed.x.toDouble(),
            transformed.y.toDouble(),
            transformed.z.toDouble(),
        )
    }

    /**
     * 返回整组 GPU 变换启用时使用的稳定槽位坐标。
     *
     * 示例：system 原点为 `(10, 20, 30)` 时，相对坐标 `(2, 3, 4)` 写为 `(12, 23, 34)`。
     * 禁止在 CPU 路径提供该坐标；此时 system 需要按现存矩阵逆变换真实世界坐标。
     *
     * @param pos composition 保存的相对坐标
     * @param managedSystem 当前 composition 管理的 GPU system
     * @return 槽位写入坐标；不使用整组 GPU 变换时返回 `null`
     */
    internal fun resolveCParticleStoragePosition(
        pos: RelativeLocation,
        managedSystem: CParticleSystem?,
    ): Vec3? {
        val system = managedSystem?.takeIf { gpuTransformActive } ?: return null
        return system.origin.add(pos.x, pos.y, pos.z)
    }

    private fun applyGpuAxisRotation(rotationAxis: RelativeLocation, radian: Double) {
        val axisVector = normalizedAxis(rotationAxis)
        val delta = Matrix4f().rotate(radian.toFloat(), axisVector)
        delta.mul(cParticleLinearTransform, cParticleLinearTransform)
        syncGpuTransform()
    }

    private fun applyGpuRotationTo(from: RelativeLocation, to: RelativeLocation, radian: Double) {
        val fromVector = normalizedAxis(from)
        val normalizedFrom = RelativeLocation.of(fromVector)
        val normalizedTo = RelativeLocation.of(normalizedAxis(to))
        val alignRotation = Quaternionf()
            .rotateY(-Math3DUtil.getYawFromLocation(normalizedTo).toFloat())
            .rotateX(-Math3DUtil.getPitchFromLocation(normalizedTo).toFloat())
            .mul(
                Quaternionf()
                    .rotateY(Math3DUtil.getYawFromLocation(normalizedFrom).toFloat())
                    .rotateLocalX(Math3DUtil.getPitchFromLocation(normalizedFrom).toFloat())
            )
        if (radian != 0.0) {
            alignRotation.rotateAxis(radian.toFloat(), fromVector)
        }
        val align = Matrix4f().rotation(alignRotation)
        align.mul(cParticleLinearTransform, cParticleLinearTransform)
        syncGpuTransform()
    }

    private fun applyGpuScale(newScale: Double) {
        if (newScale <= 1e-7) {
            cParticleScaleCollapsed = true
            syncGpuTransform()
            return
        }
        val factor = (newScale / cParticleAppliedScale).toFloat()
        Matrix4f().scaling(factor).mul(cParticleLinearTransform, cParticleLinearTransform)
        cParticleAppliedScale = newScale
        cParticleScaleCollapsed = false
        syncGpuTransform()
    }

    protected fun refreshGpuTransformMode() {
        val wasActive = gpuTransformActive
        gpuTransformActive = managedCParticleCount > 0 &&
                managedCParticleCount == particles.size &&
                controlerTicks.none { it is CParticleControlable }
        if (gpuTransformActive) {
            syncGpuTransform()
            if (!wasActive) {
                managedCParticleSystems.forEach { it.snapGroupTransform() }
            }
        }
    }

    private fun syncGpuTransform() {
        val linear = if (cParticleScaleCollapsed) {
            cParticleRenderTransform.set(cParticleLinearTransform)
                .m00(0f).m01(0f).m02(0f)
                .m10(0f).m11(0f).m12(0f)
                .m20(0f).m21(0f).m22(0f)
        } else {
            cParticleLinearTransform
        }
        managedCParticleSystems.forEach { system ->
            system.groupTransform
                .set(linear)
                .m30((position.x - system.origin.x).toFloat())
                .m31((position.y - system.origin.y).toFloat())
                .m32((position.z - system.origin.z).toFloat())
        }
    }

    private fun resetGpuTransformState() {
        managedCParticleSystems.forEach { it.groupTransform.identity() }
        managedCParticleSystems.clear()
        referencedCParticleSystems.clear()
        cParticleContainers.clear()
        cParticleLinearTransform.identity()
        cParticleRenderTransform.identity()
        cParticleCapacityHint = 1
        managedCParticleCount = 0
        gpuTransformActive = false
        cParticleAppliedScale = 1.0
        cParticleScaleCollapsed = false
    }

    private fun normalizedAxis(value: RelativeLocation): Vector3f {
        val result = Vector3f(value.x.toFloat(), value.y.toFloat(), value.z.toFloat())
        return if (result.lengthSquared() > 1e-12f) result.normalize() else result.set(0f, 1f, 0f)
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
