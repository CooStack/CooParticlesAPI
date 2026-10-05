package cn.coostack.cooparticlesapi.performance.client

/** 每个完整索引块的样本数；写入聚合与窗口查询必须使用同一边界。 */
private const val CHART_INDEX_BLOCK_SIZE = 32

/** 曲线的原始采样位置，时间单位为会话毫秒。 */
internal data class PerformanceStatusChartPoint(
    /** 会话内的原始样本序号，用于相同时间戳下的稳定排序。 */
    val sampleIndex: Long,
    /** 会话开始后的毫秒数。 */
    val elapsedMillis: Long,
    /** 未经过归一化的真实指标值。 */
    val value: Double,
)

/** 可合并的窗口统计；只包含有效值，不把缺失快照或零分母替换成零。 */
internal data class PerformanceStatusChartSummary(
    /** 第一个有效样本。 */
    val first: PerformanceStatusChartPoint,
    /** 最后一个有效样本。 */
    val last: PerformanceStatusChartPoint,
    /** 数值最低的原始样本。 */
    val minimum: PerformanceStatusChartPoint,
    /** 数值最高的原始样本。 */
    val maximum: PerformanceStatusChartPoint,
    /** 普通曲线的数值总和，或比值曲线的有效影响项总和。 */
    val numeratorTotal: Double,
    /** 普通曲线的有效样本数，或比值曲线的有效性能项总和。 */
    val denominatorTotal: Double,
) {
    /** 按时间顺序合并相邻区间，保持原始峰谷和比值统计口径。 */
    fun followedBy(next: PerformanceStatusChartSummary?): PerformanceStatusChartSummary {
        if (next == null) return this
        return PerformanceStatusChartSummary(
            first, next.last,
            if (minimum.value <= next.minimum.value) minimum else next.minimum,
            if (maximum.value >= next.maximum.value) maximum else next.maximum,
            numeratorTotal + next.numeratorTotal, denominatorTotal + next.denominatorTotal,
        )
    }
}

/** 某一曲线在当前时间窗口的保峰绘图点和完整统计，不持有可变历史视图。 */
internal data class PerformanceStatusChartSeries(
    /** 原始指标或负载比值定义。 */
    val selection: PerformanceStatusChartSelection,
    /** 每个时间桶按原始顺序保留首、谷、峰、末，重复点已去除。 */
    val points: List<PerformanceStatusChartPoint>,
    /** 整个可视窗口的统计，而非抽样点的统计；全无有效值时为 null。 */
    val summary: PerformanceStatusChartSummary?,
)

