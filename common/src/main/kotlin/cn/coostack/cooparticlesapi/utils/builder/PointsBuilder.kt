package cn.coostack.cooparticlesapi.utils.builder

import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.SequencedParticleStyle
import cn.coostack.cooparticlesapi.extend.ofFloored
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.particles.control.group.SequencedParticleGroup
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.MathPresets
import cn.coostack.cooparticlesapi.utils.NoiseMode
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.BezierNode
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import cn.coostack.cooparticlesapi.extend.asRelative
import java.util.HashMap
import java.util.HashSet
import java.util.SortedMap
import java.util.TreeMap
import kotlin.math.floor

/**
 * 点集构建器：用于组合、变换并导出一组 {@link RelativeLocation}。
 *
 * ## 它是什么
 * - 内部维护一个 `points: ArrayList<RelativeLocation>`
 * - 你可以不断 `addXxx(...)` 往里面塞点
 * - 也可以对当前点集做 `rotateAsAxis / rotateTo / pointsOnEach` 等批量变换
 * - 最终用 `create()` 复制导出点集（避免外部修改影响 builder 内部）
 *
 * ## 坐标语义
 * - 这里的点默认是“相对坐标”（RelativeLocation），通常表示以某个发射器/实体为原点的偏移。
 * - 多数 `addXxx(...)` 方法调用 `Math3DUtil` 生成的点，默认以 (0,0,0) 为中心。
 *
 * ## axis 的意义
 * `axis` 表示“当前图形的对称轴 / 参考轴”：
 * - `rotateAsAxis(...)`：绕 axis 旋转点集
 * - `rotateTo(...)`：将 axis 指向某个方向（让图形朝向目标点）
 */
class PointsBuilder {
    companion object {
        private data class MaskGridKey(val x: Long, val y: Long, val z: Long)

        /**
         * 创建一个 PointsBuilder，并设置其对称轴为 [axis]。
         *
         * @param axis 图形对称轴（相对向量）。常用：`RelativeLocation.yAxis()`
         */
        @JvmStatic
        fun of(axis: RelativeLocation): PointsBuilder {
            return PointsBuilder().also { it.axis = axis }
        }

        /**
         * 使用已有点集创建 PointsBuilder。
         * 默认对称轴为 Y 轴（`RelativeLocation.yAxis()`）。
         *
         * @param points 初始点集合（会被 add 进 builder）
         */
        @JvmStatic
        fun of(points: Collection<RelativeLocation>): PointsBuilder {
            return PointsBuilder().also { it.addPoints(points) }
        }

        /**
         * 使用对称轴与点集一起创建 PointsBuilder。
         *
         * @param axis 图形对称轴
         * @param points 初始点集合
         */
        @JvmStatic
        fun of(axis: RelativeLocation, points: Collection<RelativeLocation>): PointsBuilder {
            return PointsBuilder().also { it.axis = axis; it.addPoints(points) }
        }

        private fun maskGridKey(point: RelativeLocation, inverseCellSize: Double): MaskGridKey {
            return MaskGridKey(
                floor(point.x * inverseCellSize).toLong(),
                floor(point.y * inverseCellSize).toLong(),
                floor(point.z * inverseCellSize).toLong()
            )
        }

        /**
         * 按输入顺序执行点遮罩：后来的点保留，删除所有距离它小于 [maskRange] 的旧点。
         *
         * 采用三维空间分桶，只扫描当前点所在桶及周围 26 个邻桶，避免全量 O(n^2) 扫描。
         */
        private fun applyMaskInPlace(points: MutableList<RelativeLocation>, maskRange: Double) {
            if (points.isEmpty() || maskRange <= 0.0 || maskRange.isNaN()) {
                return
            }

            val originalSize = points.size
            val inverseCellSize = 1.0 / maskRange
            val rangeSq = maskRange * maskRange
            val alive = BooleanArray(originalSize)
            val buckets = HashMap<MaskGridKey, MutableSet<Int>>(originalSize * 2)

            for (index in 0 until originalSize) {
                val point = points[index]
                val cell = maskGridKey(point, inverseCellSize)

                for (dx in -1..1) {
                    for (dy in -1..1) {
                        for (dz in -1..1) {
                            val bucket = buckets[MaskGridKey(
                                cell.x + dx.toLong(),
                                cell.y + dy.toLong(),
                                cell.z + dz.toLong()
                            )] ?: continue

                            val iterator = bucket.iterator()
                            while (iterator.hasNext()) {
                                val candidateIndex = iterator.next()
                                if (!alive[candidateIndex]) {
                                    iterator.remove()
                                    continue
                                }

                                val candidate = points[candidateIndex]
                                val offsetX = candidate.x - point.x
                                val offsetY = candidate.y - point.y
                                val offsetZ = candidate.z - point.z
                                if (offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ < rangeSq) {
                                    alive[candidateIndex] = false
                                    iterator.remove()
                                }
                            }
                        }
                    }
                }

                alive[index] = true
                buckets.getOrPut(cell) { HashSet() }.add(index)
            }

            var write = 0
            for (read in 0 until originalSize) {
                if (alive[read]) {
                    points[write++] = points[read]
                }
            }
            if (write < originalSize) {
                points.subList(write, originalSize).clear()
            }
        }

        /**
         * 对 PointsBuilder 直接执行点遮罩，结果会回写到 builder 内部。
         */
        @JvmStatic
        fun clearAsMask(builder: PointsBuilder, maskRange: Double): PointsBuilder {
            applyMaskInPlace(builder.points, maskRange)
            return builder
        }

        /**
         * 对 PointsBuilder 执行点遮罩后，再把额外点集合并入 builder。
         *
         * @param points 支持直接传入点集合
         */
        @JvmStatic
        fun clearAsMaskAndJoin(
            builder: PointsBuilder,
            points: Collection<RelativeLocation>,
            maskRange: Double
        ): PointsBuilder {
            clearAsMask(builder, maskRange)
            builder.points.addAll(points)
            return builder
        }

        /**
         * 对 PointsBuilder 执行点遮罩后，再把另一个 PointsBuilder 的点并入 builder。
         */
        @JvmStatic
        fun clearAsMaskAndJoin(
            builder: PointsBuilder,
            points: PointsBuilder,
            maskRange: Double
        ): PointsBuilder {
            if (builder === points) {
                return clearAsMask(builder, maskRange)
            }
            clearAsMask(builder, maskRange)
            builder.points.addAll(points.createWithoutClone())
            return builder
        }
    }

    /**
     * 当前图形对称轴（默认 Y 轴）。
     *
     * 主要用于：
     * - [rotateAsAxis]：绕轴旋转
     * - [rotateTo]：让轴指向目标方向
     */
    var axis = RelativeLocation.yAxis()
        private set

    private val points = ArrayList<RelativeLocation>()

    private fun addGeneratedWithOffset(
        offset: RelativeLocation,
        generatedPoints: Collection<RelativeLocation>
    ): PointsBuilder = addPoints(generatedPoints.onEach { it.add(offset) })

