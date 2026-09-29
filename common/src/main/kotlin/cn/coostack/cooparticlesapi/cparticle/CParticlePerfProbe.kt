package cn.coostack.cooparticlesapi.cparticle

import java.util.concurrent.atomic.AtomicLong

/**
 * # CParticle 创建与扩容阶段计时
 *
 * 用户观察到百万 GPU 粒子约 200 FPS 的同时，创建大容量 System 或大量粒子仍有短暂 CPU 峰值。
 * 定位这类峰值不能靠猜，需要把相关阶段分开计时。本对象提供这些分段计时入口，
 * 用于在真实客户端上区分到底是数组分配、批量生成打包、首次元数据上传、GPU 分配还是扩容复制。
 *
 * ## 成本
 * 每个阶段只做两次 `System.nanoTime()` 与一次 `AtomicLong` 累加。默认开启，因为计时成本
 * 相对这些阶段的真实工作量可以忽略；需要极限压测时可以关闭 [enabled]。
 *
 * ## 用法
 * ```kotlin
 * CParticlePerfProbe.measure(CParticlePerfProbe.Stage.SYSTEM_ALLOCATE) {
 *     CParticleSystem(name, capacity, layer, mode)
 * }
 * ```
 * 结果通过 [snapshot] / [reset] 读取，供性能面板或测试断言使用。
 */
object CParticlePerfProbe {
    /**
     * 需要分开观测的阶段。
     *
     * 每个成员对应一个可以独立优化的成本来源；合并观测会让“到底哪一段慢”无法回答。
     * [wireName] 用于稳定的日志与报表输出，不能随意改名。
     */
    enum class Stage(val wireName: String) {
        /** 创建 System 的 CPU 侧对象与 SoA 数组。 */
        SYSTEM_ALLOCATE("system_allocate"),

        /** System 扩容：CPU 侧复制全部数组并重建空闲栈。 */
        SYSTEM_GROW_CPU("system_grow_cpu"),

        /** System 扩容：GPU 侧重新分配实例缓冲并复制已有模拟结果。 */
        SYSTEM_GROW_GPU("system_grow_gpu"),

        /** 批量粒子生成：写入 SoA 数据、申请槽位与全局额度。 */
        PARTICLE_SPAWN_BATCH("particle_spawn_batch"),

        /** 批量粒子生成：纹理与外观描述符解析。 */
        PARTICLE_RESOLVE_TEXTURES("particle_resolve_textures"),

        /** 首次元数据上传：把 metadata 整段传到 GPU。 */
        METADATA_FIRST_UPLOAD("metadata_first_upload"),

        /** GPU 分配：为新 System 首次创建 GL 缓冲。 */
        GL_ALLOCATE("gl_allocate"),

        /** 命令打包：每 tick 把 Force / Command 写成 SSBO 载荷。 */
        COMMAND_PACK("command_pack"),

        /** 路径几何同步与上传。 */
        PATH_SYNC("path_sync"),
    }

    /** 是否收集计时；关闭后 [measure] 只执行动作，不产生任何计时开销。 */
    @Volatile
    var enabled: Boolean = true

    private val nanos = LongArray(Stage.entries.size)
    private val counts = LongArray(Stage.entries.size)
    private val maxNanos = LongArray(Stage.entries.size)
    private val wallStart = AtomicLong(System.nanoTime())

    /**
     * 执行 [action] 并把它记入 [stage]。
     *
     * @param stage 要观测的阶段
     * @param action 实际工作；异常会正常抛出，耗时仍会被记录
     * @return [action] 的返回值
     */
    inline fun <T> measure(stage: Stage, action: () -> T): T {
        if (!enabled) return action()
        val start = System.nanoTime()
        try {
            return action()
        } finally {
            record(stage, System.nanoTime() - start)
        }
    }

    /** 记录一次已经测好的耗时。 */
    @JvmStatic
    fun record(stage: Stage, elapsedNanos: Long) {
        if (!enabled) return
        val index = stage.ordinal
        nanos[index] += elapsedNanos
        counts[index]++
        if (elapsedNanos > maxNanos[index]) maxNanos[index] = elapsedNanos
    }

    /**
     * 取当前累计结果。
     *
     * @return 每个阶段的总耗时（毫秒）、调用次数与单次峰值（毫秒）
     */
    @JvmStatic
    fun snapshot(): Map<Stage, StageSample> {
        val result = LinkedHashMap<Stage, StageSample>()
        for (stage in Stage.entries) {
            val index = stage.ordinal
            result[stage] = StageSample(
                totalMillis = nanos[index] / NANOS_PER_MILLI,
                calls = counts[index],
                maxMillis = maxNanos[index] / NANOS_PER_MILLI,
            )
        }
        return result
    }

    /** 复位全部累计值，供对比基线前后使用。 */
    @JvmStatic
    fun reset() {
        synchronized(this) {
            nanos.fill(0L)
            counts.fill(0L)
            maxNanos.fill(0L)
            wallStart.set(System.nanoTime())
        }
    }

    /** 自上次 [reset] 以来的墙钟时间（毫秒）；用于把阶段耗时换算成占比。 */
    @JvmStatic
    fun elapsedSinceResetMillis(): Long = (System.nanoTime() - wallStart.get()) / NANOS_PER_MILLI

    /** 单阶段累计结果。 */
    data class StageSample(
        /** 累计耗时（毫秒）。 */
        val totalMillis: Long,
        /** 调用次数。 */
        val calls: Long,
        /** 单次峰值耗时（毫秒）。 */
        val maxMillis: Long,
    ) {
        /** 平均单次耗时（毫秒）；没有调用时返回 0。 */
        val averageMillis: Double
            get() = if (calls <= 0L) 0.0 else totalMillis.toDouble() / calls.toDouble()
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
