package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.helper.SequencedCompositionAnimationHelper
import cn.coostack.cooparticlesapi.utils.storage.Memo
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.SortedMap
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * 自动更新(AutoToggle) 生长动画
 *
 * AI神力 根本懒得再写一份Sequenced的版本了
 */
abstract class SequencedParticleComposition(position: Vec3, world: Level? = null) :
    ParticleComposition(position, world) {

    constructor(world: Level) : this(Vec3.ZERO, world)
    constructor(world: Level, pos: Vec3) : this(pos, world)

    companion object {
        @JvmStatic
        fun encodeBase(data: SequencedParticleComposition, buf: FriendlyByteBuf) {
            ParticleComposition.encodeBase(data, buf)
            buf.writeInt(data.count)
            buf.writeInt(data.displayedParticleCount)
            buf.writeInt(data.serverCurrentIndex)
            buf.writeLongArray(data.index.get())
        }

        @JvmStatic
        fun decodeBase(instance: SequencedParticleComposition, buf: FriendlyByteBuf) {
            ParticleComposition.decodeBase(instance, buf)
            instance.count = buf.readInt()
            instance.displayedParticleCount = buf.readInt()
            instance.serverCurrentIndex = buf.readInt()
            instance.index.setMemoValue(buf.readLongArray())
        }
    }

    /**
     * 这里采用 animate框架 写多个条件用来方便生长动画的条件变化
     * 在init 或者 onDisplay执行
     */
    val animate =
        SequencedCompositionAnimationHelper<SequencedParticleComposition>()
            .loadComposition(this)


    var count = 0
    var index = Memo {
        var page = count / 64
        if (count % 64 > 0) {
            page++
        }
        LongArray(page)
    }

    /** 服务端：已经“显示”的粒子数量（仅逻辑字段，可用于业务） */
    var displayedParticleCount: Int = 0
        protected set

    /** 服务端：下一个将要生成的 index 指针（用于 addSingle/addMultiple 生长动画） */
    var serverCurrentIndex: Int = 0
        protected set
    protected val sequencedParticlesData = ArrayList<Pair<CompositionData, RelativeLocation>>()
    override fun getParticles(): SortedMap<CompositionData, RelativeLocation> {
        return getParticleSequenced()
    }

    /**
     * 不要调用只有客户端才能调用的方法 因为服务器也会调用一次这个方法用来获取实际的粒子个数
     *
     * @return 粒子相对样式
     */
    abstract fun getParticleSequenced(): SortedMap<CompositionData, RelativeLocation>
    final override fun tick() {
        super.tick()
    }

    override fun flush() {
        if (particles.isNotEmpty() || indexToUuid.any { it != null }) {
            clear(false)
        }
        displayParticles()
        restoreDisplayedParticles()
    }

    override fun clear(cancel: Boolean) {
        super.clear(cancel)
        sequencedParticlesData.clear()
        particleRotatedLocations.clear()
        indexToUuid.fill(null)
    }

    /**
     * 进入序列 Composition 的显示生命周期并初始化动画状态。
     *
     * 示例：客户端直接显示时会登记为一个活动 Composition 实例。
     * 禁止在 [world] 尚未设置时调用。
     */
    override fun display() {
        if (displayed) {
            return
        }
        displayed = true
        // 修复禁用后不自动移除的问题
        status.loadControler(this)
        status.initHelper()
        this.client = world!!.isClientSide
        if (client) {
            ParticleCompositionManager.setClientLoaded(this, true)
        }
        // 在服务器需要用来更新粒子个数 所以需要参与一次计算
        flush()
        if (!client) {
            // 服务器只负责数据同步，不负责粒子生成。
            onDisplay()
            return
        }
        onDisplay()
    }

    open fun beforeDisplaySequenced(map: SortedMap<CompositionData, RelativeLocation>) {

    }

    /**
     * 这里非常关键：
     * - 只计算序列数据、count、缩放/旋转后的相对坐标
     * - 不创建粒子对象（不调用 displayEntry）
     */
    override fun displayParticles() {
        val locations = getParticles()
        val newCount = locations.size
        prepareGpuComposition(newCount)

        // 更新粒子总数并扩容位集，同时保留已有状态。
        ensureIndexCapacity(newCount)

        count = newCount

        // 服务端不做任何粒子生成
        if (!client) {
            return
        }

        beforeDisplaySequenced(locations)
        Math3DUtil.rotatePointsToPoint(locations.values.toList(), axis, RelativeLocation.yAxis())
        Math3DUtil.rotateAsAxis(locations.values.toList(), axis, roll)
        toggleScale(locations)

        sequencedParticlesData.clear()
        sequencedParticlesData.addAll(locations.toList())
        particleRotatedLocations.clear()
        particleRotatedLocations.addAll(locations.values)
        // 客户端：准备索引到 UUID 的映射数组。
        if (indexToUuid.size != count) {
            indexToUuid = arrayOfNulls(count)
        }
    }

    override fun update(other: ParticleComposition) {
        super.update(other)
        other as SequencedParticleComposition
        val oldIndex = this.index.get().copyOf()
        val oldCount = this.count

        this.count = other.count
        this.displayedParticleCount = other.displayedParticleCount
        this.serverCurrentIndex = other.serverCurrentIndex

        this.index.setMemoValue(other.index.get())

        if (!client) return

        // 粒子总数或缓存结构变化时，客户端需要重新计算序列数据。
        if (oldCount != this.count || sequencedParticlesData.size != this.count) {
            // 缓存重建会移除旧节点，flush 会按新的完整位集恢复已显示状态。
            flush()
            return
        }

        applyIndexDiff(oldIndex, this.index.get())
        restoreDisplayedParticles()
    }

    private fun restoreDisplayedParticles() {
        if (!client || sequencedParticlesData.size != count) return
        for (i in 0 until count) {
            if (isParticleDisplayed(i)) createWithIndex(i)
        }
    }

    private fun applyIndexDiff(oldBits: LongArray, newBits: LongArray) {

        if (!client) return
        val n = count
        if (n <= 0) return
        if (sequencedParticlesData.size != n) return

        val pages = pagesFor(n)
        // 复制到同长度，避免越界判断
        val oldSafe = if (oldBits.size == pages) oldBits else oldBits.copyOf(pages)
        val newSafe = if (newBits.size == pages) newBits else newBits.copyOf(pages)

        for (page in 0 until pages) {
            var diff = oldSafe[page] xor newSafe[page]
            if (diff == 0L) continue

            val base = page shl 6 // 当前页的首个槽位
            while (diff != 0L) {
                val bit = diff.countTrailingZeroBits() // 0..63
                val i = base + bit
                if (i >= n) break

                val newGen = ((newSafe[page] ushr bit) and 1L) != 0L
                if (newGen) createWithIndex(i) else removeWithIndex(i)

                diff = diff and (diff - 1) // 清除最低位的 1
            }
        }
    }


    /**
     * 服务端：生成下一个粒子（只改 位集 和计数，不生成粒子对象）
     *
     * 警告：如果你的样式不是仅客户端（也就是要在服务器里调用生成），在调用此方法前
     * 一定要在非客户端作用域执行
     * ```kotlin
     * if (!client){
     *  addSingle()
     * }
     * ```
     * 如果你使用的是单纯的客户端 比如 SequencedParticleShapeComposition 则无需此判断。
     * 方法从当前指针向后寻找第一个未显示槽位，必要时回绕；成功后会标记完整网络状态。
     * 服务端调用必须位于 Composition 生命周期更新所在的逻辑线程。
     */
    fun addSingle() {
        if (count <= 0) return
        val start = serverCurrentIndex.coerceIn(0, count - 1)
        val idx = (start until count).firstOrNull { !isParticleDisplayed(it) }
            ?: (0 until start).firstOrNull { !isParticleDisplayed(it) }
            ?: return

        setBit(index.get(), idx, true)
        displayedParticleCount++

        // 客户端：立即生成该索引对应的粒子。
        if (client) {
            createWithIndex(idx)
        }
        serverCurrentIndex = min(idx + 1, max(count - 1, 0))
        markDirty()
    }

    /**
     * 服务端：生成多个粒子（只改 位集 和计数，不生成粒子对象）
     *
     * 警告：如果你的样式不是仅客户端（也就是要在服务器里调用生成），在调用此方法前
     * 一定要在非客户端作用域执行
     * ```kotlin
     * if (!client){
     *  addMultiple(amount)
     * }
     * ```
     * 如果你使用的是单纯的客户端 比如 SequencedParticleShapeComposition 则无需此判断
     *
     * @param amount 生成数量（小于等于 0 时直接返回）
     */
    fun addMultiple(amount: Int) {
        if (amount <= 0) return
        repeat(amount) { addSingle() }
    }

    /**
     * 服务端：移除上一个粒子（只改 位集 和计数，不删除粒子对象）
     *
     * 警告：如果你的样式不是仅客户端（也就是要在服务器里调用生成），在调用此方法前
     * 一定要在非客户端作用域执行
     * ```kotlin
     * if (!client){
     *  removeSingle()
     * }
     * ```
     * 如果你使用的是单纯的客户端 比如 SequencedParticleShapeComposition 则无需此判断
     *
     * 说明：
     * - 从 `serverCurrentIndex` 向前寻找最近的已显示槽位，必要时回绕
     * - 成功后清除位、更新计数和指针，并标记完整网络状态
     * - 客户端场景会立即调用 `removeWithIndex`，服务端调用必须位于 Composition 生命周期更新线程
     */
    fun removeSingle() {
        if (count <= 0) return
        val start = serverCurrentIndex.coerceIn(0, count - 1)
        val idx = (start downTo 0).firstOrNull(::isParticleDisplayed)
            ?: (count - 1 downTo start + 1).firstOrNull(::isParticleDisplayed)
            ?: return

        setBit(index.get(), idx, false)
        displayedParticleCount--

        // 客户端：立即删除该索引对应的粒子。
        if (client) {
            removeWithIndex(idx)
        }
        serverCurrentIndex = max(idx - 1, 0)
        markDirty()
    }

    /**
     * 服务端：移除多个粒子（只改 位集 和计数，不删除粒子对象）
     *
     * 警告：如果你的样式不是仅客户端（也就是要在服务器里调用生成），在调用此方法前
     * 一定要在非客户端作用域执行
     * ```kotlin
     * if (!client){
     *  removeMultiple(amount)
     * }
     * ```
     * 如果你使用的是单纯的客户端 比如 SequencedParticleShapeComposition 则无需此判断
     *
     * @param amount 移除数量（小于等于 0 时直接返回）
     */
    fun removeMultiple(amount: Int) {
        if (amount <= 0) return
        repeat(amount) { removeSingle() }
    }

    /**
     * 重置所有粒子状态（清空 位集、计数器、指针）
     *
     * 警告：如果你的样式不是仅客户端（也就是要在服务器里调用生成），在调用此方法前
     * 一定要在非客户端作用域执行
     * ```kotlin
     * if (!client){
     *  resetAll()
     * }
     * ```
     * 如果你使用的是单纯的客户端 比如 SequencedParticleShapeComposition 则无需此判断
     *
     * 说明：
     * - 服务端：只清位集、计数和指针，等待同步到客户端后按差异处理
     * - 客户端：先遍历位集，把已生成的粒子对象全部通过 `removeWithIndex(i)` 移除，再清空位集
     *
     * 注意：
     * - resetAll() 会把 serverCurrentIndex 重置为 0
     * - displayedParticleCount 会重置为 0
     */
    fun resetAll() {
        // 客户端：先删除所有已经生成的粒子对象。
        if (client && count > 0) {
            val pages = pagesFor(count)
            val bits = index.get()
            for (page in 0 until min(bits.size, pages)) {
                var v = bits[page]
                if (v == 0L) continue
                val base = page shl 6
                while (v != 0L) {
                    val bit = v.countTrailingZeroBits()
                    val i = base + bit
                    if (i >= count) break
                    removeWithIndex(i)
                    v = v and (v - 1)
                }
            }
        }

        // 清空位集。
        val arr = index.get()
        val changed = displayedParticleCount != 0 || serverCurrentIndex != 0 || arr.any { it != 0L }
        for (i in arr.indices) arr[i] = 0L

        displayedParticleCount = 0
        serverCurrentIndex = 0
        if (changed) markDirty()
    }

    override fun rotateToPoint(to: RelativeLocation) {
        super.rotateToPoint(to)
    }

    override fun rotateToWithAngle(to: RelativeLocation, radian: Double) {
        super.rotateToWithAngle(to, radian)
    }

    override fun rotateAsAxis(radian: Double) {
        super.rotateAsAxis(radian)
    }

    /**
     * 直接设置一个序列槽位的显示状态。
     *
     * 服务端真实变更后会标记完整网络状态，客户端真实变更后会立即创建或移除对应节点。
     * 越界或状态未变化时不会修改计数、节点和网络脏状态。
     *
     * @param index 目标槽位索引，有效范围为 `0 until count`
     * @param generated `true` 表示显示，`false` 表示移除
     */
    fun setParticleStatus(index: Int, generated: Boolean) {
        if (index !in 0 until count || isParticleDisplayed(index) == generated) return
        setBit(this.index.get(), index, generated)
        displayedParticleCount += if (generated) 1 else -1
        if (client) {
            if (generated) createWithIndex(index) else removeWithIndex(index)
        }
        markDirty()
    }

    fun isParticleDisplayed(index: Int): Boolean {
        if (index !in 0 until count) return false
        return getBit(this.index.get(), index)
    }

    final override fun toggleScaleDisplayed() {
        if (!displayed) {
            return
        }
        sequencedParticlesData.forEach { (data, location) ->
            applyScale(data.uuid, location)
        }
        toggleRelative()
    }

    /**
     * 客户端使用的索引到 UUID 映射，用于按索引删除。
     * 服务器不会读取该映射。
     */
    private var indexToUuid: Array<UUID?> = emptyArray()
    private fun createWithIndex(i: Int) {
        if (!client) return
        if (i !in 0 until sequencedParticlesData.size) return
        if (indexToUuid.size != count) indexToUuid = arrayOfNulls(count)

        // 如果已经创建过就跳过（避免重复）
        if (indexToUuid[i] != null) return

        val (data, rl) = sequencedParticlesData[i]
        displayEntry(data, rl)
        if (particles.containsKey(data.uuid)) {
            indexToUuid[i] = data.uuid
        }
    }

    override fun displayEntry(data: CompositionData, pos: RelativeLocation) {
        super.displayEntry(data, pos)
        refreshGpuTransformMode()
    }

    override fun trackDisplayedParticleLocation(pos: RelativeLocation) = Unit


    private fun removeWithIndex(i: Int) {
        if (!client) return
        if (i !in 0 until count) return
        if (indexToUuid.size != count) return

        val uuid = indexToUuid[i] ?: return
        val obj = particles[uuid] ?: run {
            indexToUuid[i] = null
            return
        }

        unregisterCParticleNode(obj)
        obj.remove()
        if (obj is Tickable<*>) {
            controlerTicks.remove(obj)
        }
        particles.remove(uuid)
        particleLocations.remove(obj)
        indexToUuid[i] = null
        refreshGpuTransformMode()
    }


    private fun ensureIndexCapacity(newCount: Int) {
        val pages = pagesFor(newCount)
        val current = index.get()
        if (current.size == pages) return

        val resized = LongArray(pages)
        // 把旧页复制到新数组
        val len = min(current.size, resized.size)
        for (i in 0 until len) resized[i] = current[i]
        index.setMemoValue(resized)
    }

    private fun pagesFor(cnt: Int): Int {
        if (cnt <= 0) return 0
        return (cnt + 63) / 64
    }

    private fun getBit(arr: LongArray, index: Int): Boolean {
        if (arr.isEmpty()) return false
        val page = index ushr 6 // /64
        if (page !in arr.indices) return false
        val bit = index and 63
        return ((arr[page] ushr bit) and 1L) == 1L
    }

    private fun setBit(arr: LongArray, index: Int, value: Boolean) {
        if (arr.isEmpty()) return
        val page = index ushr 6
        if (page !in arr.indices) return
        val bit = index and 63
        val mask = 1L shl bit
        arr[page] = if (value) (arr[page] or mask) else (arr[page] and mask.inv())
    }

}