    /**
     * 修改当前对称轴。
     *
     * @param axis 新的对称轴（相对向量）
     */
    fun axis(axis: RelativeLocation): PointsBuilder {
        this.axis = axis
        return this
    }

    /**
     * 添加由 [ImagePointBuilder] 构建出的点集。
     *
     * @param image 图像点生成器（通常把图片像素映射为点）
     */
    fun addImage(image: ImagePointBuilder): PointsBuilder = addPoints(image.build())

    fun addImage(offset: RelativeLocation, image: ImagePointBuilder): PointsBuilder =
        addGeneratedWithOffset(offset, image.build())

    /**
     * 修改builder内所有的点，进行偏移
     *
     * @param noiseX X轴最大偏移幅度（最终偏移范围约为 [-noiseX, +noiseX]）
     * @param noiseY Y轴最大偏移幅度
     * @param noiseZ Z轴最大偏移幅度
     * @param seed 传入则结果可复现；为 null 则每次不同
     * @param mode 噪声分布模式：
     *        AXIS_UNIFORM：xyz 各自均匀随机（立方体噪声）
     *        SPHERE_UNIFORM：在单位球内均匀随机，再按 noiseX/Y/Z 拉伸
     *        SHELL_UNIFORM：在单位球面均匀随机（方向随机），再按 noiseX/Y/Z 拉伸
     * @param offsetLenMin 对最终偏移向量长度做下限（null 表示不限制）
     * @param offsetLenMax 对最终偏移向量长度做上限（null 表示不限制）
     */
    fun applyNoiseOffset(
        noiseX: Double,
        noiseY: Double = noiseX,
        noiseZ: Double = noiseX,
        mode: NoiseMode = NoiseMode.AXIS_UNIFORM,
        seed: Long? = null,
        offsetLenMin: Double? = null,
        offsetLenMax: Double? = null,
    ) = apply {
        Math3DUtil.applyNoiseOffset(points, noiseX, noiseY, noiseZ, seed, mode, offsetLenMin, offsetLenMax)
    }

    /**
     * 修改builder内所有的点，进行偏移
     *
     * @param noise 最大偏移幅度（最终偏移范围约为 [-noise, +noise]）
     * @param seed 传入则结果可复现；为 null 则每次不同
     * @param mode 噪声分布模式：
     *        AXIS_UNIFORM：xyz 各自均匀随机（立方体噪声）
     *        SPHERE_UNIFORM：在单位球内均匀随机，再按 noiseX/Y/Z 拉伸
     *        SHELL_UNIFORM：在单位球面均匀随机（方向随机），再按 noiseX/Y/Z 拉伸
     * @param offsetLenMin 对最终偏移向量长度做下限（null 表示不限制）
     * @param offsetLenMax 对最终偏移向量长度做上限（null 表示不限制）
     */
    fun applyNoiseOffset(
        noise: Vec3,
        mode: NoiseMode = NoiseMode.AXIS_UNIFORM,
        seed: Long? = null,
        offsetLenMin: Double? = null,
        offsetLenMax: Double? = null,
    ) = apply {
        Math3DUtil.applyNoiseOffset(points, noise.x, noise.y, noise.z, seed, mode, offsetLenMin, offsetLenMax)
    }

    /**
     * 让输入的点集合进行随机偏移，会修改原有列表内的点对象
     *
     * @param points 点集合（会被原地修改）
     * @param noiseX X轴最大偏移幅度（最终偏移范围约为 [-noiseX, +noiseX]）
     * @param noiseY Y轴最大偏移幅度
     * @param noiseZ Z轴最大偏移幅度
     * @param seed 传入则结果可复现；为 null 则每次不同
     * @param mode 噪声分布模式：
     *        AXIS_UNIFORM：xyz 各自均匀随机（立方体噪声）
     *        SPHERE_UNIFORM：在单位球内均匀随机，再按 noiseX/Y/Z 拉伸
     *        SHELL_UNIFORM：在单位球面均匀随机（方向随机），再按 noiseX/Y/Z 拉伸
     * @param offsetLenMin 对最终偏移向量长度做下限（null 表示不限制）
     * @param offsetLenMax 对最终偏移向量长度做上限（null 表示不限制）
     */
    fun applyNoiseOffset(
        points: List<RelativeLocation>,
        noiseX: Double,
        noiseY: Double = noiseX,
        noiseZ: Double = noiseX,
        mode: NoiseMode = NoiseMode.AXIS_UNIFORM,
        seed: Long? = null,
        offsetLenMin: Double? = null,
        offsetLenMax: Double? = null,
    ) = addWith {
        applyNoiseOffset(points.map { it.clone() }, noiseX, noiseY, noiseZ, seed, mode, offsetLenMin, offsetLenMax)
        points
    }

    /**
     * 让输入的点集合进行随机偏移，会修改原有列表内的点对象
     *
     * @param points 点集合（会被原地修改）
     * @param noise 最大偏移幅度（最终偏移范围约为 [-noise, +noise]）
     * @param seed 传入则结果可复现；为 null 则每次不同
     * @param mode 噪声分布模式：
     *        AXIS_UNIFORM：xyz 各自均匀随机（立方体噪声）
     *        SPHERE_UNIFORM：在单位球内均匀随机，再按 noiseX/Y/Z 拉伸
     *        SHELL_UNIFORM：在单位球面均匀随机（方向随机），再按 noiseX/Y/Z 拉伸
     * @param offsetLenMin 对最终偏移向量长度做下限（null 表示不限制）
     * @param offsetLenMax 对最终偏移向量长度做上限（null 表示不限制）
     */
    fun applyNoiseOffset(
        points: List<RelativeLocation>,
        noise: Vec3,
        mode: NoiseMode = NoiseMode.AXIS_UNIFORM,
        seed: Long? = null,
        offsetLenMin: Double? = null,
        offsetLenMax: Double? = null,
    ) = addWith {
        applyNoiseOffset(points.map { it.clone() }, noise.x, noise.y, noise.z, seed, mode, offsetLenMin, offsetLenMax)
        points
    }

    /**
     * 添加由 [FourierSeriesBuilder] 构建出的点集。
     *
     * @param builder 傅里叶级数点生成器（生成特定曲线/图形）
     */
    fun addFourierSeries(builder: FourierSeriesBuilder): PointsBuilder = addPoints(builder.build())

    fun addFourierSeries(offset: RelativeLocation, builder: FourierSeriesBuilder): PointsBuilder =
        addGeneratedWithOffset(offset, builder.build())

    /**
     * 对当前 builder 已加入的每个点执行一次操作（原地修改点坐标）。
     *
     * 典型用途：
     * - 批量平移/缩放/随机扰动
     * - 给某些点加高度偏移、扭曲图形等
     *
     * @param handler 对单个点的处理逻辑（直接改 RelativeLocation 的 x/y/z）
     */
    fun pointsOnEach(handler: (RelativeLocation) -> Unit): PointsBuilder {
        points.onEach { handler.invoke(it) }
        return this
    }

