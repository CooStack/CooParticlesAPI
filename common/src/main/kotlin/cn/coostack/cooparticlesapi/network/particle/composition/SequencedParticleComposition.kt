package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.helper.SequencedCompositionAnimationHelper
import cn.coostack.cooparticlesapi.utils.storage.Memo
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.SortedMap
import java.util.UUID
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

/**
 * 自动更新(AutoToggle) 生长动画
 *
 * AI神力 根本懒得再写一份Sequenced的版本了
 */
abstract class SequencedParticleComposition(position: Vec3, world: Level? = null) :
    ParticleComposition(position, world) {
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
        if (particles.isNotEmpty()) {
            clear(false)
        }
        displayParticles()
    }

    override fun clear(cancel: Boolean) {
        super.clear(cancel)
        sequencedParticlesData.clear()
        particleRotatedLocations.clear()
    }

    override fun display() {
        if (displayed) {
            return
        }
        displayed = true

        this.client = world!!.isClientSide
        // 在服务器需要用来更新粒子个数 所以需要参与一次计算
        flush()
        if (!client) {
            // 服务器只负责数据同步 不负责粒子生成
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

        // 更新 count 并保证 bitset capacity（保留旧状态）
        ensureIndexCapacity(newCount)

        count = newCount

        // 服务端不做任何粒子生成
        if (!client) {
            return
        }

        beforeDisplaySequenced(locations)
        toggleScale(locations)
        Math3DUtil.rotateAsAxis(locations.values.toList(), axis, roll)

        sequencedParticlesData.clear()
        sequencedParticlesData.addAll(locations.toList())
        this.particleRotatedLocations.addAll(locations.values)
        // client：准备 index->uuid 数组
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

        // 如果 count 变化，保证本地缓存容量；且需要重新 flush 计算 sequencedParticlesData（因为位置可能也变了）
        // 所以当 count 或其他参数改变导致粒子序列改变时，客户端需要重算序列数据
        if (oldCount != this.count || sequencedParticlesData.size != this.count) {
            // 重新计算 sequencedParticlesData（仍然不生成粒子）
            flush()
        }

        applyIndexDiff(oldIndex, this.index.get())
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

            val base = page shl 6 // page*64
            while (diff != 0L) {
                val bit = java.lang.Long.numberOfTrailingZeros(diff) // 0..63
                val i = base + bit
                if (i >= n) break

                val newGen = ((newSafe[page] ushr bit) and 1L) != 0L
                if (newGen) createWithIndex(i) else removeWithIndex(i)

                diff = diff and (diff - 1) // clear lowest set bit
            }
        }
    }


    /**
     * 服务端：生成下一个粒子（只改 bitset 和计数，不生成粒子对象）
     *
     * waring 如果你的style不是client only （也就是要在服务器里面调用生成的，在调用此方法前
     * 一定要在非client作用域执行
     * ```kotlin
     * if (!client){
     *  addSingle()
     * }
     * ```
     * 如果你使用的是单纯的客户端 比如 SequencedParticleShapeComposition 则无需此判断
     */
    fun addSingle() {
        if (count <= 0) return
        if (serverCurrentIndex !in 0 until count) return

        val idx = serverCurrentIndex
        if (!isParticleDisplayed(idx)) {
            setParticleStatus(idx, true)
            displayedParticleCount++

            // client: 立即生成该 index 对应的粒子
            if (client) {
                createWithIndex(idx)
            }
        }

        serverCurrentIndex = min(idx + 1, max(count - 1, 0))
    }

    /**
     * 服务端：生成多个粒子（只改 bitset 和计数，不生成粒子对象）
     *
     * waring 如果你的style不是client only （也就是要在服务器里面调用生成的，在调用此方法前
     * 一定要在非client作用域执行
     * ```kotlin
     * if (!client){
     *  addMultiple(amount)
     * }
     * ```
     * 如果你使用的是单纯的客户端 比如 SequencedParticleShapeComposition 则无需此判断
     *
     * @param amount 生成数量（<=0 会直接 return）
     */
    fun addMultiple(amount: Int) {
        if (amount <= 0) return
        repeat(amount) { addSingle() }
    }

    /**
     * 服务端：移除上一个粒子（只改 bitset 和计数，不删除粒子对象）
     *
     * waring 如果你的style不是client only （也就是要在服务器里面调用生成的，在调用此方法前
     * 一定要在非client作用域执行
     * ```kotlin
     * if (!client){
     *  removeSingle()
     * }
     * ```
     * 如果你使用的是单纯的客户端 比如 SequencedParticleShapeComposition 则无需此判断
     *
     * 说明：
     * - 使用 serverCurrentIndex 作为删除指针
     * - 若对应 index 已显示，则置为 false 并减少 displayedParticleCount
     * - client 场景下会立即 removeWithIndex(idx) 以本地预览/嵌套使用
     */
    fun removeSingle() {
        if (count <= 0) return
        val idx = min(serverCurrentIndex, count - 1)
        if (idx !in 0 until count) return

        if (isParticleDisplayed(idx)) {
            setParticleStatus(idx, false)
            displayedParticleCount--

            // client: 立即删除该 index 对应的粒子
            if (client) {
                removeWithIndex(idx)
            }
        }

        serverCurrentIndex = max(idx - 1, 0)
    }

    /**
     * 服务端：移除多个粒子（只改 bitset 和计数，不删除粒子对象）
     *
     * waring 如果你的style不是client only （也就是要在服务器里面调用生成的，在调用此方法前
     * 一定要在非client作用域执行
     * ```kotlin
     * if (!client){
     *  removeMultiple(amount)
     * }
     * ```
     * 如果你使用的是单纯的客户端 比如 SequencedParticleShapeComposition 则无需此判断
     *
     * @param amount 移除数量（<=0 会直接 return）
     */
    fun removeMultiple(amount: Int) {
        if (amount <= 0) return
        repeat(amount) { removeSingle() }
    }

    /**
     * 重置所有粒子状态（清空 bitset、计数器、指针）
     *
     * waring 如果你的style不是client only （也就是要在服务器里面调用生成的，在调用此方法前
     * 一定要在非client作用域执行
     * ```kotlin
     * if (!client){
     *  resetAll()
     * }
     * ```
     * 如果你使用的是单纯的客户端 比如 SequencedParticleShapeComposition 则无需此判断
     *
     * 说明：
     * - server：只清 bitset/计数/指针，等待同步到客户端由 diff 处理
     * - client：会先遍历 bitset，把已生成的粒子对象全部 removeWithIndex(i)，再清 bitset
     *
     * 注意：
     * - resetAll() 会把 serverCurrentIndex 重置为 0
     * - displayedParticleCount 会重置为 0
     */
    fun resetAll() {
        // client: 先删掉所有已经生成的粒子对象
        if (client && count > 0) {
            val pages = pagesFor(count)
            val bits = index.get()
            for (page in 0 until min(bits.size, pages)) {
                var v = bits[page]
                if (v == 0L) continue
                val base = page shl 6
                while (v != 0L) {
                    val bit = java.lang.Long.numberOfTrailingZeros(v)
                    val i = base + bit
                    if (i >= count) break
                    removeWithIndex(i)
                    v = v and (v - 1)
                }
            }
        }

        // 清 bitset
        val arr = index.get()
        for (i in arr.indices) arr[i] = 0L

        displayedParticleCount = 0
        serverCurrentIndex = 0
    }

    override fun rotateToPoint(to: RelativeLocation) {
        if (!client) {
            axis.apply {
                this.x = to.x
                this.y = to.y
                this.z = to.z
            }
            return
        }
        Math3DUtil.rotatePointsToPoint(
            particleRotatedLocations, to, axis
        )
        axis.apply {
            this.x = to.x
            this.y = to.y
            this.z = to.z
        }
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
            axis.apply {
                this.x = to.x
                this.y = to.y
                this.z = to.z
            }
            return
        }
        Math3DUtil.rotateToWithRoll(
            particleRotatedLocations, axis, to, radian
        )
        axis.apply {
            this.x = to.x
            this.y = to.y
            this.z = to.z
        }
        toggleRelative()
    }

    override fun rotateAsAxis(radian: Double) {
        this.roll += radian
        if (this.roll >= 2 * PI) {
            this.roll -= 2 * PI
        } else if (this.roll <= 2 * PI) {
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

    fun setParticleStatus(index: Int, generated: Boolean) {
        if (index !in 0 until count) return
        setBit(this.index.get(), index, generated)
    }

    fun isParticleDisplayed(index: Int): Boolean {
        if (index !in 0..count) return false
        return getBit(this.index.get(), index)
    }

    /**
     * 客户端映射：index -> uuid（用于按 index 删除）
     * 只在 client 使用；server 不会用到
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
        indexToUuid[i] = data.uuid
    }

    override fun displayEntry(data: CompositionData, pos: RelativeLocation) {
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
        particles[uuid] = controler
        particleLocations[controler] = pos
    }


    private fun removeWithIndex(i: Int) {
        if (!client) return
        if (i !in 0 until count) return
        if (indexToUuid.size != count) return

        val uuid = indexToUuid[i] ?: return
        val obj = particles[uuid] ?: run {
            indexToUuid[i] = null
            return
        }

        obj.remove()
        particles.remove(uuid)
        particleLocations.remove(obj)
        indexToUuid[i] = null
    }


    private fun ensureIndexCapacity(newCount: Int) {
        val pages = pagesFor(newCount)
        val current = index.get()
        if (current.size == pages) return

        val resized = LongArray(pages)
        // copy old pages into new
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
