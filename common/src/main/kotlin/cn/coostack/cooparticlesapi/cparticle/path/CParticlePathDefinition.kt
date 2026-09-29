package cn.coostack.cooparticlesapi.cparticle.path

import cn.coostack.cooparticlesapi.gpudata.CooGpuDataLayer
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * # 路径资源
 *
 * 保存控制点、控制柄、段类型与闭合属性，并派生一份**预采样表**供 GPU 与 CPU 一致地求值。
 *
 * ## 职责边界
 * 路径资源只回答几何问题：某个参数处在哪里、朝哪、横截面参考基是什么、弧长是多少。
 * 它不保存播放模式、进度来源、环绕半径或姿态偏移——那些属于使用端的运动或绘制逻辑。
 *
 * ## 精度说明
 * 预采样表的样本数由 [samplesPerSegment] 独立决定，**不**等于控制点数：
 * - 控制点数只决定控制点表的有效长度。
 * - 预采样按均匀参数采样；贝塞尔求值在 shader 与 CPU 内直接用三次 Bernstein 多项式，
 *   不靠纹理线性过滤冒充曲线求值。
 * - 弧长由相邻样本弦长累加近似；[arcLengthToleranceRatio] 记录“单个样本间距占总长的最大比例”，
 *   用来描述当前采样密度下的近似量级。
 *
 * ## 空间约定
 * 控制点位于**持有者的本地空间**。使用端把求值结果乘以自己的绑定变换（位置/旋转/缩放），
 * 因此多个对象可以共享同一条路径定义，各自保留独立变换。
 *
 * 本类型只在客户端渲染线程写入图层；服务端可以创建声明并同步句柄，但不能写入图层数据。
 */
