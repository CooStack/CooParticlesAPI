package cn.coostack.cooparticlesapi.network.particle.style

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.impl.TestEndRodEffect
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.MathDataUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import com.sun.jna.platform.win32.OleAuto
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.SortedMap
import java.util.UUID
import kotlin.collections.toList
import kotlin.math.PI


/**
 * 几个免除手动书写 SequencedParticleStyle的方法
 *
 * 在这里不建议使用 autoToggle方法
 * 会导致一些奇奇怪怪的问题 (原因未知)
 * 可能是 部分index同步错误导致
 */
abstract class SequencedParticleStyle(visibleRange: Double = 32.0, uuid: UUID = UUID.randomUUID()) :
    ParticleGroupStyle(visibleRange, uuid) {
    companion object {
        const val CREATE_PARTICLE = 1
        const val DELETE_PARTICLE = 0
    }


    class SortedStyleData(displayerBuilder: (UUID) -> ParticleDisplayer, val order: Int) :
        StyleData(displayerBuilder), Comparable<SortedStyleData> {
        override fun compareTo(other: SortedStyleData): Int {
            return order - other.order
        }
    }


    class SortedStyleDataBuilder {
        private var displayerBuilder: (UUID) -> ParticleDisplayer =
            { ParticleDisplayer.withSingle(TestEndRodEffect(it)) }
        private val particleHandlers = mutableListOf<ControlableParticle.() -> Unit>()
        private val particleControlerHandlers = mutableListOf<ParticleControler.() -> Unit>()
        fun addParticleHandler(
            builder: ControlableParticle.() -> Unit
        ): SortedStyleDataBuilder {
            particleHandlers.add(builder)
            return this
        }

        fun addParticleControlerHandler(
            builder: ParticleControler.() -> Unit
        ): SortedStyleDataBuilder {
            particleControlerHandlers.add(builder)
            return this
        }

        fun clearParticleHandlers(): SortedStyleDataBuilder {
            particleHandlers.clear()
            return this
        }

        fun clearParticleControlers(): SortedStyleDataBuilder {
            particleControlerHandlers.clear()
            return this
        }

        fun removeHandler(index: Int): SortedStyleDataBuilder {
            if (index in particleHandlers.indices) particleHandlers.removeAt(index)
            return this
        }

        fun removeParticleControler(index: Int): SortedStyleDataBuilder {
            if (index in particleControlerHandlers.indices) particleControlerHandlers.removeAt(index)
            return this
        }

        fun displayer(builder: (UUID) -> ParticleDisplayer): SortedStyleDataBuilder {
            this.displayerBuilder = builder
            return this
        }

        fun build(order: Int): SortedStyleData = SortedStyleData(displayerBuilder, order)
            .withParticleHandler {
                particleHandlers.forEach { it() }
            }.withParticleControlerHandler {
                particleControlerHandlers.forEach { it() }
            } as SortedStyleData
    }

    /**
     * 在服务器处是0 size的大小
     * client用于存储某个粒子/粒子组 的展示情况
     *
     * 用于彻底解除强制重写的烦恼
     *
     * FIXED 修复多余的内存占用
     */
    val displayedStatus: LongArray by lazy {
        val count = getParticlesCount()
        var page = count / 64
        if (count % 64 > 0) {
            page++
        }
        LongArray(page)
    }


    /**
     * 统计对应的粒子顺序 (uuid)
     */
    protected var sequencedParticles = ArrayList<Pair<SortedStyleData, RelativeLocation>>()
        private set


    var displayedParticleCount = 0
        private set(value) {
            field = value.coerceAtLeast(0)
        }

    /**
     * 此参数所指向的状态为false
     *
     * 服务器可用
     * 不会同步到客户端
     */
    var particleLinkageDisplayCurrentIndex = 0
        private set

    /**
     * @return 当前粒子样式的样式个数 (客户端执行getCurrentFramesSequenced返回的集合长度)
     * 请手动计算粒子个数
     * 或者维护一个变量 支持服务器和客户端同时生成
     * 请勿直接在服务器中调用 getCurrentFramesSequenced().size
     * 否则会导致服务器崩溃
     */
    abstract fun getParticlesCount(): Int
    abstract fun getCurrentFramesSequenced(): SortedMap<SortedStyleData, RelativeLocation>
    abstract fun writePacketArgsSequenced(): Map<String, ParticleControlerDataBuffer<*>>
    abstract fun readPacketArgsSequenced(args: Map<String, ParticleControlerDataBuffer<*>>)

    /**
     * 服务器发包 -> index_status_change -> intArray index, status
     */
    fun addSingle() {
        if (client) {
            // 适配在纯客户端页面的添加
            // 由于和服务器无关所以大多数参数无需设置
            if (particleLinkageDisplayCurrentIndex >= getParticlesCount()) {
                return
            }
            toggleFromStatus(particleLinkageDisplayCurrentIndex++, true)
            return
        }
        displayedParticleCount++
        if (getStatus(particleLinkageDisplayCurrentIndex)) {
            return
        }
        setStatus(particleLinkageDisplayCurrentIndex, true)
        // 处理服务器
        // 发包
        val args = buildChangeSingleStatusArgs(
            particleLinkageDisplayCurrentIndex++,
            true
        )
        change({}, mapOf(args))
    }

    /**
     * 服务器发包 -> indexes_status_change_arg -> intArray indexes
     *              indexes_status_change_method -> intArray status method
     */
    fun addMultiple(count: Int) {
        if (client) {
            repeat(count) {
                addSingle()
            }
            return
        }
        if (count <= 0) {
            return
        }
        if (count == 1) {
            addSingle()
            return
        }
        displayedParticleCount += count
        // 处理服务器
        // 发包
        val indexes = particleLinkageDisplayCurrentIndex..<particleLinkageDisplayCurrentIndex + count
        for (i in indexes) {
            setStatus(i, true)
        }
        val args =
            buildChangeMultipleStatusArgs(
                indexes.toList().toIntArray(),
                true
            )
        change({
            particleLinkageDisplayCurrentIndex =
                (particleLinkageDisplayCurrentIndex + count).coerceAtMost(getParticlesCount() - 1)
        }, args)
    }

    fun removeSingle() {
        if (client) {
            if (particleLinkageDisplayCurrentIndex <= 0) return
            toggleFromStatus(particleLinkageDisplayCurrentIndex--, false)
        }
        displayedParticleCount--
        setStatus(particleLinkageDisplayCurrentIndex, false)
        // 处理服务器
        // 发包
        val args = buildChangeSingleStatusArgs(
            particleLinkageDisplayCurrentIndex--,
            false
        )
        change({}, mapOf(args))
    }

    fun removeMultiple(count: Int) {
        if (client) {
            repeat(count) {
                removeSingle()
            }
            return
        }
        if (count <= 0) {
            return
        }
        if (count == 1) {
            removeSingle()
            return
        }

        displayedParticleCount -= count
        // 处理服务器
        // 发包
        val indexes = particleLinkageDisplayCurrentIndex - count + 1..particleLinkageDisplayCurrentIndex
        for (i in indexes) {
            setStatus(i, false)
        }
        val args =
            buildChangeMultipleStatusArgs(
                indexes.toList().toIntArray(),
                false
            )
        change({
            particleLinkageDisplayCurrentIndex =
                (particleLinkageDisplayCurrentIndex - count).coerceAtLeast(0)
        }, args)
    }


    fun changeParticlesStatus(indexes: IntArray, status: Boolean) {
        if (!client) {
            val args = buildChangeMultipleStatusArgs(indexes, status)
            change(args)
            return
        }
        for (index in indexes) {
            changeSingleStatus(index, status)
        }
    }

    fun changeSingleStatus(index: Int, status: Boolean) {
        if (!client) {
            val arg = buildChangeSingleStatusArgs(index, status)
            change(mapOf(arg))
            return
        }
        toggleFromStatus(index, status)
    }


    private fun buildChangeMultipleStatusArgs(
        indexes: IntArray,
        status: Boolean,
    ): Map<String, ParticleControlerDataBuffer<*>> {
        return mapOf(
            "indexes_status_change_arg" to ParticleControlerDataBuffers.intArray(indexes),
            "indexes_status_change_status" to ParticleControlerDataBuffers.intArray(
                intArrayOf(if (status) CREATE_PARTICLE else DELETE_PARTICLE, displayedParticleCount)
            )
        )
    }

    private fun buildChangeSingleStatusArgs(
        index: Int,
        status: Boolean,
    ): Pair<String, ParticleControlerDataBuffer<IntArray>> {
        return "index_status_change" to ParticleControlerDataBuffers.intArray(
            intArrayOf(
                index,
                if (status) CREATE_PARTICLE else DELETE_PARTICLE, displayedParticleCount
            )
        )
    }

    open fun beforeDisplay(styles: SortedMap<SortedStyleData, RelativeLocation>) {}
    fun toggleScale(locations: SortedMap<SortedStyleData, RelativeLocation>) {
        super.toggleScale(locations.toMap())
    }

    override fun scale(new: Double) {
        if (new < 0.0) {
            CooParticleAPI.logger.error("scale can not be less than zero")
            return
        }
        scale = new
        if (displayed) {
            toggleScaleDisplayed()
        }
    }

    override fun display(pos: Vec3d, world: World) {
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
        toggleDataStatus()
        onDisplay()
    }

    /**
     * 清空粒子生成状态
     * 重新生成粒子
     */
    override fun flush() {
        if (particles.isNotEmpty()) {
            clear(true)
            clearStatus()
        }
        displayParticles()
    }

    override fun clear(valid: Boolean) {
        sequencedParticles.clear()
        super.clear(valid)
    }

    /**
     * 新的参数用于传输 粒子启用顺序
     */
    override fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>> {
        return mapOf(
            "status" to ParticleControlerDataBuffers.longArray(displayedStatus),
            "displayed_particle_count" to ParticleControlerDataBuffers.int(displayedParticleCount),
            *writePacketArgsSequenced().map { it.key to it.value }.toTypedArray()
        )
    }

    override fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
        args["status"]?.let {
            val array = it.loadedValue as LongArray
            System.arraycopy(array, 0, displayedStatus, 0, array.size)
        }
        args["displayed_particle_count"]?.let { displayedParticleCount = it.loadedValue!! as Int }
        readPacketArgsSequenced(args)
        args["index_status_change"]?.let {
            val array = it.loadedValue as IntArray

            /** @see particleLinkageDisplayCurrentIndex */
            val index = array[0]
            val status = array[1] == CREATE_PARTICLE
            this.displayedParticleCount = array[2]
            changeSingleStatus(index, status)
        }

        args["indexes_status_change_arg"]?.let {
            val indexes = it.loadedValue as IntArray
            val methodArray = args["indexes_status_change_status"]!!.loadedValue as IntArray
            val status = methodArray[0] == CREATE_PARTICLE
            this.displayedParticleCount = methodArray[1]
            changeParticlesStatus(indexes, status)
        }
    }

    fun displayParticles() {
        val locations = getCurrentFramesSequenced()
        beforeDisplay(locations)
        toggleScale(locations)
        sequencedParticles.addAll(locations.map { it.key to it.value })
        Math3DUtil.rotateAsAxis(locations.values.toList(), axis, rotate)
    }

    override fun rotateParticlesAsAxis(angle: Double) {
        Math3DUtil.rotateAsAxis(
            sequencedParticles.map { it.second }.toList(), axis, angle
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

    override fun rotateToWithAngle(to: RelativeLocation, angle: Double) {
        Math3DUtil.rotateAsAxis(
            sequencedParticles.map { it.second }.toList(), axis, angle
        )
        Math3DUtil.rotatePointsToPoint(
            sequencedParticles.map { it.second }.toList(), to, axis
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

    override fun rotateParticlesToPoint(to: RelativeLocation) {
        Math3DUtil.rotatePointsToPoint(
            sequencedParticles.map { it.second }.toList(), to, axis
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

    final override fun toggleScaleDisplayed() {
        sequencedParticles.forEach {
            val uuid = it.first.uuid
            val len = particleDefaultLength[uuid]!!
            val value = it.second
            if (len in -1e-3..1e-3) return@forEach
            value.multiply(len * scale / value.length())
        }
    }

    private fun createWithIndex(index: Int) {
        if (!client) {
            return
        }

        if (index !in 0..sequencedParticles.size - 1) {
            return
        }

        val (data, rl) = sequencedParticles[index]
        val uuid = data.uuid

        val displayer = data.displayerBuilder(uuid)
        if (displayer is ParticleDisplayer.SingleParticleDisplayer) {
            val controler = ControlParticleManager.createControl(uuid)
            controler.initInvoker = data.particleHandler
        }
        val toPos = Vec3d(pos.x + rl.x, pos.y + rl.y, pos.z + rl.z)
        val controler = displayer.display(toPos, world as ClientWorld) ?: return
        if (controler is ParticleControler) {
            data.particleControlerHandler(controler)
        }
        particles[uuid] = controler
        particleLocations[controler] = rl
    }

    private fun toggleFromStatus(index: Int, status: Boolean) {
        if (index >= sequencedParticles.size && client || index > getParticlesCount()) return
        if (!client) return
        if (status) {
            createWithIndex(index)
        } else {
            val uuid = sequencedParticles[index].first.uuid
            val particle = particles[uuid] ?: return
            particle.remove()
            particles.remove(uuid)
            particleLocations.remove(particle)
        }
        setStatus(index, status)
    }

    private fun setStatus(index: Int, status: Boolean) {
        val page = MathDataUtil.getStoragePageLong(index)
        val container = displayedStatus[page]
        val bit = MathDataUtil.getStorageWithBitLong(index)
        displayedStatus[page] = MathDataUtil.setStatusLong(container, bit, status)
    }

    private fun getStatus(index: Int): Boolean {
        val page = MathDataUtil.getStoragePageLong(index)
        val container = displayedStatus[page]
        val bit = MathDataUtil.getStorageWithBitLong(index)
        return MathDataUtil.getStatusLong(container, bit) == 1
    }

    /**
     * 在同步了data状态后
     * 执行生成已经生成的
     * 客户端执行
     */
    private fun toggleDataStatus() {
        if (!client) {
            return
        }

        if (displayedStatus.isEmpty()) return
        if (!displayed) return
        displayedStatus.forEachIndexed { page, container ->
            for (bit in 1..64) {
                val index = page * 64 + bit - 1
                if (index >= sequencedParticles.size) {
                    break
                }
                val status = MathDataUtil.getStatusLong(container, bit)
                toggleFromStatus(index, status == 1)
            }

        }
    }

    private fun clearStatus() {
        for (i in displayedStatus.indices) {
            displayedStatus[i] = 0L
        }
    }

    final override fun getCurrentFrames(): Map<StyleData, RelativeLocation> {
        return mapOf()
    }


    final override fun beforeDisplay(styles: Map<StyleData, RelativeLocation>) {}
}