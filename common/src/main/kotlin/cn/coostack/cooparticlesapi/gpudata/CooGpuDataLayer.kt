package cn.coostack.cooparticlesapi.gpudata

import java.util.Arrays

/**
 * # GPU 数据图层 — 逻辑数据资源
 *
 * 一个图层是一段按 [layout] 解释的 float 数据，加上它的有效长度、分配容量、版本和脏区间。
 * 图层是**逻辑**资源，不要求一对一映射到一张 GL 纹理或一个独立 buffer；具体后端由
 * [CooGpuDataGlBuffer] 决定。
 *
 * 图层自己负责：
 * - 有效长度与分配容量分离。写入超过容量才增长，减少元素不会立刻缩容，避免每次改点都重建资源。
 * - 版本与脏区间。每次 [markDirty] 递增 [version] 并合并脏区间，使用端据此只上传变化段。
 * - 上传判定。静态数据在首次上传后 [needsUpload] 返回 `false`，不重复上传。
 *
 * 图层**不负责**引用计数、资源 ID 和世代检查，这些属于 [CooGpuDataRegistry]；
 * 也不持有任何 GL 句柄，GL 资源只在客户端渲染线程由 [CooGpuDataGlBuffer] 创建。
 *
 * @param layout 数据的成分布局
 * @param initialElements 初始分配的元素容量；实际数组会按 [allocationGrowth] 只增不减
 */