class CParticlePathDefinition(
    points: List<CParticlePathPoint>,
    /** 段类型；改变后所有段按新类型重新解释控制柄。 */
    var segmentType: CParticlePathSegmentType = CParticlePathSegmentType.LINEAR,
    /** 几何是否首尾相连。闭合会在负载末尾补一个等于首点的接缝点。 */
    var closed: Boolean = false,
) {
    init {
        require(points.size >= 2) { "Particle path needs at least two control points, got ${points.size}" }
    }

    private var controlPoints: MutableList<CParticlePathPoint> = points.toMutableList()

    /**
     * 几何与控制柄的修订号。
     *
     * 每次几何、段类型或闭合属性变化都会递增。图层同步层用它判断是否需要重新采样与重传，
     * 因此静态路径在首次写入后不会再产生采样与上传开销。
     */
    var revision: Long = 0L
        private set

    /**
     * 每段的均匀参数采样数。
     *
     * 越大则高曲率贝塞尔段越平滑、弧长近似越准，代价是每路径显存占用线性增长。
     */
    var samplesPerSegment: Int = defaultSamplesPerSegment(points.size)
        set(value) {
            val clamped = value.coerceIn(1, MAX_SAMPLES_PER_SEGMENT)
            if (field == clamped) return
            field = clamped
            invalidateSamples()
        }

    private var cachedTable: PathSampleTable? = null

    /** 用户编辑用的控制点列表**副本**；修改它不会影响资源，必须调用 [setPoints]。 */
    val points: List<CParticlePathPoint>
        get() = controlPoints.toList()

    /** 实际写入图层的控制点数，包含闭合路径末尾的接缝重复点。 */
    val layerPointCount: Int
        get() = if (closed) controlPoints.size + 1 else controlPoints.size

    /** 段数，等于写入图层的控制点数减一。 */
    val segmentCount: Int
        get() = layerPointCount - 1

    /** 当前预采样表的样本数。 */
    val sampleCount: Int
        get() = sampleTable.sampleCount

    /** 预采样弧长近似得到的曲线总长。 */
    val totalLength: Double
        get() = sampleTable.totalLength

    /** 弧长近似的精度描述，见 [PathSampleTable.toleranceRatio]。 */
    val arcLengthToleranceRatio: Double
        get() = sampleTable.toleranceRatio

    /**
     * 整体替换控制点。
     *
     * 支持运行时增点与减点；图层容量按倍增策略管理，减点不会立刻缩容。
     *
     * @param next 新的控制点序列，至少两个点
     */
    fun setPoints(next: List<CParticlePathPoint>) {
        require(next.size >= 2) { "Particle path needs at least two control points, got ${next.size}" }
        controlPoints = next.toMutableList()
        invalidateSamples()
    }

    /** 追加一个控制点。 */
    fun addPoint(point: CParticlePathPoint) {
        controlPoints.add(point)
        invalidateSamples()
    }

    /**
     * 移除指定下标的控制点。
     *
     * @param index 要移除的下标
     * @throws IllegalArgumentException 移除后点数少于两个时抛出
     */
    fun removePoint(index: Int) {
        require(index in controlPoints.indices) { "Particle path point index out of range: $index" }
        require(controlPoints.size > 2) { "Particle path keeps at least two control points" }
        controlPoints.removeAt(index)
        invalidateSamples()
    }

    /**
     * 平移全部控制点，控制柄保持不变。
     *
     * 示例：共享路径整体移动时调用一次，所有引用者立即看到新几何。
     * 禁止：只想让某个对象看到偏移后的路径时应改绑定变换，不要修改共享资源。
     *
     * @param offset 本地空间平移量
     */
    fun translate(offset: Vec3) {
        if (offset == Vec3.ZERO) return
        controlPoints = controlPoints.map { it.copy(position = it.position.add(offset)) }.toMutableList()
        invalidateSamples()
    }

    /** 让预采样表在下次访问时重建，并递增 [revision]。 */
    fun invalidateSamples() {
        cachedTable = null
        revision++
    }

    /** 取当前预采样表，必要时重建。 */
    val sampleTable: PathSampleTable
        get() = cachedTable ?: buildSamples().also { cachedTable = it }

    // ------------------------------------------------------------------ 求值

    /**
     * 求某个全局参数处的本地空间位置。
     *
     * 参数 `1` 明确落在最后一段的终点：直接 `toInt()` 截断会因浮点误差把 `1.0 * 段数` 变成
     * `段数 - 1`，从而错误地回到第一段内部，导致采样表重复计算首段、曲线总长系统性偏大。
     *
     * @param u 全局曲线参数，取值域为 `0..1`
     * @return 该参数处的本地位置
     */
    fun positionAt(u: Double): Vec3 {
        val clamped = u.coerceIn(0.0, 1.0)
        if (clamped >= 1.0) return pointOnSegment(segmentCount - 1, 1.0)
        val scaled = clamped * segmentCount
        val segment = scaled.toInt().coerceIn(0, segmentCount - 1)
        val local = (scaled - segment).coerceIn(0.0, 1.0)
        return pointOnSegment(segment, local)
    }

    /**
     * 求某个全局参数处的单位切线。
     *
     * 折线拐角与零长度段使用相邻段的平均方向，两端按单侧差分；方向全部退化时回退到零向量，
     * 由横截面参考基沿用上一个有效值，不会产生 NaN。
     *
     * @param u 全局曲线参数，取值域为 `0..1`
     * @return 单位切线；整条路径退化时返回 `Vec3.ZERO`
     */
    fun tangentAt(u: Double): Vec3 {
        val clamped = u.coerceIn(0.0, 1.0)
        if (clamped >= 1.0) return segmentDirection(segmentCount - 1, 1.0)
        val scaled = clamped * segmentCount
        val segment = scaled.toInt().coerceIn(0, segmentCount - 1)
        val local = (scaled - segment).coerceIn(0.0, 1.0)
        val direct = segmentDirection(segment, local)
        if (direct != Vec3.ZERO) return direct
        for (offset in 1..segmentCount) {
            val before = segment - offset
            if (before >= 0) {
                val candidate = segmentDirection(before, 1.0)
                if (candidate != Vec3.ZERO) return candidate
            }
            val after = segment + offset
            if (after < segmentCount) {
                val candidate = segmentDirection(after, 0.0)
                if (candidate != Vec3.ZERO) return candidate
            }
            if (before < 0 && after >= segmentCount) break
        }
        return Vec3.ZERO
    }

    private fun segmentDirection(segmentIndex: Int, localParameter: Double): Vec3 {
        val low = (localParameter - DERIVATIVE_STEP).coerceAtLeast(0.0)
        val high = (localParameter + DERIVATIVE_STEP).coerceAtMost(1.0)
        if (high > low) {
            val difference = pointOnSegment(segmentIndex, high).subtract(pointOnSegment(segmentIndex, low))
            if (difference.lengthSqr() > MIN_DIRECTION_SQUARED) return difference.normalize()
        }
        if (segmentIndex > 0) {
            val previous = pointOnSegment(segmentIndex, 0.0).subtract(pointOnSegment(segmentIndex - 1, 1.0))
            if (previous.lengthSqr() > MIN_DIRECTION_SQUARED) return previous.normalize()
        }
        if (segmentIndex + 1 < segmentCount) {
            val next = pointOnSegment(segmentIndex + 1, 0.0).subtract(pointOnSegment(segmentIndex, 1.0))
            if (next.lengthSqr() > MIN_DIRECTION_SQUARED) return next.normalize()
        }
        return Vec3.ZERO
    }

    /**
     * 把归一化弧长参数映射到全局曲线参数。
     *
     * [CParticlePathProgressMode.PARAMETER] 直接返回输入；[CParticlePathProgressMode.ARC_LENGTH]
     * 通过预采样累计弧长做一次二分定位与一次线性插值，复杂度与粒子数无关。
     *
     * @param tau 归一化弧长，取值域 `0..1`
     * @param mode 进度映射方式
     * @return 对应的全局曲线参数
     */
    fun parameterAtArcFraction(tau: Double, mode: CParticlePathProgressMode): Double {
        val clamped = tau.coerceIn(0.0, 1.0)
        if (mode == CParticlePathProgressMode.PARAMETER) return clamped
        val table = sampleTable
        if (table.totalLength <= MIN_DIRECTION_SQUARED) return clamped
        return table.parameterAtDistance(clamped * table.totalLength)
    }

    private fun pointOnSegment(segmentIndex: Int, localParameter: Double): Vec3 {
        val safeIndex = segmentIndex.coerceIn(0, segmentCount - 1)
        val t = localParameter.coerceIn(0.0, 1.0)
        val start = layerPoint(safeIndex)
        val end = layerPoint(safeIndex + 1)
        if (segmentType == CParticlePathSegmentType.LINEAR) {
            return start.position.add(end.position.subtract(start.position).scale(t))
        }
        val firstControl = start.position.add(start.outHandle)
        val secondControl = end.position.add(end.inHandle)
        return cubicBezier(start.position, firstControl, secondControl, end.position, t)
    }

    /** 取写入图层的控制点；闭合路径在末尾返回等于首点的接缝点。 */
    private fun layerPoint(index: Int): CParticlePathPoint {
        if (index < controlPoints.size) return controlPoints[index]
        return controlPoints[0]
    }

    // ------------------------------------------------------------------ 采样

    /**
     * 构造预采样表。
     *
     * 采样按**段内均匀**分配，而不是全局参数均匀：每段固定若干样本，段末样本与下一段首样本
     * 落在同一个参数上。这样开放路径的最后一个样本正好落在参数 `1`，闭合路径的最后一个样本
     * 落在最后一段末端，弧长表因此覆盖整条曲线——全局均匀会让最后一段永远缺一个样本，
     * 曲线总长系统性偏短，弧长映射也随之失真。
     *
     * @return 采样表
     */
    private fun buildSamples(): PathSampleTable {
        val segmentTotal = segmentCount
        val totalSamples = segmentTotal * samplesPerSegment + if (closed) 0 else 1
        val table = PathSampleTable(segmentTotal, totalSamples, samplesPerSegment, closed)
        // 闭合表是循环的，因此分母用样本数；开放表有一个额外样本落回参数 1，分母用样本数减一。
        val parameterDenominator = if (closed) totalSamples else totalSamples - 1

        for (sample in 0 until totalSamples) {
            table.positions[sample] = positionAt(sample.toDouble() / parameterDenominator.toDouble())
        }
        for (sample in 0 until totalSamples) {
            val lowIndex = if (sample == 0) (if (closed) totalSamples - 1 else 0) else sample - 1
            val highIndex = if (sample == totalSamples - 1) (if (closed) 0 else totalSamples - 1) else sample + 1
            table.tangents[sample] = normalizeOrZero(
                table.positions[highIndex].subtract(table.positions[lowIndex]),
            )
        }
        seedDegenerateTangents(table)

        var distance = 0.0
        var longestStep = 0.0
        table.distances[0] = 0.0
        for (sample in 0 until totalSamples) {
            val next = sample + 1
            if (next >= totalSamples) {
                if (closed) distance += table.positions[0].distanceTo(table.positions[sample])
                break
            }
            val step = table.positions[next].distanceTo(table.positions[sample])
            longestStep = max(longestStep, step)
            distance += step
            table.distances[next] = distance
        }
        table.totalLength = distance
        table.toleranceRatio = if (distance <= 0.0) 0.0 else longestStep / distance
        buildFrames(table)
        return table
    }

    /**
     * 用旋转最小化（投影）方式构造沿曲线连续的横截面参考基。
     *
     * 每一步把上一步的法线投影到当前切线的垂直平面并重新正交化，因此不会像
     * `cross(tangent, 固定 up)` 那样在切线与 up 平行处突然翻转；闭合接缝、折线拐角、
     * 零长度段与零速度都沿用上一个有效基，而不是产生 NaN。
     */
    private fun buildFrames(table: PathSampleTable) {
        var latest = Vec3.ZERO
        for (sample in 0 until table.sampleCount) {
            val tangent = table.tangents[sample]
            if (tangent.lengthSqr() <= MIN_DIRECTION_SQUARED) continue
            if (latest == Vec3.ZERO) latest = orthonormalCandidate(tangent)
            val projected = latest.subtract(tangent.scale(latest.dot(tangent)))
            val normal = if (projected.lengthSqr() > MIN_DIRECTION_SQUARED) {
                projected.normalize()
            } else {
                orthonormalCandidate(tangent)
            }
            table.normals[sample] = normal
            table.binormals[sample] = tangent.cross(normal).normalize()
            latest = normal
        }
        var fallbackNormal = Vec3.ZERO
        var fallbackBinormal = Vec3.ZERO
        for (sample in 0 until table.sampleCount) {
            if (table.normals[sample].lengthSqr() > MIN_DIRECTION_SQUARED) {
                fallbackNormal = table.normals[sample]
                fallbackBinormal = table.binormals[sample]
                continue
            }
            table.normals[sample] = fallbackNormal
            table.binormals[sample] = fallbackBinormal
        }
    }

    private fun seedDegenerateTangents(table: PathSampleTable) {
        var latest = Vec3.ZERO
        for (sample in 0 until table.sampleCount) {
            if (table.tangents[sample].lengthSqr() > MIN_DIRECTION_SQUARED) {
                latest = table.tangents[sample]
                continue
            }
            table.tangents[sample] = latest
        }
        var next = Vec3.ZERO
        for (sample in table.sampleCount - 1 downTo 0) {
            if (table.tangents[sample].lengthSqr() > MIN_DIRECTION_SQUARED) {
                next = table.tangents[sample]
                continue
            }
            table.tangents[sample] = next
        }
    }

    // ------------------------------------------------------------------ 图层

    /**
     * 把当前几何与预采样表写入公共图层的指定基址。
     *
     * @param layer 目标图层；布局必须是 [CooPathLayer.layout]
     * @param base 本路径在图层中的 float 基址
     * @param layerRevision 当前图层重建版本，写入负载表头供运行时校验基址是否过期
     */
    fun writeToLayer(layer: CooGpuDataLayer, base: Int, layerRevision: Int) {
        require(layer.layout === CooPathLayer.layout) {
            "Particle path layer must use ${CooPathLayer.layout.id}, got ${layer.layout.id}"
        }
        val table = sampleTable
        val pointTotal = layerPointCount
        val requiredFloats = base + CooPathLayer.CooPathPayload.floats(pointTotal, table.sampleCount)
        require(requiredFloats <= layer.data.size) {
            "Particle path payload does not fit at base $base: needs $requiredFloats floats, " +
                "layer holds ${layer.data.size}"
        }
        val data = layer.data
        data[base + CooPathLayer.CooPathPayload.H_MAGIC] = CooPathLayer.MAGIC.toFloat()
        data[base + CooPathLayer.CooPathPayload.H_ABI] = CooPathLayer.ABI_VERSION.toFloat()
        data[base + CooPathLayer.CooPathPayload.H_SEGMENT_COUNT] = segmentCount.toFloat()
        data[base + CooPathLayer.CooPathPayload.H_POINT_COUNT] = pointTotal.toFloat()
        data[base + CooPathLayer.CooPathPayload.H_SAMPLE_COUNT] = table.sampleCount.toFloat()
        data[base + CooPathLayer.CooPathPayload.H_CLOSED] = if (closed) 1F else 0F
        data[base + CooPathLayer.CooPathPayload.H_SEGMENT_TYPE] = segmentType.wireValue.toFloat()
        data[base + CooPathLayer.CooPathPayload.H_SAMPLES_PER_SEGMENT] = table.samplesPerSegment.toFloat()
        data[base + CooPathLayer.CooPathPayload.H_TOTAL_LENGTH] = table.totalLength.toFloat()
        data[base + CooPathLayer.CooPathPayload.H_LENGTH_TOLERANCE] = table.toleranceRatio.toFloat()
        data[base + CooPathLayer.CooPathPayload.H_LAYER_REVISION] =
            layerRevision.coerceIn(0, CParticlePathCommandPacker.MAX_EXACT_LAYER_VERSION).toFloat()
        data[base + CooPathLayer.CooPathPayload.H_PARAM_DENOMINATOR] = table.parameterDenominator.toFloat()

        for (index in 0 until pointTotal) {
            val point = layerPoint(index)
            val pointBase = base + CooPathLayer.CooPathPayload.POINT_BASE +
                index * CooPathLayer.CooPathPayload.POINT_STRIDE
            putVec3(data, pointBase + CooPathLayer.CooPathPayload.POINT_POSITION, point.position)
            putVec3(data, pointBase + CooPathLayer.CooPathPayload.POINT_IN_HANDLE, point.inHandle)
            putVec3(data, pointBase + CooPathLayer.CooPathPayload.POINT_OUT_HANDLE, point.outHandle)
        }

        val sampleBase = base + CooPathLayer.CooPathPayload.sampleBase(pointTotal)
        for (sample in 0 until table.sampleCount) {
            val sampleOffset = sampleBase + sample * CooPathLayer.CooPathPayload.SAMPLE_STRIDE
            putVec3(data, sampleOffset + CooPathLayer.CooPathPayload.SAMPLE_POSITION, table.positions[sample])
            putVec3(data, sampleOffset + CooPathLayer.CooPathPayload.SAMPLE_TANGENT, table.tangents[sample])
            // 累计弧长必须单独写入：它是弧长映射的唯一数据来源，缺失或与位置混写会让
            // “弧长均匀”退化成错误的位置分布。
            data[sampleOffset + CooPathLayer.CooPathPayload.SAMPLE_DISTANCE] = table.distances[sample].toFloat()
            putVec3(data, sampleOffset + CooPathLayer.CooPathPayload.SAMPLE_NORMAL, table.normals[sample])
            putVec3(data, sampleOffset + CooPathLayer.CooPathPayload.SAMPLE_BINORMAL, table.binormals[sample])
        }
    }

    companion object {
        /** 单个路径段允许的最大均匀采样数，防止误配置造成显存爆炸。 */
        const val MAX_SAMPLES_PER_SEGMENT = 64

        /** 默认每段采样数下限：保证高曲率贝塞尔段不会采样过疏。 */
        private const val MIN_SAMPLES_PER_SEGMENT = 8

        /** 差分求切线的参数步长。 */
        private const val DERIVATIVE_STEP = 1.0E-3

        /** 判定方向向量是否可用的平方长度阈值。 */
        private const val MIN_DIRECTION_SQUARED = 1.0E-18

        /**
         * 按控制点数给出默认的每段采样数。
         *
         * @param pointCount 控制点数
         * @return 介于下限与 [MAX_SAMPLES_PER_SEGMENT] 之间的采样数
         */
        @JvmStatic
        fun defaultSamplesPerSegment(pointCount: Int): Int =
            max(MIN_SAMPLES_PER_SEGMENT, min(MAX_SAMPLES_PER_SEGMENT, pointCount * 2))

        /** 计算一条路径在图层负载中需要的 float 数。 */
        @JvmStatic
        fun payloadFloats(pointCount: Int, sampleCount: Int): Int =
            CooPathLayer.CooPathPayload.floats(pointCount, sampleCount)

        /** 把 float 数换算成图层元素个数；路径图层布局的成分为 1，因此两者相等。 */
        @JvmStatic
        fun layerElements(floatCount: Int): Int =
            ceil(floatCount.toDouble() / CooPathLayer.layout.componentCount).toInt()

        private fun cubicBezier(first: Vec3, firstControl: Vec3, secondControl: Vec3, second: Vec3, t: Double): Vec3 {
            val inverse = 1.0 - t
            val inverseSquared = inverse * inverse
            val tSquared = t * t
            return first.scale(inverseSquared * inverse)
                .add(firstControl.scale(3.0 * inverseSquared * t))
                .add(secondControl.scale(3.0 * inverse * tSquared))
                .add(second.scale(tSquared * t))
        }

        private fun normalizeOrZero(vector: Vec3): Vec3 =
            if (vector.lengthSqr() > MIN_DIRECTION_SQUARED) vector.normalize() else Vec3.ZERO

        /** 选择与切线夹角最大的坐标轴作为初始法线候选，避免投影退化。 */
        private fun orthonormalCandidate(tangent: Vec3): Vec3 {
            val absoluteX = abs(tangent.x)
            val absoluteY = abs(tangent.y)
            val absoluteZ = abs(tangent.z)
            val axis = when {
                absoluteX <= absoluteY && absoluteX <= absoluteZ -> Vec3(1.0, 0.0, 0.0)
                absoluteY <= absoluteZ -> Vec3(0.0, 1.0, 0.0)
                else -> Vec3(0.0, 0.0, 1.0)
            }
            val projected = axis.subtract(tangent.scale(axis.dot(tangent)))
            return if (projected.lengthSqr() > MIN_DIRECTION_SQUARED) projected.normalize() else Vec3.ZERO
        }

        private fun putVec3(target: FloatArray, base: Int, value: Vec3) {
            target[base] = value.x.toFloat()
            target[base + 1] = value.y.toFloat()
            target[base + 2] = value.z.toFloat()
        }
    }
}