    /**
     * 使用预设库 [MathPresets] 生成点并加入。
     *
     * @param handler 在 MathPresets 上取某个预设点集的方法
     * @return this
     */
    fun withPreset(handler: MathPresets.() -> Collection<RelativeLocation>): PointsBuilder =
        addPoints(handler(MathPresets))

    fun withPreset(offset: RelativeLocation, handler: MathPresets.() -> Collection<RelativeLocation>): PointsBuilder =
        addGeneratedWithOffset(offset, handler(MathPresets))

    /**
     * 填充三角形
     *
     * 三点不能共线， 否则计算直线
     * @param p1 点1
     * @param p2 点2
     * @param p3 点3
     * @param sampler 采样精度 越大越密集
     * @return
     */
    fun addFillTriangle(p1: Vec3, p2: Vec3, p3: Vec3, sampler: Number) = addWith { fillTriangle(p1, p2, p3, sampler) }

    /**
     * 填充三角形
     *
     * 三点不能共线， 否则计算直线
     * @param p1 点1
     * @param p2 点2
     * @param p3 点3
     * @param sampler 采样精度 越大越密集
     * @return
     */
    fun addFillTriangle(p1: RelativeLocation, p2: RelativeLocation, p3: RelativeLocation, sampler: Number) =
        addWith { fillTriangle(p1, p2, p3, sampler) }

    /**
     * 添加一批点到 builder。
     *
     * @param enter 要加入的点集合（会原样加入，后续可被 rotate/pointsOnEach 修改）
     */
    fun addPoints(enter: Collection<RelativeLocation>): PointsBuilder {
        points.addAll(enter)
        return this
    }

    /**
     * 使用 [Math3DUtil] 生成点并加入。
     *
     * @param handler 在 Math3DUtil 上调用的生成函数，例如 `getCircleXZ(...)`
     */
    fun addWith(handler: Math3DUtil.() -> Collection<RelativeLocation>): PointsBuilder =
        addPoints(handler(Math3DUtil))

    fun addWith(offset: RelativeLocation, handler: Math3DUtil.() -> Collection<RelativeLocation>): PointsBuilder =
        addGeneratedWithOffset(offset, handler(Math3DUtil))

    /**
     * 添加单个点到 builder。
     *
     * @param point 要加入的点
     */
    fun addPoint(point: RelativeLocation): PointsBuilder {
        points.add(point)
        return this
    }

    /**
     * 根据当前的大小进行百分比缩放
     *
     * @param factor 缩放的百分比 不能小于等于0 否则会破坏所有的点
     */
    fun scale(factor: Number): PointsBuilder {
        val f = factor.toDouble()
        if (f <= 0) {
            return this
        }
        points.forEach {
            it.multiply(f)
        }
        return this
    }

    /**
     * 添加一条三次贝塞尔曲线点集（兼容旧的原点起笔二维曲线，Z 固定为 0）。
     *
     * @param target 终点（相对坐标）
     * @param startHandle 起点控制柄（影响起点切线方向/弯曲程度）
     * @param endHandle 终点控制柄（以 target 为原点的控制柄偏移）
     * @param count 采样点数量（越大越平滑）
     */
    fun addBezierCurve(
        target: RelativeLocation,
        startHandle: RelativeLocation,
        endHandle: RelativeLocation,
        count: Int
    ): PointsBuilder = addWith { generateBezierCurve(target, startHandle, endHandle, count) }

    /**
     * 添加一条三次贝塞尔曲线点集（空间曲线）。
     *
     * @param start 起点
     * @param end 终点
     * @param startHandle 起点控制柄（以 start 为原点的偏移）
     * @param endHandle 终点控制柄（以 end 为原点的偏移）
     * @param count 采样点数量（count > 1 时会包含起点与终点）
     */
    fun addBezierCurve(
        start: RelativeLocation,
        end: RelativeLocation,
        startHandle: RelativeLocation,
        endHandle: RelativeLocation,
        count: Int
    ): PointsBuilder = addWith { generateBezierCurve(start, end, startHandle, endHandle, count) }

    /**
     * 添加由多个控制点组成、按曲线弧长等距采样的空间贝塞尔曲线。
     *
     * @param controlNodes 按曲线顺序排列的节点，每个节点带有入射和出射控制柄
     * @param count 返回点数量，至少为 1
     * @return 当前 PointsBuilder
     */
    fun addBezierCurve(
        controlNodes: Collection<BezierNode>,
        count: Int
    ): PointsBuilder = addWith { generateBezierCurve(controlNodes, count) }

    /**
     * 使用节点 DSL 添加按曲线弧长等距采样的空间贝塞尔曲线。
     *
     * @param count 返回点数量，至少为 1
     * @param handler 贝塞尔曲线节点配置逻辑
     * @return 当前 PointsBuilder
     */
    fun addBezierCurve(
        count: Int,
        handler: BezierCurveBuilder.() -> Unit
    ): PointsBuilder = addBezierCurve(BezierCurveBuilder().apply(handler).build(), count)

    /**
     * 按空间贝塞尔曲线等距放置点集。
     *
     * @param builder 贝塞尔分布构建器；调用时会使用其点集快照
     * @return 当前 PointsBuilder
     *
     * 曲线分布器没有节点时，以原点作为唯一放置位置；分布器没有点集时不会加入任何点。
     */
    fun applyBezierDistribution(builder: BezierDistributionBuilder): PointsBuilder =
        addPoints(builder.build())

    /**
     * 使用 DSL 配置空间贝塞尔曲线分布。
     *
     * @param handler 分布构建器配置逻辑
     * @return 当前 PointsBuilder
     */
    fun applyBezierDistribution(handler: BezierDistributionBuilder.() -> Unit): PointsBuilder =
        applyBezierDistribution(BezierDistributionBuilder().apply(handler))

    /**
     * 合并另一个 builder 的点集（会复制加入）。
     *
     * @param builder 需要合并的 builder
     */
    fun withBuilder(builder: PointsBuilder): PointsBuilder {
        addPoints(builder.create())
        return this
    }

    /**
     * 通过回调创建一个临时 builder，并把它的点加入当前 builder。
     *
     * @param handler 用于往临时 builder 里 add 点
     */
    fun withBuilder(handler: (PointsBuilder) -> Unit): PointsBuilder {
        val builder = PointsBuilder()
        handler(builder)
        addPoints(builder.create())
        return this
    }

    /**
     * 通过回调创建一个指定轴的临时 builder，并把它的点加入当前 builder。
     *
     * @param axis 临时 builder 的对称轴
     * @param handler 用于往临时 builder 里 add 点
     */
    fun withBuilderAxis(axis: RelativeLocation, handler: (PointsBuilder) -> Unit): PointsBuilder {
        val builder = of(axis)
        handler(builder)
        addPoints(builder.create())
        return this
    }

