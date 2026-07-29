package cn.coostack.cooparticlesapi.cparticle.compat

/**
 * 记住一组分段中当前可写的对象，避免每次写入都从首段重新查找。
 *
 * 示例：前 122 段已满时，首次查到第 122 段后，后续写入会直接复用该对象。
 * 禁止让 [getOrCreate] 返回已经释放的对象；释放状态只用于使缓存失效。
 *
 * @param T 被分段管理的对象类型
 * @property isReleased 判断缓存对象是否已经释放
 * @property isFull 判断对象是否没有剩余容量
 * @property getOrCreate 按分段编号查找或创建对象
 */
internal class CParticleEmitterSegmentCursor<T>(
    private val isReleased: (T) -> Boolean,
    private val isFull: (T) -> Boolean,
    private val getOrCreate: (Int) -> T,
) {
    /**
     * 当前查找起点的分段编号。
     *
     * 示例：首次查找从 `0` 开始。
     * 禁止把该值当成总分段数量。
     */
    private var segment = 0

    /**
     * 当前可写对象；对象写满或释放后清空。
     *
     * 示例：连续写入同一未满 system 时保留它的引用。
     * 禁止在对象释放后继续返回旧引用。
     */
    private var current: T? = null

    /**
     * 返回当前可写对象；缓存写满后重新查找旧分段中的空槽。
     *
     * 示例：缓存对象仍有容量时，本方法不会调用 [getOrCreate]。
     * 禁止在多个线程同时调用；游标属于客户端渲染线程。
     *
     * @return 当前可写的分段对象
     */
    fun findAvailable(): T {
        current?.let { cached ->
            if (isReleased(cached)) {
                current = null
            } else if (!isFull(cached)) {
                return cached
            } else {
                current = null
                segment = 0
            }
        }

        while (true) {
            val candidate = getOrCreate(segment)
            if (!isFull(candidate)) {
                current = candidate
                return candidate
            }
            segment++
        }
    }
}
