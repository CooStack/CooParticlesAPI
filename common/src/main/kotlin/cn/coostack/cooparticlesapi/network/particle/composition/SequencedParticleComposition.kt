package cn.coostack.cooparticlesapi.network.particle.composition

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

        beforeDisplay(locations)
        toggleScale(locations)
        Math3DUtil.rotateAsAxis(locations.values.toList(), axis, roll)

        sequencedParticlesData.clear()
        sequencedParticlesData.addAll(locations.toList())

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

        // 替换 index 引用（Memo 的意义）
        this.index.setMemoValue(other.index.get())

        // client 才能应用 diff
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
     * 客户端会在 update(diff) 中真正生成
     */
    fun addSingle() {
        if (client) return
        if (count <= 0) return
        if (serverCurrentIndex !in 0 until count) return

        if (!getGenerated(serverCurrentIndex)) {
            setGenerated(serverCurrentIndex, true)
            displayedParticleCount++
        }
        serverCurrentIndex = min(serverCurrentIndex + 1, max(count - 1, 0))
    }

    fun addMultiple(amount: Int) {
        if (client) return
        if (amount <= 0) return
        repeat(amount) { addSingle() }
    }

    fun removeSingle() {
        if (client) return
        if (count <= 0) return
        val idx = min(serverCurrentIndex, count - 1)
        if (idx !in 0 until count) return

        if (getGenerated(idx)) {
            setGenerated(idx, false)
            displayedParticleCount--
        }
        serverCurrentIndex = max(idx - 1, 0)
    }

    fun removeMultiple(amount: Int) {
        if (client) return
        if (amount <= 0) return
        repeat(amount) { removeSingle() }
    }

    fun resetAllGenerated() {
        val arr = index.get()
        for (i in arr.indices) arr[i] = 0L
        displayedParticleCount = 0
        serverCurrentIndex = 0
    }

    fun setGenerated(index: Int, generated: Boolean) {
        if (index !in 0 until count) return
        setBit(this.index.get(), index, generated)
    }

    fun getGenerated(index: Int): Boolean {
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
        // ParticleComposition 的 displayEntry 会把粒子塞进 particles/particleLocations
        displayEntry(data, rl)
        indexToUuid[i] = data.uuid
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