    /**
     * 添加一个离散圆环（XZ 平面），点会在圆环附近随机偏移。
     *
     * @param r 圆环基础半径
     * @param count 点数量
     * @param discrete 最大随机偏移半径（0 表示严格在圆上）
     */
    fun addDiscreteCircleXZ(r: Double, count: Int, discrete: Double): PointsBuilder =
        addWith { getDiscreteCircleXZ(r, count, discrete) }

    fun addDiscreteCircleXZ(offset: RelativeLocation, r: Double, count: Int, discrete: Double): PointsBuilder =
        addWith(offset) { getDiscreteCircleXZ(r, count, discrete) }

    /**
     * 添加一个标准圆（XZ 平面）。
     *
     * @param r 半径
     * @param count 点数量（越大越圆）
     */
    fun addCircle(r: Double, count: Int): PointsBuilder = addPoints(Math3DUtil.getCircleXZ(r, count))

    fun addCircle(offset: RelativeLocation, r: Double, count: Int): PointsBuilder =
        addWith(offset) { getCircleXZ(r, count) }

    /**
     * 添加一个半圆（XZ 平面）。
     *
     * @param r 半径
     * @param count 点数量
     */
    fun addHalfCircle(r: Double, count: Int): PointsBuilder = addWith { getHalfCircleXZ(r, count) }

    fun addHalfCircle(offset: RelativeLocation, r: Double, count: Int): PointsBuilder =
        addWith(offset) { getHalfCircleXZ(r, count) }

    /**
     * 添加一个弧线 从-radian/2 到 radian/2
     * 以X轴为对称轴设置弧度
     *
     * @param r 弧长
     * @param count 采样点个数
     * @param radian 弧度
     */
    fun addRadianCenter(r: Double, count: Int, radian: Double, rotate: Double = 0.0) = addWith {
        getRadianXZCenter(r, count, radian, rotate)
    }

    fun addRadianCenter(
        offset: RelativeLocation,
        r: Double,
        count: Int,
        radian: Double,
        rotate: Double = 0.0
    ) = addWith(offset) {
        getRadianXZCenter(r, count, radian, rotate)
    }

    /**
     * 添加一个弧线 从startRadian 到 endRadian
     *
     * @param r 弧长
     * @param count 采样点个数
     * @param startRadian  弧度
     * @param endRadian  弧度
     */
    fun addRadian(r: Double, count: Int, startRadian: Double, endRadian: Double, rotate: Double = 0.0) = addWith {
        getRadianXZ(r, count, startRadian, endRadian, rotate)
    }

    fun addRadian(
        offset: RelativeLocation,
        r: Double,
        count: Int,
        startRadian: Double,
        endRadian: Double,
        rotate: Double = 0.0
    ) = addWith(offset) {
        getRadianXZ(r, count, startRadian, endRadian, rotate)
    }


    /**
     * 添加一个半圆（XZ 平面），并对其整体旋转。
     *
     * @param r 半径
     * @param count 点数量
     * @param rotate 旋转角（弧度制）
     */
    fun addHalfCircle(r: Double, count: Int, rotate: Double): PointsBuilder =
        addWith { getHalfCircleXZ(r, count, rotate) }

    fun addHalfCircle(offset: RelativeLocation, r: Double, count: Int, rotate: Double): PointsBuilder =
        addWith(offset) { getHalfCircleXZ(r, count, rotate) }

    /** 添加球面点集；[count] 是最终点数。 */
    fun addBallSurface(r: Double, count: Int): PointsBuilder = addWith { getBallSurfaceLocations(r, count) }

    fun addBallSurface(offset: RelativeLocation, r: Double, count: Int): PointsBuilder =
        addWith(offset) { getBallSurfaceLocations(r, count) }

    /** 添加球体内部点集；[count] 是最终点数。 */
    fun addBallSolid(r: Double, count: Int): PointsBuilder = addWith { getBallSolidLocations(r, count) }

    fun addBallSolid(offset: RelativeLocation, r: Double, count: Int): PointsBuilder =
        addWith(offset) { getBallSolidLocations(r, count) }

    fun addBallVolume(r: Double, count: Int): PointsBuilder = addBallSolid(r, count)

    fun addBallVolume(offset: RelativeLocation, r: Double, count: Int): PointsBuilder =
        addBallSolid(offset, r, count)

    /** @deprecated 使用 [addBallSurface]；此方法保留旧的 countPow 分辨率语义。 */
    @Deprecated("Use addBallSurface; this method retains countPow resolution semantics")
    fun addBall(r: Double, countPow: Int): PointsBuilder = addBallSurface(r, countPow * countPow)

    @Deprecated("Use addBallSurface; this method retains countPow resolution semantics")
    fun addBall(offset: RelativeLocation, r: Double, countPow: Int): PointsBuilder =
        addBallSurface(offset, r, countPow * countPow)

    fun addCubeSurface(size: Double, count: Int): PointsBuilder = addCubeSurface(size, size, size, count)

    fun addCubeSurface(offset: RelativeLocation, size: Double, count: Int): PointsBuilder =
        addCubeSurface(offset, size, size, size, count)

    fun addCubeSurface(width: Double, height: Double, depth: Double, count: Int): PointsBuilder =
        addWith { getCubeSurfaceLocations(width, height, depth, count) }

    fun addCubeSurface(
        offset: RelativeLocation,
        width: Double,
        height: Double,
        depth: Double,
        count: Int
    ): PointsBuilder =
        addWith(offset) { getCubeSurfaceLocations(width, height, depth, count) }

    fun addCubeSolid(size: Double, count: Int): PointsBuilder = addCubeSolid(size, size, size, count)

    fun addCubeSolid(offset: RelativeLocation, size: Double, count: Int): PointsBuilder =
        addCubeSolid(offset, size, size, size, count)

    fun addCubeSolid(width: Double, height: Double, depth: Double, count: Int): PointsBuilder =
        addWith { getCubeSolidLocations(width, height, depth, count) }

    fun addCubeSolid(
        offset: RelativeLocation,
        width: Double,
        height: Double,
        depth: Double,
        count: Int
    ): PointsBuilder =
        addWith(offset) { getCubeSolidLocations(width, height, depth, count) }


    fun addCubeVolume(size: Double, count: Int): PointsBuilder = addCubeSolid(size, count)

    fun addCubeVolume(offset: RelativeLocation, size: Double, count: Int): PointsBuilder =
        addCubeSolid(offset, size, count)

    fun addCubeVolume(width: Double, height: Double, depth: Double, count: Int): PointsBuilder =
        addCubeSolid(width, height, depth, count)

    fun addCubeVolume(
        offset: RelativeLocation,
        width: Double,
        height: Double,
        depth: Double,
        count: Int
    ): PointsBuilder =
        addCubeSolid(offset, width, height, depth, count)