/**
 * # 路径预采样表
 *
 * 保存按段内均匀参数采样的位置、单位切线，以及沿曲线连续的横截面参考基，另加累计弦长表。
 * 由 [CParticlePathDefinition] 派生，使用端不应直接构造。
 */
class PathSampleTable internal constructor(
    /** 段数。 */
    val segmentCount: Int,
    /** 样本数。 */
    val sampleCount: Int,
    /** 每段均匀采样数。 */
    val samplesPerSegment: Int,
    /** 几何是否闭合。 */
    val closed: Boolean,
) {
    internal val positions = Array(sampleCount) { Vec3.ZERO }
    internal val tangents = Array(sampleCount) { Vec3.ZERO }
    internal val normals = Array(sampleCount) { Vec3.ZERO }
    internal val binormals = Array(sampleCount) { Vec3.ZERO }

    /** 累计弧长；下标 0 恒为 0。 */
    internal val distances = DoubleArray(sampleCount)

    /** 曲线总长。 */
    internal var totalLength: Double = 0.0

    /** 单个样本间距占总长的最大比例，作为弧长近似精度描述。 */
    internal var toleranceRatio: Double = 0.0

    /**
     * 样本下标换算全局曲线参数时的分母。
     *
     * 开放表最后一个样本落在参数 1，因此分母是 `sampleCount - 1`；闭合表的最后一个样本之后
     * 还有一段回到首样本的连接段，分母是 `sampleCount`。GPU 侧的 `pathParameterAtDistance`
     * 必须使用同样的分母，否则弧长映射会在两端产生偏差。
     */
    internal val parameterDenominator: Int
        get() = if (closed) sampleCount else sampleCount - 1

    /**
     * 按累计弧长反查全局曲线参数。
     *
     * 二分定位样本区间后做一次线性插值，复杂度 `O(log n)`，与粒子数无关。
     *
     * @param distance 目标弧长
     * @return 全局曲线参数，取值域 `0..1`
     */
    internal fun parameterAtDistance(distance: Double): Double {
        if (sampleCount <= 1) return 0.0
        val target = distance.coerceIn(0.0, totalLength)
        var low = 0
        var high = sampleCount - 1
        while (low + 1 < high) {
            val middle = (low + high) ushr 1
            if (distances[middle] <= target) low = middle else high = middle
        }
        val spanStart = distances[low]
        val spanEnd = distances[high]
        val ratio = if (spanEnd - spanStart <= 1.0E-12) 0.0 else (target - spanStart) / (spanEnd - spanStart)
        return ((low + ratio) / parameterDenominator.toDouble()).coerceIn(0.0, 1.0)
    }
}
