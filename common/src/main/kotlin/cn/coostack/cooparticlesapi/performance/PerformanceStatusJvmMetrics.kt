package cn.coostack.cooparticlesapi.performance

import java.lang.management.ManagementFactory

/**
 * JVM 垃圾收集器累计指标。
 *
 * @property collectionCount 所有可用垃圾收集器累计完成的收集次数；不可用值按零处理
 * @property collectionTimeMs 所有可用垃圾收集器累计耗时毫秒；不可用值按零处理
 */
data class PerformanceStatusGcTotals(
    val collectionCount: Long,
    val collectionTimeMs: Long,
)

/** 从当前 JVM 的 GarbageCollectorMXBean 汇总堆回收指标。 */
object PerformanceStatusJvmMetrics {
    /** JVM 生命周期内稳定的 GarbageCollectorMXBean 列表，避免每个 Status tick 重复查询。 */
    private val garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans()

    /** 返回采样时所有可用垃圾收集器的累计次数与耗时。 */
    fun snapshot(): PerformanceStatusGcTotals {
        return PerformanceStatusGcTotals(
            collectionCount = garbageCollectors.sumOf { bean ->  bean.collectionCount.coerceAtLeast(0L) },
            collectionTimeMs = garbageCollectors.sumOf { bean -> bean.collectionTime.coerceAtLeast(0L) },
        )
    }
}