    fun addCubeWireframe(size: Double, count: Int): PointsBuilder = addCubeWireframe(size, size, size, count)

    fun addCubeWireframe(offset: RelativeLocation, size: Double, count: Int): PointsBuilder =
        addCubeWireframe(offset, size, size, size, count)

    fun addCubeWireframe(width: Double, height: Double, depth: Double, count: Int): PointsBuilder =
        addWith { getCubeWireframeLocations(width, height, depth, count) }

    fun addCubeWireframe(
        offset: RelativeLocation,
        width: Double,
        height: Double,
        depth: Double,
        count: Int
    ): PointsBuilder =
        addWith(offset) { getCubeWireframeLocations(width, height, depth, count) }

    fun addCubeOutline(size: Double, count: Int): PointsBuilder = addCubeWireframe(size, count)

    fun addCubeOutline(offset: RelativeLocation, size: Double, count: Int): PointsBuilder =
        addCubeWireframe(offset, size, count)

    fun addCubeOutline(width: Double, height: Double, depth: Double, count: Int): PointsBuilder =
        addCubeWireframe(width, height, depth, count)

    fun addCubeOutline(
        offset: RelativeLocation,
        width: Double,
        height: Double,
        depth: Double,
        count: Int
    ): PointsBuilder =
        addCubeWireframe(offset, width, height, depth, count)

    /**
     * 添加摆线/旋轮线图形（Cycloid / Hypotrochoid / Epitrochoid 风格）。
     *
     * @param r1 主圆半径
     * @param r2 副圆半径
     * @param w1 主圆角速度（整数）
     * @param w2 副圆角速度（整数）
     * @param count 采样点数量
     * @param scale 缩放系数（用于整体缩放图形）
     */
    fun addCycloidGraphic(
        r1: Double, r2: Double, w1: Int, w2: Int, count: Int, scale: Double
    ): PointsBuilder = addPoints(Math3DUtil.getCycloidGraphic(r1, r2, w1, w2, count, scale))

    fun addCycloidGraphic(
        offset: RelativeLocation,
        r1: Double,
        r2: Double,
        w1: Int,
        w2: Int,
        count: Int,
        scale: Double
    ): PointsBuilder = addWith(offset) {
        getCycloidGraphic(r1, r2, w1, w2, count, scale)
    }

    /**
     * 将另一个 builder 的点集加上一个平移 [origin] 后加入当前 builder。
     *
     * @param origin 平移偏移（相对坐标）
     * @param builder 被添加的 builder
     */
    fun addBuilder(origin: RelativeLocation, builder: PointsBuilder): PointsBuilder {
        points.addAll(builder.create().onEach { it.add(origin) })
        return this
    }

    /**
     * 添加圆内接正 n 边形的边上点集（每条边采样 edgeCount 个点）。
     *
     * @param n 边数（>=3）
     * @param edgeCount 每条边的采样点数量
     * @param r 外接圆半径
     */
    fun addPolygonInCircle(n: Int, edgeCount: Int, r: Double): PointsBuilder =
        addPoints(Math3DUtil.getPolygonInCircleLocations(n, edgeCount, r))

    fun addPolygonInCircle(offset: RelativeLocation, n: Int, edgeCount: Int, r: Double): PointsBuilder =
        addWith(offset) { getPolygonInCircleLocations(n, edgeCount, r) }

    /**
     * 添加圆内接正 n 边形的顶点点集。
     *
     * @param n 边数（>=3）
     * @param r 外接圆半径
     */
    fun addPolygonInCircleVertices(n: Int, r: Double): PointsBuilder =
        addPoints(Math3DUtil.getPolygonInCircleVertices(n, r))

    fun addPolygonInCircleVertices(offset: RelativeLocation, n: Int, r: Double): PointsBuilder =
        addWith(offset) { getPolygonInCircleVertices(n, r) }

    /**
     * 添加圆面点集（XZ 平面的一圈圈圆环）。
     *
     * @param r 最大半径
     * @param step 相邻圆环半径间距
     * @param preCircleCount 每个圆环的点数量
     */
    fun addRoundShape(r: Double, step: Double, preCircleCount: Int): PointsBuilder =
        addPoints(Math3DUtil.getRoundScapeLocations(r, step, preCircleCount))

    fun addRoundShape(offset: RelativeLocation, r: Double, step: Double, preCircleCount: Int): PointsBuilder =
        addWith(offset) { getRoundScapeLocations(r, step, preCircleCount) }

    /**
     * 添加圆面点集（XZ 平面），并允许不同半径的圆环点数在区间内变化。
     *
     * @param r 最大半径
     * @param step 相邻圆环半径间距
     * @param minCircleCount 最小圆环点数
     * @param maxCircleCount 最大圆环点数
     */
    fun addRoundShape(r: Double, step: Double, minCircleCount: Int, maxCircleCount: Int): PointsBuilder =
        addWith { getRoundScapeLocations(r, step, minCircleCount, maxCircleCount) }

    fun addRoundShape(
        offset: RelativeLocation,
        r: Double,
        step: Double,
        minCircleCount: Int,
        maxCircleCount: Int
    ): PointsBuilder = addWith(offset) {
        getRoundScapeLocations(r, step, minCircleCount, maxCircleCount)
    }

    /**
     * 添加线段点集（start -> end）。
     *
     * @param start 起点
     * @param end 终点
     * @param count 采样点数量（越大越密）
     */
    fun addLine(start: RelativeLocation, end: RelativeLocation, count: Int): PointsBuilder =
        addPoints(Math3DUtil.getLineLocations(start, end, count))

    /**
     * 添加线段点集（start -> end）。
     *
     * @param start 起点（世界坐标 Vec3）
     * @param end 终点（世界坐标 Vec3）
     * @param count 采样点数量
     */
    fun addLine(start: Vec3, end: Vec3, count: Int): PointsBuilder =
        addPoints(Math3DUtil.getLineLocations(start, end, count))

    /**
     * 添加射线点集（从原点沿 direction 方向生成）。
     *
     * @param direction 方向向量（相对坐标）
     * @param step 相邻点距离
     * @param count 点数量
     */
    fun addLine(direction: RelativeLocation, step: Double, count: Int): PointsBuilder =
        addPoints(Math3DUtil.getLineLocations(Vec3.ZERO, direction.toVector(), step, count))

    /**
     * 添加射线点集（从原点沿 direction 方向生成）。
     *
     * @param direction 方向向量（Vec3）
     * @param step 相邻点距离
     * @param count 点数量
     */
    fun addLine(direction: Vec3, step: Double, count: Int): PointsBuilder =
        addPoints(Math3DUtil.getLineLocations(Vec3.ZERO, direction, step, count))

    /**
     * 添加射线点集（从 origin 沿 direction 方向生成）。
     *
     * @param origin 起点
     * @param direction 方向向量
     * @param step 相邻点距离
     * @param count 点数量
     */
    fun addLine(origin: RelativeLocation, direction: RelativeLocation, step: Double, count: Int): PointsBuilder =
        addPoints(Math3DUtil.getLineLocations(origin.toVector(), direction.toVector(), step, count))