/** 单条曲线的增量多级索引；完整块只聚合一次，查询只扫描两端不足一块的样本。 */
internal class PerformanceStatusChartIndex(
    /** 此索引唯一负责的指标定义，不跨曲线复用统计。 */
    private val selection: PerformanceStatusChartSelection,
) {
    /** 每层保存连续完整块；第 n 层覆盖 32 * 2^n 个原始样本。 */
    private val levels = mutableListOf<Level>()

    /** 上次已读取的样本身份，同时用于识别会话重置。 */
    private var latest: PerformanceStatusSample? = null

    /** 尚未填满的末尾块统计。 */
    private var pending: PerformanceStatusChartSummary? = null

    /** 末尾块是否从对齐的第一个样本开始采集。 */
    private var pendingAligned = false

    /** 吸收新增样本并释放过期索引。首次选择曲线时才遍历已有历史。 */
    fun sync(history: List<PerformanceStatusSample>) {
        if (history.isEmpty()) {
            levels.clear()
            latest = null
            pending = null
            pendingAligned = false
            return
        }
        val firstIndex = history.first().sampleIndex
        val previous = latest
        val previousOffset = previous?.let { it.sampleIndex - firstIndex } ?: -1L
        if (previous != null && (previousOffset !in 0L until history.size.toLong() ||
                history[previousOffset.toInt()] !== previous)) {
            levels.clear()
            latest = null
            pending = null
            pendingAligned = false
        }
        levels.forEachIndexed { index, level ->
            val firstBlock = firstIndex / (CHART_INDEX_BLOCK_SIZE.toLong() shl index)
            while (level.values.isNotEmpty() && level.start < firstBlock) {
                level.values.removeFirst()
                level.start++
            }
        }
        val start = latest?.let { (it.sampleIndex - firstIndex + 1L).toInt() } ?: 0
        for (index in start until history.size) {
            val sample = history[index]
            if (sample.sampleIndex % CHART_INDEX_BLOCK_SIZE == 0L) {
                pending = null
                pendingAligned = true
            }
            if (pendingAligned) {
                val value = summaryOf(sample)
                pending = pending?.followedBy(value) ?: value
                if ((sample.sampleIndex + 1L) % CHART_INDEX_BLOCK_SIZE == 0L) {
                    appendBlock(0, sample.sampleIndex / CHART_INDEX_BLOCK_SIZE, pending)
                    pending = null
                    pendingAligned = false
                }
            }
        }
        latest = history.last()
    }

    /** 查询原始历史中的半开区间；完整块走索引，边缘和未完成块读取原样本。 */
    fun query(history: List<PerformanceStatusSample>, start: Int, end: Int): PerformanceStatusChartSummary? {
        require(start in 0..end && end <= history.size)
        if (start == end) return null
        val firstIndex = history.first().sampleIndex
        var cursor = firstIndex + start
        val endIndex = firstIndex + end
        var result: PerformanceStatusChartSummary? = null
        while (cursor < endIndex) {
            var blockUsed = false
            if (cursor % CHART_INDEX_BLOCK_SIZE == 0L) {
                // 贪心覆盖最大的对齐完整块；过期边缘绝不使用包含窗口外样本的统计。
                for (levelIndex in levels.lastIndex downTo 0) {
                    val span = CHART_INDEX_BLOCK_SIZE.toLong() shl levelIndex
                    if (cursor % span != 0L || span > endIndex - cursor) continue
                    val level = levels[levelIndex]
                    val offset = cursor / span - level.start
                    if (offset !in 0L until level.values.size.toLong()) continue
                    val value = level.values[offset.toInt()]
                    result = result?.followedBy(value) ?: value
                    cursor += span
                    blockUsed = true
                    break
                }
            }
            if (!blockUsed) {
                val nextBoundary = minOf(endIndex, cursor + CHART_INDEX_BLOCK_SIZE - cursor % CHART_INDEX_BLOCK_SIZE)
                val value = summarizeEdge(history, (cursor - firstIndex).toInt(), (nextBoundary - firstIndex).toInt())
                result = result?.followedBy(value) ?: value
                cursor = nextBoundary
            }
        }
        return result
    }

    /** 合并不足一块的原样本；只为最终首尾和峰谷分配对象，避免逐样本创建临时统计。 */
    private fun summarizeEdge(history: List<PerformanceStatusSample>, start: Int, end: Int): PerformanceStatusChartSummary? {
        var first: PerformanceStatusSample? = null
        var last: PerformanceStatusSample? = null
        var minimum: PerformanceStatusSample? = null
        var maximum: PerformanceStatusSample? = null
        var firstValue = 0.0
        var lastValue = 0.0
        var minimumValue = Double.POSITIVE_INFINITY
        var maximumValue = Double.NEGATIVE_INFINITY
        var numeratorTotal = 0.0
        var denominatorTotal = 0.0
        for (offset in start until end) {
            val sample = history[offset]
            val numerator: Double
            val denominator: Double
            when (selection) {
                is PerformanceStatusChartSelection.Metric -> {
                    numerator = selection.extract(sample) ?: continue
                    denominator = 1.0
                }
                is PerformanceStatusChartSelection.Ratio -> {
                    numerator = selection.impact.extract(sample) ?: continue
                    denominator = selection.performance.extract(sample) ?: continue
                }
            }
            if (!numerator.isFinite() || numerator < 0.0 || !denominator.isFinite() || denominator <= 0.0) continue
            val value = numerator / denominator
            if (!value.isFinite()) continue
            if (first == null) {
                first = sample
                firstValue = value
            }
            if (value < minimumValue) {
                minimum = sample
                minimumValue = value
            }
            if (value > maximumValue) {
                maximum = sample
                maximumValue = value
            }
            last = sample
            lastValue = value
            numeratorTotal += numerator
            denominatorTotal += denominator
        }
        if (first == null || last == null || minimum == null || maximum == null) return null
        return PerformanceStatusChartSummary(
            PerformanceStatusChartPoint(first.sampleIndex, first.elapsedMillis, firstValue),
            PerformanceStatusChartPoint(last.sampleIndex, last.elapsedMillis, lastValue),
            PerformanceStatusChartPoint(minimum.sampleIndex, minimum.elapsedMillis, minimumValue),
            PerformanceStatusChartPoint(maximum.sampleIndex, maximum.elapsedMillis, maximumValue),
            numeratorTotal, denominatorTotal,
        )
    }

    /** 追加完整块；仅在右兄弟完成时生成父块，因此累计合并工作与新增块数成正比。 */
    private fun appendBlock(levelIndex: Int, block: Long, summary: PerformanceStatusChartSummary?) {
        if (levelIndex == levels.size) levels.add(Level(block))
        val level = levels[levelIndex]
        if (level.values.isEmpty()) level.start = block
        level.values.addLast(summary)
        if (block % 2L == 1L && level.values.size >= 2) {
            val left = level.values[level.values.lastIndex - 1]
            appendBlock(levelIndex + 1, block / 2L, left?.followedBy(summary) ?: summary)
        }
    }

    /** 从一个有效原样本建立统计；比值分子分母分别累加，不能平均各点的比值。 */
    private fun summaryOf(sample: PerformanceStatusSample): PerformanceStatusChartSummary? {
        val numerator: Double
        val denominator: Double
        when (selection) {
            is PerformanceStatusChartSelection.Metric -> {
                numerator = selection.extract(sample) ?: return null
                denominator = 1.0
            }
            is PerformanceStatusChartSelection.Ratio -> {
                numerator = selection.impact.extract(sample) ?: return null
                denominator = selection.performance.extract(sample) ?: return null
            }
        }
        if (!numerator.isFinite() || numerator < 0.0 || !denominator.isFinite() || denominator <= 0.0) return null
        val value = numerator / denominator
        if (!value.isFinite()) return null
        val point = PerformanceStatusChartPoint(sample.sampleIndex, sample.elapsedMillis, value)
        return PerformanceStatusChartSummary(point, point, point, point, numerator, denominator)
    }

    /** 单层完整块的环形存储，start 是首块的绝对块序号。 */
    private class Level(
        /** 当前首块的绝对序号；裁剪后同步前移。 */
        var start: Long,
    ) {
        /** 空统计仍占用一个块位置，避免稀疏网络指标错位。 */
        val values = ArrayDeque<PerformanceStatusChartSummary?>()
    }
}