class CooGpuDataLayer(
    val layout: CooGpuDataLayout,
    initialElements: Int,
) {
    init {
        require(initialElements >= 0) { "Gpu data layer initial element count must be non-negative: $initialElements" }
    }

    /**
     * 当前分配容量能够容纳的元素个数。
     *
     * 示例：路径从 8 个点减到 4 个点时 [elementCount] 变小，但 [capacity] 不变。
     * 禁止：不要把该值当成有效数据长度，尾部可能仍是上一版数据。
     */
    var capacity: Int = initialElements
        private set

    /** 交错数据缓冲；长度始终等于 `capacity * layout.componentCount`。 */
    var data = FloatArray(layout.floatsFor(initialElements))
        private set

    /**
     * 当前有效元素个数。
     *
     * 示例：路径控制点数写入后该值等于控制点数。
     * 禁止：使用端读取时必须按本值截断，不能读取到旧容量尾部。
     */
    var elementCount: Int = 0
        private set

    /**
     * 内容版本号。
     *
     * 示例：修改任一元素后版本递增，使用端比较版本即可跳过静态资源的重复上传。
     * 禁止：不要用它表示 GL 资源是否已经创建，那属于 [CooGpuDataGlBuffer.initialized]。
     */
    var version: Int = 0
        private set

    /** 脏区间起点（元素下标）；无脏数据时为 `-1`。 */
    var dirtyFrom: Int = -1
        private set

    /** 脏区间终点（元素下标，含）；无脏数据时为 `-1`。 */
    var dirtyTo: Int = -1
        private set

    /** 已经上传到 GPU 的内容版本；尚未上传时为 `-1`。 */
    var uploadedVersion: Int = -1
        private set

    /** 是否需要重新上传。静态图层在首次上传后返回 `false`。 */
    val needsUpload: Boolean
        get() = uploadedVersion != version

    /** 当前有效数据占用的 float 数量。 */
    val floatCount: Int
        get() = layout.floatsFor(elementCount)

    /** 当前分配容量对应的 float 数量。 */
    val allocatedFloatCount: Int
        get() = layout.floatsFor(capacity)

    /**
     * 把有效元素个数设为 [count]，必要时扩展分配容量。
     *
     * 容量不足时按倍增策略扩展：`max(需要量, 当前容量 * 2)`。这样路径增点时不会每加一个点
     * 就重建一次 GL 资源；减点时也不缩容，避免来回抖动。
     *
     * @param count 新的有效元素个数，必须非负
     * @return 本次是否改变了分配容量；调用方据此决定是否需要重建 GPU 资源
     */
    fun resize(count: Int): Boolean {
        require(count >= 0) { "Gpu data layer element count must be non-negative: $count" }
        val grew = ensureCapacity(count)
        if (count != elementCount) {
            elementCount = count
            markDirty(0, count - 1)
        }
        return grew
    }

    /**
     * 保证分配容量至少能容纳 [elements] 个元素。
     *
     * @param elements 需要的最小元素容量，必须非负
     * @return 本次是否发生了扩容
     */
    fun ensureCapacity(elements: Int): Boolean {
        require(elements >= 0) { "Gpu data layer element capacity must be non-negative: $elements" }
        if (elements <= capacity) return false
        val grown = maxOf(elements.toLong(), capacity.toLong() * 2L, 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        data = data.copyOf(layout.floatsFor(grown))
        capacity = grown
        return true
    }

    /**
     * 读取一个 float 成分。
     *
     * @param element 元素下标
     * @param component 成分下标，取值范围由 [CooGpuDataLayout.componentCount] 决定
     * @return 该成分当前值
     */
    fun getFloat(element: Int, component: Int): Float = data[offset(element, component)]

    /** 写入一个 float 成分；越界写入会被拒绝，避免静默破坏相邻元素。 */
    fun setFloat(element: Int, component: Int, value: Float) {
        data[offset(element, component)] = value
    }

    /**
     * 把一个元素的全部成分写入图层。
     *
     * @param element 元素下标
     * @param values 成分值，长度必须不小于 [CooGpuDataLayout.componentCount]
     * @param sourceOffset [values] 中的起始下标
     */
    fun setElement(element: Int, values: FloatArray, sourceOffset: Int = 0) {
        require(values.size - sourceOffset >= layout.componentCount) {
            "Gpu data layer ${layout.id} element write needs ${layout.componentCount} floats " +
                "but only ${values.size - sourceOffset} available"
        }
        val base = offset(element, 0)
        values.copyInto(data, base, sourceOffset, sourceOffset + layout.componentCount)
    }

    /**
     * 标记整个有效范围已变化。
     *
     * 示例：路径整体重建后调用一次即可。
     * 禁止：逐点写入时不要每个点都调用，使用 [markDirty] 指定区间，让脏区间自动合并。
     */
    fun markAllDirty() = markDirty(0, elementCount - 1)

    /**
     * 标记一个元素区间已变化，并递增内容版本。
     *
     * 脏区间按并集合并：多次相邻或重叠的标记只会产出一个待上传范围。
     *
     * @param from 起始元素下标（含）
     * @param to 终止元素下标（含）
     */
    fun markDirty(from: Int, to: Int) {
        version++
        if (to < from || to < 0 || from >= elementCount) return
        val clampedFrom = from.coerceAtLeast(0)
        val clampedTo = to.coerceAtMost(elementCount - 1)
        if (dirtyFrom < 0) {
            dirtyFrom = clampedFrom
            dirtyTo = clampedTo
            return
        }
        if (clampedFrom < dirtyFrom) dirtyFrom = clampedFrom
        if (clampedTo > dirtyTo) dirtyTo = clampedTo
    }

    /** 把当前数据视为已经同步到 GPU，并清除脏区间。 */
    fun markUploaded() {
        uploadedVersion = version
        dirtyFrom = -1
        dirtyTo = -1
    }

    /**
     * 释放 CPU 侧数据。
     *
     * 示例：资源注册表在最后一个使用者释放路径后调用。
     * 禁止：释放后继续调用读写入口，所有入口都会因为容量为零而拒绝写入。
     */
    fun release() {
        Arrays.fill(data, 0F)
        data = FloatArray(0)
        capacity = 0
        elementCount = 0
        dirtyFrom = -1
        dirtyTo = -1
        uploadedVersion = -1
    }

    private fun offset(element: Int, component: Int): Int {
        require(element in 0 until capacity) {
            "Gpu data layer ${layout.id} element index out of range: $element (capacity $capacity)"
        }
        require(component in 0 until layout.componentCount) {
            "Gpu data layer ${layout.id} component index out of range: $component"
        }
        return element * layout.componentCount + component
    }
}