    /**
     * 添加带衰减的闪电节点（递归二分生成的折线节点）。
     *
     * @param start 起点
     * @param end 终点
     * @param counts 二分次数（越大节点越多、越“碎”）
     * @param maxOffset 第一次二分的最大随机偏移范围
     * @param attenuation 偏移衰减系数（每次二分偏移范围乘以它，范围通常 (0,1]）
     */
    fun addLightningNodesAttenuation(
        start: RelativeLocation, end: RelativeLocation, counts: Int, maxOffset: Double, attenuation: Double
    ): PointsBuilder = addWith { getLightningNodesEffectAttenuation(start, end, counts, maxOffset, attenuation) }

    /**
     * 添加从原点到 end 的带衰减闪电节点。
     */
    fun addLightningNodesAttenuation(
        end: RelativeLocation, counts: Int, maxOffset: Double, attenuation: Double
    ): PointsBuilder =
        addWith { getLightningNodesEffectAttenuation(RelativeLocation(), end, counts, maxOffset, attenuation) }

    /**
     * 添加虚线（从原点指向 target）。
     *
     * @param target 目标方向/终点（相对坐标）
     * @param totalCount 总采样点数量
     * @param dottedCount 虚线段数量（分成多少段“实线”）
     * @param emptyStep 每段虚线之间的空隙长度
     */
    fun addDottedLine(target: RelativeLocation, totalCount: Int, dottedCount: Int, emptyStep: Double): PointsBuilder =
        addWith { Math3DUtil.generateDottedLine(target, totalCount, dottedCount, emptyStep) }

    fun addDottedLine(
        offset: RelativeLocation,
        target: RelativeLocation,
        totalCount: Int,
        dottedCount: Int,
        emptyStep: Double
    ): PointsBuilder = addWith(offset) {
        generateDottedLine(target, totalCount, dottedCount, emptyStep)
    }

    /**
     * 添加虚线圆环（XZ 平面）。
     *
     * @param r 半径
     * @param totalCount 总采样点数量
     * @param dottedCount 虚线段数量
     * @param emptyStep 每段虚线之间的空隙弧度（弧度制）
     */
    fun addDottedCircle(r: Double, totalCount: Int, dottedCount: Int, emptyStep: Double): PointsBuilder =
        addWith { Math3DUtil.generateDottedCircle(r, totalCount, dottedCount, emptyStep) }

    fun addDottedCircle(
        offset: RelativeLocation,
        r: Double,
        totalCount: Int,
        dottedCount: Int,
        emptyStep: Double
    ): PointsBuilder = addWith(offset) {
        generateDottedCircle(r, totalCount, dottedCount, emptyStep)
    }

    /**
     * 添加带衰减的闪电折线点（节点 + 每段连线采样）。
     *
     * @param start 起点
     * @param end 终点
     * @param counts 二分次数
     * @param maxOffset 第一次二分最大偏移
     * @param attenuation 偏移衰减系数
     * @param preLineCount 每段节点连线的采样点数
     */
    fun addLightningAttenuationPoints(
        start: RelativeLocation,
        end: RelativeLocation,
        counts: Int,
        maxOffset: Double,
        attenuation: Double,
        preLineCount: Int
    ): PointsBuilder = addWith {
        getLightningEffectAttenuationPoints(start, end, counts, maxOffset, attenuation, preLineCount)
    }

    /**
     * 添加从原点到 end 的带衰减闪电折线点。
     */
    fun addLightningAttenuationPoints(
        end: RelativeLocation,
        counts: Int,
        maxOffset: Double,
        attenuation: Double,
        preLineCount: Int
    ): PointsBuilder = addWith {
        getLightningEffectAttenuationPoints(RelativeLocation(), end, counts, maxOffset, attenuation, preLineCount)
    }

    /**
     * 添加闪电节点（无衰减版本）。
     *
     * @param end 终点
     * @param count 二分次数（越大越“碎”）
     */
    fun addLightningNodes(end: RelativeLocation, count: Int): PointsBuilder =
        addWith { getLightningEffectNodes(RelativeLocation(), end, count) }

    /**
     * 添加闪电节点（start -> end）。
     */
    fun addLightningNodes(start: RelativeLocation, end: RelativeLocation, count: Int): PointsBuilder =
        addWith { getLightningEffectNodes(start, end, count) }

    /**
     * 添加闪电节点（并指定随机偏移范围）。
     *
     * @param offsetRange 节点最大随机偏移范围
     */
    fun addLightningNodes(end: RelativeLocation, count: Int, offsetRange: Double): PointsBuilder =
        addWith { getLightningEffectNodes(RelativeLocation(), end, count, offsetRange) }

    /**
     * 添加闪电节点（start -> end，并指定偏移范围）。
     */
    fun addLightningNodes(
        start: RelativeLocation,
        end: RelativeLocation,
        count: Int,
        offsetRange: Double
    ): PointsBuilder = addWith { getLightningEffectNodes(start, end, count, offsetRange) }

    /**
     * 添加闪电折线点（节点 + 连线采样）。
     *
     * @param end 终点
     * @param count 二分次数
     * @param preLineCount 每段连线采样点数
     * @param offsetRange 随机偏移范围
     */
    fun addLightningPoints(end: RelativeLocation, count: Int, preLineCount: Int, offsetRange: Double): PointsBuilder =
        addWith { getLightningEffectPoints(end, count, preLineCount, offsetRange) }

    /**
     * 添加闪电折线点（start -> end），并将结果整体平移到 start。
     */
    fun addLightningPoints(
        start: RelativeLocation,
        end: RelativeLocation,
        count: Int,
        preLineCount: Int,
        offsetRange: Double
    ): PointsBuilder = addWith {
        getLightningEffectPoints(end, count, preLineCount, offsetRange).onEach { it.add(start) }
    }

    /**
     * 添加闪电折线点（默认偏移范围）。
     */
    fun addLightningPoints(end: RelativeLocation, count: Int, preLineCount: Int): PointsBuilder =
        addWith { getLightningEffectPoints(end, count, preLineCount) }

    /**
     * 添加闪电折线点（start -> end），并将结果整体平移到 start。
     */
    fun addLightningPoints(
        start: RelativeLocation,
        end: RelativeLocation,
        count: Int,
        preLineCount: Int
    ): PointsBuilder = addWith {
        getLightningEffectPoints(end, count, preLineCount).onEach { it.add(start) }
    }

    /**
     * 添加从 origin 出发的射线点集（沿 direction 每 step 生成一个点）。
     *
     * @param origin 起点（世界坐标）
     * @param direction 方向向量（世界坐标）
     * @param step 相邻点距离
     * @param count 点数量
     */
    fun addLine(origin: Vec3, direction: Vec3, step: Double, count: Int): PointsBuilder =
        addPoints(Math3DUtil.getLineLocations(origin, direction, step, count))