/**
 * 按 10Hz 缓存图表数据，时间窗或曲线选择改变时立即刷新。
 * 历史保留逐 tick 原值；每桶只绘制首尾和峰谷，长期记录不增加每帧遍历量。
 */
internal class PerformanceStatusChartData {
    /** 仅索引已选曲线，取消选择后立即释放索引。 */
    private val indices = linkedMapOf<PerformanceStatusChartSelection, PerformanceStatusChartIndex>()
    /** 上次绘图数据对应的原始样本。 */
    private var latest: PerformanceStatusSample? = null
    /** 上次缓存使用的历史首样本，用于识别没有新增采样的历史裁剪。 */
    private var firstSample: PerformanceStatusSample? = null
    /** 上次缓存使用的样本数量；主动缩短历史必须立即更新。 */
    private var historySize = 0
    /** 上次窗口的起点比例。 */
    private var start = -1.0
    /** 上次窗口的终点比例。 */
    private var end = -1.0
    /** 上次绘图区允许的时间桶数量。 */
    private var buckets = 0
    /** 上次选择顺序，颜色与图例必须沿用此顺序。 */
    private var selections = emptyList<PerformanceStatusChartSelection>()
    /** 上次数据重建的单调时间纳秒。 */
    private var refreshedAt = 0L
    /** 无需重新遍历原始历史的渲染数据。 */
    private var cached = emptyList<PerformanceStatusChartSeries>()
    /** 缓存窗口的左边界，单位为会话毫秒。 */
    var firstMillis = 0L
        private set
    /** 缓存窗口的右边界，单位为会话毫秒。 */
    var lastMillis = 0L
        private set