    /**
     * 添加螺旋上升点集（XZ 旋转 + Y 上升）。
     *
     * @param startRadius 起始半径
     * @param endRadius 结束半径
     * @param height 螺旋高度
     * @param step 高度步长（由此推导点数）
     * @param rotateSpeed 每步旋转角速度（弧度制）
     * @param radiusBias 半径变化曲线系数（1 表示线性，>1 前慢后快）
     * @param heightBias 高度变化曲线系数（1 表示线性，>1 前慢后快）
     */
    fun addSpiral(
        startRadius: Double,
        endRadius: Double,
        height: Double,
        step: Double,
        rotateSpeed: Double,
        radiusBias: Double = 1.0,
        heightBias: Double = 1.0
    ): PointsBuilder = addWith {
        generateSpiralCircleXZ(startRadius, endRadius, height, step, rotateSpeed, radiusBias, heightBias)
    }

    fun addSpiral(
        offset: RelativeLocation,
        startRadius: Double,
        endRadius: Double,
        height: Double,
        step: Double,
        rotateSpeed: Double,
        radiusBias: Double = 1.0,
        heightBias: Double = 1.0
    ): PointsBuilder = addWith(offset) {
        generateSpiralCircleXZ(startRadius, endRadius, height, step, rotateSpeed, radiusBias, heightBias)
    }

    /**
     * 添加螺旋上升点集（显式指定点数）。
     *
     * @param count 点数量
     */
    fun addSpiral(
        startRadius: Double,
        endRadius: Double,
        height: Double,
        count: Int,
        rotateSpeed: Double,
        radiusBias: Double = 1.0,
        heightBias: Double = 1.0
    ): PointsBuilder = addWith {
        generateSpiralCircleXZ(startRadius, endRadius, height, count, rotateSpeed, radiusBias, heightBias)
    }

    fun addSpiral(
        offset: RelativeLocation,
        startRadius: Double,
        endRadius: Double,
        height: Double,
        count: Int,
        rotateSpeed: Double,
        radiusBias: Double = 1.0,
        heightBias: Double = 1.0
    ): PointsBuilder = addWith(offset) {
        generateSpiralCircleXZ(startRadius, endRadius, height, count, rotateSpeed, radiusBias, heightBias)
    }

    /**
     * 将当前点集绕当前 [axis] 旋转。
     *
     * @param radius 旋转角（弧度制）
     */
    fun rotateAsAxis(radius: Double): PointsBuilder {
        Math3DUtil.rotateAsAxis(points, axis, radius)
        return this
    }

    /**
     * 将当前点集绕指定轴旋转。
     *
     * @param radius 旋转角（弧度制）
     * @param axis 旋转轴（相对向量）
     */
    fun rotateAsAxis(radius: Double, axis: RelativeLocation): PointsBuilder {
        Math3DUtil.rotateAsAxis(points, axis, radius)
        return this
    }

    /**
     * 将当前点集的对称轴 [axis] 指向 [to]（图形整体朝向变化）。
     *
     * @param to 目标方向（相对坐标）
     */
    fun rotateTo(to: RelativeLocation): PointsBuilder {
        Math3DUtil.rotatePointsToPoint(points, to, axis)
        return this
    }

    /**
     * 将当前点集的对称轴 [axis] 指向 [to]。
     *
     * @param to 目标方向（世界坐标）
     */
    fun rotateTo(to: Vec3): PointsBuilder {
        Math3DUtil.rotatePointsToPoint(points, RelativeLocation.of(to), axis)
        return this
    }

    /**
     * 以 origin 为参照，让 [axis] 指向 (origin -> end)。
     *
     * @param origin 起点
     * @param end 目标点
     */
    fun rotateTo(origin: RelativeLocation, end: RelativeLocation): PointsBuilder {
        Math3DUtil.rotatePointsToPoint(points, origin.toVector(), end.toVector(), axis)
        return this
    }

    /**
     * 以 origin 为参照，让 [axis] 指向 (origin -> end)。
     *
     * @param origin 起点（世界坐标）
     * @param end 目标点（世界坐标）
     */
    fun rotateTo(origin: Vec3, end: Vec3): PointsBuilder {
        Math3DUtil.rotatePointsToPoint(points, origin, end, axis)
        return this
    }

    /**
     * 清空当前点集。
     */
    fun clear(): PointsBuilder {
        points.clear()
        return this
    }

    /**
     * 执行点遮罩：
     * - 按当前点的输入顺序处理
     * - 新点会清理掉所有与它距离小于 [maskRange] 的旧点
     * - 使用空间分桶优化查询范围
     */
    fun clearAsMask(maskRange: Double): PointsBuilder = apply {
        clearAsMask(this, maskRange)
    }

    /**
     * 执行点遮罩后，再把外部点集合并入当前 builder。
     *
     * @param points 支持 Collection / List / Set 等集合输入
     */
    fun clearAsMaskAndJoin(points: Collection<RelativeLocation>, maskRange: Double): PointsBuilder = apply {
        clearAsMaskAndJoin(this, points, maskRange)
    }

    /**
     * 执行点遮罩后，再把另一个 PointsBuilder 的点并入当前 builder。
     */
    fun clearAsMaskAndJoin(points: PointsBuilder, maskRange: Double): PointsBuilder = apply {
        clearAsMaskAndJoin(this, points, maskRange)
    }

    fun clearAsBallMask(origin: RelativeLocation, radius: Double) = apply {
        points.removeIf { origin.distance(it) <= radius }
    }

    fun clearAsBallMask(origin: Vec3, radius: Double) = clearAsBallMask(origin.asRelative(), radius)
    fun clearAsBallMask(radius: Double) = clearAsBallMask(RelativeLocation(), radius)

    /**
     * 清空 水平面的
     *
     * @param origin
     * @param radius
     */
    fun clearAsRoundXZMask(origin: RelativeLocation, radius: Double, yAxisRange: Double = -1.0) = apply {
        val limitY = yAxisRange > .0
        points.removeIf {
            val hor = it.distanceHorizontal(origin)
            if (!limitY) {
                hor < radius
            } else {
                hor < radius && it.y in origin.y - yAxisRange..origin.y + yAxisRange
            }
        }
    }

    /**
     * 清空 水平面的
     *
     * @param origin
     * @param radius
     */
    fun clearAsRoundXZMask(origin: Vec3, radius: Double, yAxisRange: Double = -1.0) =
        clearAsRoundXZMask(origin.asRelative(), radius, yAxisRange)

    /**
     * 导出点集副本（每个点 clone 一份），避免外部修改影响 builder。
     */
    fun create(): List<RelativeLocation> = points.asSequence().map { it.clone() }.toList()

    /**
     * 导出点集合，但是不clone （节约性能）
     */
    fun createWithoutClone(): List<RelativeLocation> = points

    /**
     * 导出点集并为每个点生成粒子数据（用于 ControlableParticleGroup）。
     *
     * @param dataBuilder 根据相对点生成粒子数据的函数
     * @return key 为粒子数据，value 为对应的相对点
     */
    fun createWithParticleEffects(
        dataBuilder: (relative: RelativeLocation) -> ControlableParticleGroup.ParticleRelativeData
    ): Map<ControlableParticleGroup.ParticleRelativeData, RelativeLocation> =
        mapOf(*createWithoutClone().map { dataBuilder(it) to it }.toTypedArray())

    /**
     * 导出点集并为每个点生成 CompositionData。
     *
     * @param builder 根据相对点生成 CompositionData 的函数
     */
    fun createWithCompositionData(builder: (RelativeLocation) -> CompositionData): Map<CompositionData, RelativeLocation> =
        mapOf(*createWithoutClone().map { builder(it) to it }.toTypedArray())

    /**
     * 导出点集并以 CompositionData 为 key 构建有序映射（TreeMap）。
     *
     * @param builder 根据相对点生成 CompositionData 的函数（需保证 key 可比较/排序）
     */
    fun createWithCompositionDataSorted(builder: (RelativeLocation) -> CompositionData): SortedMap<CompositionData, RelativeLocation> =
        TreeMap<CompositionData, RelativeLocation>().apply {
            putAll(createWithoutClone().associateBy { builder(it) })
        }

    /**
     * 导出点集并生成 SequencedParticleStyle 的排序数据（通常用于按顺序播放/生长）。
     *
     * @param dataBuilder 生成 SortedStyleData 的函数，其中 order 为点的顺序编号（从 0 开始）
     */
    fun createWithSequencedStyleData(
        dataBuilder: (relative: RelativeLocation, order: Int) -> SequencedParticleStyle.SortedStyleData
    ): SortedMap<SequencedParticleStyle.SortedStyleData, RelativeLocation> {
        var order = 0
        return sortedMapOf(*createWithoutClone().map { dataBuilder(it, order++) to it }.toTypedArray())
    }

    /**
     * 导出点集并为每个点生成 SequencedParticleGroup 的粒子数据。
     *
     * @param dataBuilder 根据相对点生成 SequencedParticleRelativeData 的函数
     */
    fun createWithSequencedParticleEffects(
        dataBuilder: (relative: RelativeLocation) -> SequencedParticleGroup.SequencedParticleRelativeData
    ): Map<SequencedParticleGroup.SequencedParticleRelativeData, RelativeLocation> =
        mapOf(*createWithoutClone().map { dataBuilder(it) to it }.toTypedArray())

    /**
     * 导出点集并为每个点生成 ParticleGroupStyle 的样式数据。
     *
     * @param dataBuilder 根据相对点生成 StyleData 的函数
     */
    fun createWithStyleData(
        dataBuilder: (relative: RelativeLocation) -> ParticleGroupStyle.StyleData
    ): Map<ParticleGroupStyle.StyleData, RelativeLocation> =
        mapOf(*createWithoutClone().map { dataBuilder(it) to it }.toTypedArray())

    /**
     * 导出点集并为每个点生成 CompositionData（不 clone 点对象）。
     *
     * @param builder 根据相对点生成 CompositionData 的函数
     */
    fun createWithCompositionDataWithoutClone(
        builder: (RelativeLocation) -> CompositionData
    ): Map<CompositionData, RelativeLocation> =
        mapOf(*createWithoutClone().map { builder(it) to it }.toTypedArray())

    /**
     * 导出点集并以 CompositionData 为 key 构建有序映射（不 clone 点对象）。
     *
     * @param builder 根据相对点生成 CompositionData 的函数（需保证 key 可比较/排序）
     */
    fun createWithCompositionDataSortedWithoutClone(
        builder: (RelativeLocation) -> CompositionData
    ): SortedMap<CompositionData, RelativeLocation> =
        TreeMap<CompositionData, RelativeLocation>().apply {
            putAll(createWithoutClone().associateBy { builder(it) })
        }


    /**
     * 导出点集并生成 SequencedParticleStyle 的排序数据（不 clone 点对象）。
     *
     * @param dataBuilder 生成 SortedStyleData 的函数，其中 order 为点的顺序编号（从 0 开始）
     */
    fun createWithSequencedStyleDataWithoutClone(
        dataBuilder: (relative: RelativeLocation, order: Int) -> SequencedParticleStyle.SortedStyleData
    ): SortedMap<SequencedParticleStyle.SortedStyleData, RelativeLocation> {
        var order = 0
        return sortedMapOf(
            *createWithoutClone().map { dataBuilder(it, order++) to it }.toTypedArray()
        )
    }

    /**
     * 导出点集并为每个点生成粒子数据（用于 ControlableParticleGroup）（不 clone 点对象）。
     *
     * @param dataBuilder 根据相对点生成粒子数据的函数
     * @return key 为粒子数据，value 为对应的相对点
     */
    fun createWithParticleEffectsWithoutClone(
        dataBuilder: (relative: RelativeLocation) -> ControlableParticleGroup.ParticleRelativeData
    ): Map<ControlableParticleGroup.ParticleRelativeData, RelativeLocation> =
        mapOf(*createWithoutClone().map { dataBuilder(it) to it }.toTypedArray())

    /**
     * 导出点集并为每个点生成 SequencedParticleGroup 的粒子数据（不 clone 点对象）。
     *
     * @param dataBuilder 根据相对点生成 SequencedParticleRelativeData 的函数
     */
    fun createWithSequencedParticleEffectsWithoutClone(
        dataBuilder: (relative: RelativeLocation) -> SequencedParticleGroup.SequencedParticleRelativeData
    ): Map<SequencedParticleGroup.SequencedParticleRelativeData, RelativeLocation> =
        mapOf(*createWithoutClone().map { dataBuilder(it) to it }.toTypedArray())

    /**
     * 导出点集并为每个点生成 ParticleGroupStyle 的样式数据（不 clone 点对象）。
     *
     * @param dataBuilder 根据相对点生成 StyleData 的函数
     */
    fun createWithStyleDataWithoutClone(
        dataBuilder: (relative: RelativeLocation) -> ParticleGroupStyle.StyleData
    ): Map<ParticleGroupStyle.StyleData, RelativeLocation> =
        mapOf(*createWithoutClone().map { dataBuilder(it) to it }.toTypedArray())

    /**
     * 将当前点集转换为方块坐标集合（BlockPos）。
     *
     * - 会对每个点执行 ofFloored（向下取整）
     * - 适合：用于方块高亮、碰撞采样、块级别效果定位等
     */
    fun createAsBlockPos(): Set<BlockPos> =
        points.asSequence().map { ofFloored(it.toVector()) }.toMutableSet()

     /**
     * 克隆一个新的 builder（包含当前 axis 与点集副本）。
     */
    fun cloneBuilder(): PointsBuilder = of(axis, create())
}