    /** 准备有界绘图点与完整窗口统计；同一帧数据可在任意 FPS 下重复读取。 */
    fun prepare(
        history: List<PerformanceStatusSample>,
        selected: Collection<PerformanceStatusChartSelection>,
        rangeStart: Double,
        rangeEnd: Double,
        bucketCount: Int,
        nowNanos: Long,
    ): List<PerformanceStatusChartSeries> {
        require(bucketCount > 0)
        require(rangeStart in 0.0..1.0 && rangeEnd in rangeStart..1.0)
        val sameViewport = rangeStart == start && rangeEnd == end && bucketCount == buckets &&
            selected.size == selections.size && selected.withIndex().all { selections[it.index] == it.value }
        val previous = latest
        if (history.isNotEmpty() && sameViewport && previous != null) {
            val first = history.first()
            val last = history.last()
            if (last === previous && first === firstSample) return cached
            val offset = previous.sampleIndex - first.sampleIndex
            val sameSession = offset in 0L until history.size.toLong() && history[offset.toInt()] === previous
            if (sameSession && last.sampleIndex > previous.sampleIndex && history.size >= historySize &&
                nowNanos - refreshedAt in 0L until 100_000_000L) return cached
        }
        start = rangeStart
        end = rangeEnd
        buckets = bucketCount
        selections = selected.toList()
        refreshedAt = nowNanos
        latest = history.lastOrNull()
        firstSample = history.firstOrNull()
        historySize = history.size
        indices.keys.retainAll(selected.toSet())
        if (history.isEmpty()) {
            indices.clear()
            cached = emptyList()
            firstMillis = 0L
            lastMillis = 0L
            return cached
        }
        val first = history.first().elapsedMillis
        val duration = history.last().elapsedMillis - first
        firstMillis = first + (duration * rangeStart).toLong()
        lastMillis = first + (duration * rangeEnd).toLong()
        val windowStart = lowerBound(history, firstMillis)
        val windowEnd = lowerBound(history, lastMillis, inclusive = true)
        cached = selected.map { selection ->
            val index = indices.getOrPut(selection) { PerformanceStatusChartIndex(selection) }
            index.sync(history)
            val points = ArrayList<PerformanceStatusChartPoint>()
            var summary: PerformanceStatusChartSummary? = null
            var from = windowStart
            repeat(bucketCount) { bucket ->
                val to = if (bucket == bucketCount - 1) windowEnd else {
                    val boundary = firstMillis + ((lastMillis - firstMillis).toDouble() * (bucket + 1) / bucketCount).toLong()
                    lowerBound(history, boundary).coerceIn(from, windowEnd)
                }
                val part = index.query(history, from, to)
                if (part != null) {
                    summary = summary?.followedBy(part) ?: part
                    // 保留原始时间顺序，尤其不能把同一桶里的峰和谷画反。
                    val candidates = listOf(part.first, part.minimum, part.maximum, part.last).sortedBy { it.sampleIndex }
                    for (point in candidates) {
                        if (points.lastOrNull()?.sampleIndex != point.sampleIndex) points.add(point)
                    }
                }
                from = to
            }
            PerformanceStatusChartSeries(selection, points, summary)
        }
        return cached
    }

    /** 二分定位真实时间边界；inclusive 为 true 时返回第一个严格大于目标时间的位置。 */
    private fun lowerBound(history: List<PerformanceStatusSample>, millis: Long, inclusive: Boolean = false): Int {
        var low = 0
        var high = history.size
        while (low < high) {
            val middle = low + (high - low) / 2
            val time = history[middle].elapsedMillis
            if (time < millis || inclusive && time == millis) low = middle + 1 else high = middle
        }
        return low
    }
}

/** 当前纵轴边界；归一化仅用于显示，不改变原始数据及窗口统计。 */
internal data class PerformanceStatusChartScale(
    /** 纵轴下界，始终不小于零。 */
    val minimum: Double,
    /** 纵轴上界，严格大于下界。 */
    val maximum: Double,
)

/** 生成零基线或局部放大量程；毫秒与比值不再被固定的 50ms 或 1.0 上界压平。 */
internal fun performanceStatusChartScale(
    summary: PerformanceStatusChartSummary?,
    fit: Boolean,
): PerformanceStatusChartScale {
    if (summary == null || summary.maximum.value == 0.0) return PerformanceStatusChartScale(0.0, 1.0)
    val low = summary.minimum.value
    val high = summary.maximum.value
    if (!fit) return PerformanceStatusChartScale(0.0, high * 1.05)
    val padding = maxOf((high - low) * 0.1, high * 0.005)
    return PerformanceStatusChartScale((low - padding).coerceAtLeast(0.0), high + padding)
}
