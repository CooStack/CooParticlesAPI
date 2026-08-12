package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.randomVec3
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.extend.unaryMinus
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import java.util.ArrayList
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*
import kotlin.random.Random

object Math3DUtil {
    private const val EPS = 1e-7
    private val BOX_EDGES = arrayOf(
        intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7),
        intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(4, 6), intArrayOf(5, 7),
        intArrayOf(0, 1), intArrayOf(2, 3), intArrayOf(4, 5), intArrayOf(6, 7),
    )
    private val AABB_AXES = listOf(
        Vec3(1.0, 0.0, 0.0),
        Vec3(0.0, 1.0, 0.0),
        Vec3(0.0, 0.0, 1.0),
    )
    private val random = Random(System.currentTimeMillis())

    @OptIn(DelicateCoroutinesApi::class)
    private val scope: CoroutineScope by lazy {
        CoroutineScope(
            newFixedThreadPoolContext(
                CooParticlesServices.API_CONFIG_MANAGER.getConfig().calculateThreadCount,
                "Math3DUtil-ThreadPool"
            )
        )
    }


    /**
     * 填充2点之间的点
     *
     * @param sampler 精细度，精细度越大两点之间越密集
     */
    fun fillLine(p1: RelativeLocation, p2: RelativeLocation, sampler: Double): List<RelativeLocation> {
        // 计算出对应的点的个数
        val actualCount = (p1.distance(p2) * sampler).roundToInt()
        return getLineLocations(p1, p2, actualCount)
    }

    /**
     * 填充2点之间的点
     *
     * @param sampler 精细度，精细度越大两点之间越密集
     */
    fun fillLine(p1: Vec3, p2: Vec3, sampler: Double): List<RelativeLocation> {
        // 计算出对应的点的个数
        return fillLine(RelativeLocation.of(p1), RelativeLocation.of(p2), sampler)
    }

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
    fun fillTriangle(
        p1: RelativeLocation,
        p2: RelativeLocation,
        p3: RelativeLocation,
        sampler: Number
    ): List<RelativeLocation> {
        return fillTriangle(p1.toVector(), p2.toVector(), p3.toVector(), sampler)
    }

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
    fun fillTriangle(p1: Vec3, p2: Vec3, p3: Vec3, sampler: Number): List<RelativeLocation> {
        val res = arrayListOf<RelativeLocation>()
        val smp = sampler.toDouble()
        if (!smp.isFinite() || smp <= 0.0) return res
        if (p1.distanceTo(p2) <= 1e-6 || p1.distanceTo(p3) <= 1e-6 || p2.distanceTo(p3) <= 1e-6) return res
        // 判断三角形共面且不共线
        val r1 = p2 - p1
        val r2 = p3 - p2
        // 判断这两条直线是否为同一条
        val sameLine = r1.cross(r2).lengthSqr() < 1e-6
        if (sameLine) {
            // 退化为同一条线时，取最远的两点
            val maxPair = listOf(p1 to p2, p1 to p3, p2 to p3).maxByOrNull { (a, b) -> a.distanceTo(b) } ?: return res
            val start = maxPair.first
            val end = maxPair.second
            return fillLine(start, end, smp)
        }

        // 用重心坐标构建等间隔网格，充满三角面
        val maxEdge = maxOf(
            p1.distanceTo(p2),
            p2.distanceTo(p3),
            p3.distanceTo(p1)
        )
        val edgeSamples = (maxEdge * smp).roundToInt().coerceAtLeast(1)

        for (i in 0..edgeSamples) {
            val u = i.toDouble() / edgeSamples
            for (j in 0..(edgeSamples - i)) {
                val v = j.toDouble() / edgeSamples
                val w = 1.0 - u - v
                res.add(
                    RelativeLocation(
                        p1.x * w + p2.x * u + p3.x * v,
                        p1.y * w + p2.y * u + p3.y * v,
                        p1.z * w + p2.z * u + p3.z * v
                    )
                )
            }
        }

        return res
    }

    /** 将RGB值转换为Minecraft粒子使用的 rgb值(/255) */
    fun colorOf(r: Int, g: Int, b: Int): Vector3f {
        return Vector3f(r.toFloat() / 255, g.toFloat() / 255, b.toFloat() / 255)
    }

    /**
     * 生成一条从原点指向target的相对虚线
     *
     * @param target 相对目标位置
     * @param totalCount 这条直线一共拥有的点的个数
     * @param dottedCount 虚线之间的间隔个数
     * @param step 每条小线段的间隔
     */
    fun generateDottedLine(
        target: RelativeLocation,
        totalCount: Int,
        dottedCount: Int,
        step: Double
    ): List<RelativeLocation> {
        val res = arrayListOf<RelativeLocation>()
        val len = target.length() //总长度
        // 就是普通的直线
        if (len <= step) return emptyList()
        if (step <= 0.0) return getLineLocations(RelativeLocation(), target, totalCount)
        val lineStep = len / dottedCount - step
        if (lineStep <= 0) return emptyList() // 空隙比他妈的直线长
        val perCount = (totalCount / dottedCount).coerceAtLeast(1)
        val dir = target.normalize()
        var current = dir.multiplyClone(lineStep)
        var pre = RelativeLocation()
        repeat(dottedCount) {
            res.addAll(getLineLocations(pre, current, perCount))
            pre = current + dir * step
            current = pre + dir * lineStep
        }
        return res
    }

    /**
     * 生成一条从原点指向target的相对虚线圆环
     *
     * @param r 半径
     * @param totalCount 总点个数
     * @param dottedCount 虚线之间的间隔个数
     * @param step 每条小线段的间隔
     */
    fun generateDottedCircle(r: Double, totalCount: Int, dottedCount: Int, step: Double): List<RelativeLocation> {
        val res = arrayListOf<RelativeLocation>()
        if (step >= 2 * PI) {
            return emptyList()
        }
        val perArcCount = (totalCount / dottedCount).coerceAtLeast(1)
        val solidArcLengthStep = 2 * PI / dottedCount - step // 计算实线部分的弧长
        val angleStep = solidArcLengthStep / perArcCount // 圆环实线部分的 点的个数
        var pre = 0.0
        var current = solidArcLengthStep
        repeat(dottedCount) {
            // 这里要生成弧线
            repeat(perArcCount) {
                val arcAngle = pre + it * angleStep
                res.add(
                    RelativeLocation(
                        cos(arcAngle) * r,
                        0.0,
                        sin(arcAngle) * r
                    )
                )
            }
            pre = current + step
            current = pre + solidArcLengthStep
        }

        return res
    }

    // 傅里叶级数
    /** 闪电 */
    fun getLightningEffectNodes(
        start: RelativeLocation, end: RelativeLocation, counts: Int
    ): List<RelativeLocation> {
        val len = end.distance(start)
        val offsetStep = len / 4
        return getLightningEffectNodes(start, end, counts, offsetStep)
    }

    fun getLightningEffectNodes(
        start: RelativeLocation, end: RelativeLocation, counts: Int, offsetRange: Double
    ): List<RelativeLocation> {
        val res = mutableListOf(start)
        res.addAll(getLightningNodes(start, end, counts, offsetRange))
        res.add(end)
        return res
    }

    /**
     * @param maxOffsetRange 第一次二分时的随机范围
     * @param attenuation 随着二分的进行, 二分的随机范围衰减 这一次是上一次的 attenuation倍
     */
    fun getLightningNodesEffectAttenuation(
        start: RelativeLocation,
        end: RelativeLocation,
        counts: Int,
        maxOffsetRange: Double,
        attenuation: Double
    ): List<RelativeLocation> {
        val res = mutableListOf(start)
        res.addAll(getLightningNodesAttenuation(start, end, counts, maxOffsetRange, attenuation))
        res.add(end)
        return res
    }

    /**
     * @param maxOffsetRange 第一次二分时的随机范围
     * @param attenuation 随着二分的进行, 二分的随机范围衰减 这一次是上一次的 attenuation倍
     */
    fun getLightningEffectAttenuationPoints(
        start: RelativeLocation,
        end: RelativeLocation,
        counts: Int,
        maxOffsetRange: Double,
        attenuation: Double,
        preLineCount: Int
    ): List<RelativeLocation> {
        return connectLineWithNodes(
            getLightningNodesEffectAttenuation(start, end, counts, maxOffsetRange, attenuation),
            preLineCount
        )
    }


    private fun getLightningNodesAttenuation(
        start: RelativeLocation, end: RelativeLocation, counts: Int, currentOffsetRange: Double, attenuation: Double
    ): List<RelativeLocation> {
        /** FIXED 当某些衰减过小时 会出现0.0的异常 */
        val fixedOffsetRange = currentOffsetRange.coerceAtLeast(0.01)
        require(attenuation in 0.01..1.0)
        // 二分 start - > end 位置
        // 先获取中点
        val mid = start + (end - start).multiply(0.5)
        // 让中点进行偏移
        mid.add(randomVec3().asRelative() * random.nextDouble(-fixedOffsetRange, fixedOffsetRange))
        val res = mutableListOf(mid)
        if (counts <= 1) {
            return res
        }
        val nextOffsetRange = (fixedOffsetRange * attenuation).coerceAtLeast(0.01)
        val left = getLightningNodesAttenuation(start, mid, counts - 1, nextOffsetRange, attenuation)
        val right = getLightningNodesAttenuation(mid, end, counts - 1, nextOffsetRange, attenuation)
        // 合并点集合
        return left + res + right
    }

    private fun getLightningNodes(
        start: RelativeLocation, end: RelativeLocation, counts: Int, offsetRange: Double
    ): List<RelativeLocation> {
        return getLightningNodesAttenuation(start, end, counts, offsetRange, 1.0)
    }

    /**
     * @param end 闪电效果的终点
     * @param counts 二分次数
     */
    fun getLightningEffectPoints(end: RelativeLocation, counts: Int, preLineCount: Int): List<RelativeLocation> {
        val nodes = getLightningEffectNodes(RelativeLocation(), end, counts)
        val res = ArrayList<RelativeLocation>()
        var i = 0
        while (i < nodes.size - 1) {
            val current = nodes[i]
            val next = nodes[i + 1]
            // 连线
            res.addAll(getLineLocations(current, next, preLineCount))
            i++
        }
        return res
    }

    fun getLightningEffectPoints(
        end: RelativeLocation,
        counts: Int,
        preLineCount: Int,
        offsetRange: Double
    ): List<RelativeLocation> {
        val nodes = getLightningEffectNodes(RelativeLocation(), end, counts, offsetRange)
        return connectLineWithNodes(nodes, preLineCount)
    }

    /**
     * 输入节点， 让节点之间按照节点顺序连线
     *
     * @param nodes 输入的节点坐标
     * @param preLineCount 每个线段的采样点个数
     * @return
     */
    fun connectLineWithNodes(nodes: List<RelativeLocation>, preLineCount: Int): List<RelativeLocation> {
        val res = ArrayList<RelativeLocation>()
        var i = 0
        while (i < nodes.size - 1) {
            val current = nodes[i]
            val next = nodes[i + 1]
            // 连线
            res.addAll(getLineLocations(current, next, preLineCount))
            i++
        }
        return res
    }

    /**
     * 在 XZ 平面上生成离散化的三维环形分布点集
     *
     * @param r 目标圆环的基础半径（单位：方块），建议非负值
     * @param discrete 最大分散距离（单位：方块），控制点与标准圆环的偏离程度：
     *    - = 0 时所有点严格位于圆环上
     *    - > 0 时点会在三维空间中以该值为最大半径随机偏移 实际偏移量为 [0, discrete] 的随机值，负值会被自动归零
     *
     * @param pointRadius 在discrete属性设置为0时 点所在的圆环的位置角度参数 输入弧度制
     */
    fun getSingleDiscreteOnCircleXZ(r: Double, discrete: Double, pointRadius: Double): RelativeLocation {
        val x = cos(pointRadius) * r
        val z = sin(pointRadius) * r
        if (discrete <= 0) return RelativeLocation(x, 0.0, z)

        val randomR = random.nextDouble(discrete)
        val rx = random.nextDouble(-PI, PI)
        val ry = random.nextDouble(-PI, PI)
        val add = RelativeLocation(
            randomR * cos(rx) * cos(ry),
            randomR * sin(rx),
            randomR * sin(ry) * cos(rx)
        )
        // 合成最终坐标
        return RelativeLocation(
            x = x + add.x,
            y = add.y,  // 原 y 坐标为 0，直接使用偏移量
            z = z + add.z
        )
    }

    /**
     * 在 XZ 平面上生成离散化的三维环形分布点集
     *
     * @param r 目标圆环的基础半径（单位：方块），建议非负值
     * @param count 需要生成的离散点数量，必须为正整数
     * @param discrete 最大分散距离（单位：方块），控制点与标准圆环的偏离程度：
     *    - = 0 时所有点严格位于圆环上
     *    - > 0 时点会在三维空间中以该值为最大半径随机偏移 实际偏移量为 [0, discrete] 的随机值，负值会被自动归零
     */
    fun getDiscreteCircleXZ(r: Double, count: Int, discrete: Double): List<RelativeLocation> {
        val result = mutableListOf<RelativeLocation>()
        if (count <= 0) return result
        val angleStep = 2 * PI / count  // 等分圆周角度
        repeat(count) { i ->
            val baseAngle = i * angleStep
            result.add(getSingleDiscreteOnCircleXZ(r, discrete, baseAngle))
        }
        return result
    }

    /**
     * @param count 点的个数
     * @return 在xz平面上的圆的点
     */
    fun getCircleXZ(r: Double, count: Int): List<RelativeLocation> {
        val res = ArrayList<RelativeLocation>()
        val step = 2 * PI / count
        var radius = 0.0
        repeat(count) {
            res.add(
                RelativeLocation(
                    r * cos(radius), 0.0, r * sin(radius),
                )
            )
            radius += step
        }
        return res
    }

    /**
     * @param count 点的个数
     * @return 在xz平面上的半圆的点
     */
    fun getHalfCircleXZ(r: Double, count: Int, rotate: Double = 0.0): List<RelativeLocation> {
        return getRadianXZ(r, count, 0.0, PI, rotate)
    }

    /**
     * 获取弧线，从 -radian/2 .. radian/2
     * 以X轴为中心 向左右扩散 radian / 2弧度
     *
     * @param r 弧长半径
     * @param count 弧度采样点个数
     * @param radian 弧度
     * @param rotate 初始旋转
     * @return
     */
    fun getRadianXZCenter(r: Double, count: Int, radian: Double, rotate: Double = 0.0): List<RelativeLocation> {
        return getRadianXZ(r, count, -radian / 2, radian / 2, rotate)
    }

    /**
     * 获取弧线， 从 startRadian .. endRadian
     *
     * @param r 弧长半径
     * @param count 采样点个数
     * @param startRadian 起始弧度 （< endRadian)
     * @param endRadian 结束弧度
     * @param rotate 初始旋转
     * @return
     */
    fun getRadianXZ(
        r: Double,
        count: Int,
        startRadian: Double,
        endRadian: Double,
        rotate: Double = 0.0
    ): List<RelativeLocation> {
        val res = ArrayList<RelativeLocation>()
        val step = (endRadian - startRadian) / count
        var rad = startRadian
        repeat(count) {
            res.add(
                RelativeLocation(
                    r * cos(rad), 0.0, r * sin(rad),
                )
            )
            rad += step
        }
        if (rotate != 0.0) {
            rotateAsAxis(res, RelativeLocation.yAxis(), rotate)
        }
        return res
    }

    /**
     * 生成以 r为半径的圆的 内接正n边形
     *
     * @param n 多边形的边数 必须大于等于3
     * @param edgeCount 每一条边的点的个数
     * @param r 半径
     */
    fun getPolygonInCircleLocations(n: Int, edgeCount: Int, r: Double): List<RelativeLocation> {
        require(n >= 3) { "n must be at least 3" }
        require(edgeCount >= 1) { "edgeCount must be at least 1" }

        // 生成正n边形的顶点列表（xz平面，圆心在原点）
        val vertices = getPolygonInCircleVertices(n, r)

        val result = mutableListOf<RelativeLocation>()

        for (i in 0 until n) {
            val j = (i + 1) % n
            val vi = vertices[i]
            val vj = vertices[j]

            // 计算边的方向向量
            val direction = Vec3(vj.x - vi.x, vj.y - vi.y, vj.z - vi.z)
            val length = direction.length()

            // 计算步长（若edgeCount为1，则步长为0，仅包含起点）
            val step = if (edgeCount > 1) length / (edgeCount - 1) else 0.0

            // 生成当前边的点集
            val lineLocations = getLineLocations(vi.toVector(), direction, step, edgeCount)
            result.addAll(lineLocations)
        }

        return result
    }

    /**
     * 生成以 r为半径的圆的 内接正n边形的每个顶点
     *
     * @param n 多边形的边数 必须大于等于3
     * @param r 半径
     */
    fun getPolygonInCircleVertices(n: Int, r: Double): List<RelativeLocation> {
        require(n >= 3) { "n must be at least 3" }
        // 生成正n边形的顶点列表（xz平面，圆心在原点）
        val vertices = List(n) { i ->
            val theta = 2 * PI * i / n
            RelativeLocation(r * cos(theta), 0.0, r * sin(theta))
        }
        return vertices
    }

    /**
     * 让两个点集合 连线规则如下 前提: points.size > to.size 建议输入的点集合的个数 points.size
     * % to.size == 0 如果不为0 则会有points.size % to.size 个点不会被链接
     * 如果输入的点集合大小相反则链接规则也会相反 令 step = points.size / to.size (整除) points
     * 的第i个点到第i+step -1个点会链接 to的第i个点
     *
     * 如果你使用了两个圆(Math3DUtil.getCircleXZ())上平均分布的点来调用函数 会发现两个圆的第一个点其实角度相同
     * 所以你需要使用 Math3DUtil.rotateAsAxis() 对小的圆进行旋转 旋转角度为 -PI / points.size
     * 这样得到的线是均匀分布的
     *
     * @param preLineCount 每个链接的直线的粒子个数
     * @return 返回一个二维列表, 代表直线点集合的集合
     */
    fun connectLines(
        points: List<RelativeLocation>,
        to: List<RelativeLocation>,
        preLineCount: Int
    ): MutableList<List<RelativeLocation>> {
        if (points.isEmpty() || to.isEmpty()) {
            return mutableListOf()
        }
        // 确定较大的列表和较小的列表
        val (bigger, smaller) = if (points.size >= to.size) points to to else to to points
        val step = bigger.size / smaller.size
        val remainder = bigger.size % smaller.size
        val result = mutableListOf<List<RelativeLocation>>()
        smaller.forEachIndexed { index, smallPoint ->
            val currentStep = if (index < remainder) step + 1 else step
            val startIndex = index * step + minOf(index, remainder)
            for (offset in 0 until currentStep) {
                val biggerIndex = startIndex + offset
                if (biggerIndex >= bigger.size) break
                val line = getLineLocations(
                    bigger[biggerIndex],
                    smallPoint,
                    preLineCount
                )
                result.add(line)
            }
        }

        return result
    }

    /**
     * DeepSeek解放大脑
     *
     * @param count 填写你使用 getCycloidGraphic方法时 输入的count
     * @see getCycloidGraphic 获取此函数生成的图像的顶点 参数要求必须和 getCycloidGraphic 生成的参数完全一致
     */
    fun computeCycloidVertices(
        r1: Double,
        r2: Double,
        w1: Int,
        w2: Int,
        count: Int,
        scale: Double
    ): MutableList<RelativeLocation> {
        val doubled = max(abs(w1), abs(w2))
        val precision = 360 * doubled / count
        val w1Step = w1 * precision
        val w2Step = w2 * precision

        val d = gcd(abs(w1), abs(w2))
        // 感谢MZ的数学更正
        val verticesCount = abs(w1 - w2) / d
        val vertices = mutableListOf<RelativeLocation>()

        for (k in 0..<verticesCount) {
            val delta = w1Step - w2Step
            val t = (2 * Math.PI * k) / delta
            val x = r1 * cos(w1Step * t) + r2 * cos(w2Step * t) * scale
            val z = r1 * sin(w1Step * t) + r2 * sin(w2Step * t) * scale
            vertices.add(
                RelativeLocation(x, 0.0, z)
            )
        }

        return vertices
    }


    /** 求最大公约数 */
    fun gcd(i: Int, j: Int): Int {
        var x = i.absoluteValue
        var y = j.absoluteValue
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }

    /**
     * 傅里叶级数 生成以r1为半径的圆上的动点A为圆心 r2为半径 上的动点P的轨迹 点A的移动速度为w1 点P的移动速度为w2
     *
     * @param r1 中心圆的半径
     * @param r2 中心圆上的圆的半径
     * @param w1 中心圆的角速度
     * @param w2 中心圆上的圆的角速度 r1:r2 与 w1:w2 和 生成的图形有紧密的关系 例如 r1:r2 = 3:2 w1:w2 =
     *    2:-3 时 图像是一个五角星
     * @param scale 半径精度 如果r1认为太大 则设置小的值
     * @return 最后的图像 (在XZ平面上(以Z为纵坐标))
     */
    fun getCycloidGraphic(
        r1: Double,
        r2: Double,
        w1: Int,
        w2: Int,
        count: Int,
        scale: Double
    ): MutableList<RelativeLocation> {
        // 原点上的圆的当前角度
        val result = ArrayList<RelativeLocation>()
        var radOrigin = 0.0
        var radA = 0.0
        val doubled = max(abs(w1), abs(w2))
        var current = 0
        // 修复当count过大时, 点计算错误
        val precision = 2 * PI * doubled / count
        while (current < count) {
            radOrigin += w1 * precision
            radA += w2 * precision
            result.add(
                RelativeLocation(
                    (r2 * cos(radA) + r1 * cos(radOrigin)) * scale,
                    0.0,
                    (r2 * sin(radA) + r1 * sin(radOrigin)) * scale
                )
            )
            current++
        }
        return result
    }

    /** 生成球面上的 [count] 个均匀分布点。 */
    fun getBallSurfaceLocations(r: Double, count: Int): MutableList<RelativeLocation> {
        require(count >= 1) { "count must be at least 1" }
        val result = ArrayList<RelativeLocation>(count)
        val goldenAngle = PI * (3.0 - sqrt(5.0))
        for (index in 0 until count) {
            val y = 1.0 - 2.0 * (index + 0.5) / count
            val ringRadius = sqrt((1.0 - y * y).coerceAtLeast(0.0))
            val angle = goldenAngle * index
            result += RelativeLocation(
                r * ringRadius * cos(angle),
                r * y,
                r * ringRadius * sin(angle),
            )
        }
        return result
    }

    /** 生成球体内部的 [count] 个均匀分布点。 */
    fun getBallSolidLocations(r: Double, count: Int): MutableList<RelativeLocation> {
        require(count >= 1) { "count must be at least 1" }
        val result = getBallSurfaceLocations(1.0, count)
        for (index in result.indices) {
            val radius = r * ((index + 0.5) / count).pow(1.0 / 3.0)
            result[index].multiply(radius)
        }
        return result
    }

    @Deprecated("Use getBallSurfaceLocations; this method retains countPow resolution semantics")
    fun getBallLocations(r: Double, countPow: Int): MutableList<RelativeLocation> =
        getBallSurfaceLocations(r, countPow * countPow)

    /** 生成长宽高为 [width]、[height]、[depth] 的方块表面点。 */
    fun getCubeSurfaceLocations(width: Double, height: Double, depth: Double, count: Int): MutableList<RelativeLocation> {
        require(count >= 1) { "count must be at least 1" }
        require(width >= 0.0 && height >= 0.0 && depth >= 0.0) { "cube dimensions must be non-negative" }
        val halfX = width / 2.0
        val halfY = height / 2.0
        val halfZ = depth / 2.0
        val areas = doubleArrayOf(width * depth, width * depth, width * height, width * height, height * depth, height * depth)
        val totalArea = areas.sum()
        val result = ArrayList<RelativeLocation>(count)
        for (index in 0 until count) {
            val selectedFace = if (totalArea == 0.0) 0 else {
                val target = totalArea * (index + 0.5) / count
                var accumulated = 0.0
                var face = 0
                while (face < areas.lastIndex && target > accumulated + areas[face]) {
                    accumulated += areas[face++]
                }
                face
            }
            val u = ((index * 0.6180339887498949) % 1.0)
            val v = ((index * 0.7548776662466927) % 1.0)
            val xU = -halfX + width * u
            val xV = -halfX + width * v
            val yU = -halfY + height * u
            val yV = -halfY + height * v
            val zU = -halfZ + depth * u
            val zV = -halfZ + depth * v
            result += when (selectedFace) {
                0 -> RelativeLocation(xU, -halfY, zV)
                1 -> RelativeLocation(xU, halfY, zV)
                2 -> RelativeLocation(xU, yV, -halfZ)
                3 -> RelativeLocation(xU, yV, halfZ)
                4 -> RelativeLocation(-halfX, yU, zV)
                else -> RelativeLocation(halfX, yU, zV)
            }
        }
        return result
    }

    /** 生成长宽高为 [width]、[height]、[depth] 的方块体积点。 */
    fun getCubeSolidLocations(width: Double, height: Double, depth: Double, count: Int): MutableList<RelativeLocation> {
        require(count >= 1) { "count must be at least 1" }
        require(width >= 0.0 && height >= 0.0 && depth >= 0.0) { "cube dimensions must be non-negative" }
        val result = ArrayList<RelativeLocation>(count)
        for (index in 0 until count) {
            val x = ((index * 0.7548776662466927) % 1.0 - 0.5) * width
            val y = ((index * 0.5698402909980532) % 1.0 - 0.5) * height
            val z = ((index * 0.4385790219242876) % 1.0 - 0.5) * depth
            result += RelativeLocation(x, y, z)
        }
        return result
    }

    /** 生成方块 12 条边上的轮廓点，总点数为 [count]。 */
    fun getCubeWireframeLocations(width: Double, height: Double, depth: Double, count: Int): MutableList<RelativeLocation> {
        require(count >= 1) { "count must be at least 1" }
        require(width >= 0.0 && height >= 0.0 && depth >= 0.0) { "cube dimensions must be non-negative" }
        val halfX = width / 2.0
        val halfY = height / 2.0
        val halfZ = depth / 2.0
        val vertices = arrayOf(
            RelativeLocation(-halfX, -halfY, -halfZ), RelativeLocation(halfX, -halfY, -halfZ),
            RelativeLocation(halfX, halfY, -halfZ), RelativeLocation(-halfX, halfY, -halfZ),
            RelativeLocation(-halfX, -halfY, halfZ), RelativeLocation(halfX, -halfY, halfZ),
            RelativeLocation(halfX, halfY, halfZ), RelativeLocation(-halfX, halfY, halfZ),
        )
        val edges = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0),
            intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 4),
            intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7),
        )
        val lengths = edges.map { edge -> vertices[edge[0]].distance(vertices[edge[1]]) }
        val totalLength = lengths.sum()
        val result = ArrayList<RelativeLocation>(count)
        for (index in 0 until count) {
            val target = if (totalLength == 0.0) 0.0 else totalLength * (index + 0.5) / count
            var edgeIndex = 0
            var accumulated = 0.0
            while (edgeIndex < lengths.lastIndex && target > accumulated + lengths[edgeIndex]) {
                accumulated += lengths[edgeIndex++]
            }
            val edge = edges[edgeIndex]
            val t = if (lengths[edgeIndex] == 0.0) 0.0 else (target - accumulated) / lengths[edgeIndex]
            result += RelativeLocation(
                vertices[edge[0]].x + (vertices[edge[1]].x - vertices[edge[0]].x) * t,
                vertices[edge[0]].y + (vertices[edge[1]].y - vertices[edge[0]].y) * t,
                vertices[edge[0]].z + (vertices[edge[1]].z - vertices[edge[0]].z) * t,
            )
        }
        return result
    }


    /**
     * from new bing 将一个相对位置按照axis旋转 n度
     *
     * @param angle 角度 输入时使用弧度制的角度
     */
    fun rotateVector(point: RelativeLocation, axis: RelativeLocation, angle: Double): RelativeLocation {
        return RotationMatrix.fromAxisAngle(axis, angle).applyToClone(point)
    }


    /**
     * 向量图形绕轴旋转N度
     *
     * @param angle 角度 输入一个弧度制角度
     */
    fun rotateAsAxis(locList: List<RelativeLocation>, axis: RelativeLocation, angle: Double): List<RelativeLocation> {
        return rotateAsAxisAsync(
            locList,
            axis,
            angle,
            CooParticlesServices.API_CONFIG_MANAGER.getConfig().calculateThreadCount
        )
    }

    /**
     * 向量图形绕轴旋转N度
     *
     * @param angle 角度 输入一个弧度制角度
     */
    fun rotateAsAxisAsync(
        shape: List<RelativeLocation>,
        axis: RelativeLocation,
        angle: Double,
        threads: Int
    ): List<RelativeLocation> {
        val copy = CopyOnWriteArrayList(shape)
        if (copy.isEmpty()) return shape
        var actualThreads = threads
        if (threads >= copy.size) {
            actualThreads = copy.size
        }
        // 计算每一个线程处理的点的平均个数
        val taskPreThreadCount = copy.size / actualThreads
        var notHandledTaskCount = copy.size % actualThreads
        // 划分索引范围 从0开始
        // 索引计算规则如下 从0开始 到 taskPreThreadCount + n 结束 左闭右开
        // 下一个thread就是 taskPreThreadCount + n 开始 n一般为1或者0
        var currentIndex = 0
        val q = Quaterniond()
        q.rotateAxis(angle, axis.toVector3d())
        val tasks = ArrayList<Deferred<Unit>>()
        repeat(actualThreads) {
            var next = currentIndex + taskPreThreadCount // 取到  taskHandledIndexStart ..< next
            if (notHandledTaskCount > 0) {
                next++
                notHandledTaskCount--
            }
            val taskHandledIndexStart = currentIndex
            currentIndex = next
            // 创建任务
            val vector = Vector3d(0.0, 0.0, 0.0)
            val job = scope.async {
                for (i in taskHandledIndexStart..<next) {
                    val it = copy[i]
                    // 复用节约内存
                    vector.set(it.x, it.y, it.z)
                    vector.rotate(q)
                    it.x = vector.x
                    it.y = vector.y
                    it.z = vector.z
                }
            }
            tasks.add(job)
        }
        runBlocking { tasks.awaitAll() }
        return shape
    }

    /**
     * # 绕轴旋转的同时带有roll角度 减少重复遍历
     * - 向量图形绕轴旋转N度
     * - 向量旋转到目标轴
     *
     * - [rotatePointsToPoint]
     * - [rotateAsAxis]
     * @param angle 角度 输入一个弧度制角度
     * @param to 旋转到目标轴
     */
    fun rotateToWithRoll(
        shape: List<RelativeLocation>,
        axis: RelativeLocation,
        to: RelativeLocation,
        angle: Double
    ) = rotateToWithRollAsync(
        shape,
        axis,
        to,
        angle,
        CooParticlesServices.API_CONFIG_MANAGER.getConfig().calculateThreadCount
    )

    /**
     * - 向量图形绕轴旋转N度
     * - 向量旋转到目标轴
     *
     * - [rotatePointsToPoint]
     * - [rotateAsAxis]
     * @param angle 角度 输入一个弧度制角度
     * @param to 旋转到目标轴
     */
    fun rotateToWithRollAsync(
        shape: List<RelativeLocation>,
        axis: RelativeLocation,
        to: RelativeLocation,
        angle: Double,
        threads: Int
    ): List<RelativeLocation> {
        // Keep behavior consistent with rotateAsAxis + rotatePointsToPoint.
        // If axis and target are collinear in the same direction, the second step is a no-op.
        if (axis.cross(to).length() in -1e-5..1e-5 && axis.dot(to) > 0) {
            return rotateAsAxisAsync(shape, axis, angle, threads)
        }
        val copy = CopyOnWriteArrayList(shape)
        if (copy.isEmpty()) return shape
        var actualThreads = threads
        if (threads >= copy.size) {
            actualThreads = copy.size
        }
        // 计算每一个线程处理的点的平均个数
        val taskPreThreadCount = copy.size / actualThreads
        var notHandledTaskCount = copy.size % actualThreads
        // 划分索引范围 从0开始
        // 索引计算规则如下 从0开始 到 taskPreThreadCount + n 结束 左闭右开
        // 下一个thread就是 taskPreThreadCount + n 开始 n一般为1或者0
        var currentIndex = 0
        val rollAxis = Quaterniond()
        rollAxis.rotateAxis(angle, axis.toVector3d())
        // 计算旋转四元数
        val rotateQ = Quaterniond()
        // 差值
        val na = axis.normalize()
        val axisYaw = getYawFromLocation(na)
        val axisPitch = getPitchFromLocation(na)

        val toa = to.normalize()
        val toYaw = getYawFromLocation(toa)
        val toPitch = getPitchFromLocation(toa)
        // 先让图形面向Z轴
        rotateQ.rotateY(axisYaw).rotateLocalX(axisPitch)
        // 后再转回目标点
        val rotateTargetQ = Quaterniond()
            .rotateY(-toYaw)
            .rotateX(-toPitch)
        val tasks = ArrayList<Deferred<Unit>>()
        repeat(actualThreads) {
            var next = currentIndex + taskPreThreadCount // 取到  taskHandledIndexStart ..< next
            if (notHandledTaskCount > 0) {
                next++
                notHandledTaskCount--
            }
            val taskHandledIndexStart = currentIndex
            currentIndex = next
            // 创建任务
            val vector = Vector3d(0.0, 0.0, 0.0)
            val job = scope.async {
                for (i in taskHandledIndexStart..<next) {
                    val it = copy[i]
                    // 复用节约内存
                    vector.set(it.x, it.y, it.z)
                    vector.rotate(rollAxis)
                        .rotate(rotateQ)
                        .rotate(rotateTargetQ)
                    it.x = vector.x
                    it.y = vector.y
                    it.z = vector.z
                }
            }
            tasks.add(job)
        }
        runBlocking { tasks.awaitAll() }
        return shape
    }

    /** 让图形的对称轴指向某个点(图形跟着转变) */
    fun rotatePointsToPoint(
        shape: List<RelativeLocation>,
        toPoint: RelativeLocation,
        axis: RelativeLocation
    ): List<RelativeLocation> {
        return rotatePointsToPointAsync(
            shape,
            toPoint,
            axis,
            CooParticlesServices.API_CONFIG_MANAGER.getConfig().calculateThreadCount
        )
    }


    /**
     * 让图形的对称轴指向某个点(图形跟着转变)
     *
     * 使用多线程并发修改shape的值 (FutureTask)
     */
    fun rotatePointsToPointAsync(
        shape: List<RelativeLocation>,
        toPoint: RelativeLocation,
        axis: RelativeLocation,
        threads: Int
    ): List<RelativeLocation> {
        val copy = CopyOnWriteArrayList(shape)
        // 同向共线
        if (axis.cross(toPoint).length() in -1e-5..1e-5 && axis.dot(toPoint) > 0) {
            return shape
        }
        if (copy.isEmpty()) return shape
        var actualThreads = threads
        if (threads >= copy.size) {
            actualThreads = copy.size
        }
        // 计算每一个线程处理的点的平均个数
        val taskPreThreadCount = copy.size / actualThreads
        var notHandledTaskCount = copy.size % actualThreads
        // 划分索引范围 从0开始
        // 索引计算规则如下 从0开始 到 taskPreThreadCount + n 结束 左闭右开
        // 下一个thread就是 taskPreThreadCount + n 开始 n一般为1或者0
        var currentIndex = 0

        // 计算旋转四元数
        val q = Quaterniond()
        // 差值
        val na = axis.normalize()
        val axisYaw = getYawFromLocation(na)
        val axisPitch = getPitchFromLocation(na)

        val toa = toPoint.normalize()
        val toYaw = getYawFromLocation(toa)
        val toPitch = getPitchFromLocation(toa)
        // 先让图形面向Z轴
        q.rotateY(axisYaw).rotateLocalX(axisPitch)
        // 后再转回目标点
        val toQ = Quaterniond()
            .rotateY(-toYaw)
            .rotateX(-toPitch)
        // 开始分配旋转任务
        val tasks = ArrayList<Deferred<Unit>>()
        repeat(actualThreads) {
            var next = currentIndex + taskPreThreadCount // 取到  taskHandledIndexStart ..< next
            if (notHandledTaskCount > 0) {
                next++
                notHandledTaskCount--
            }
            val taskHandledIndexStart = currentIndex
            currentIndex = next
            // 创建任务
            val vector = Vector3d(0.0, 0.0, 0.0)
            val job = scope.async {
                for (i in taskHandledIndexStart..<next) {
                    val it = copy[i]
                    // 复用节约内存
                    vector.set(it.x, it.y, it.z)
                    vector.rotate(q)
                    vector.rotate(toQ)
                    it.x = vector.x
                    it.y = vector.y
                    it.z = vector.z
                }
            }
            tasks.add(job)
        }
        runBlocking { tasks.awaitAll() }
        return shape
    }

    /** 让图形的对称轴指向某个点(图形跟着转变) */
    fun rotatePointsToPoint(
        locList: List<RelativeLocation>,
        origin: Vec3,
        toPoint: Vec3,
        axis: RelativeLocation
    ): List<RelativeLocation> {
        if (axis.length() in -0.00001..0.000001) {
            return locList
        }
        val relToPoint = RelativeLocation.of(origin, toPoint)
        return rotatePointsToPoint(locList, relToPoint, axis)
    }


    /**
     * @param angle 角度
     * @param rad 角度是否为弧度制
     * @return 返回符合游戏要求的角度制度数
     */
    fun toMinecraftAngle(angle: Double, rad: Boolean): Double {
        var enter = angle
        if (rad) {
            enter = Math.toDegrees(angle)
        }
        enter %= 360
        if (enter > 180) enter -= 360
        if (enter < -180) enter += 360
        return enter
    }

    /**
     * 修复输入角度 将他限定在-PI,PI这个区间内
     *
     * @param angle 角度制角度
     * @return 修复后的角度
     */
    fun fixAngle(angle: Number): Double {
        return toMinecraftAngle(angle.toDouble(), false)
    }


    /** @param yaw 输入弧度制yaw */
    fun toMinecraftYaw(yaw: Double): Double = yaw - PI / 2

    fun getYawFromLocation(loc: Vec3): Double {
        return atan2(-loc.x, loc.z)
    }

    fun getYawFromLocation(loc: RelativeLocation): Double {
        return atan2(-loc.x, loc.z)
    }

    fun getPitchFromLocation(v: RelativeLocation): Double {
        return atan2(v.y, sqrt(v.x.pow(2) + v.z.pow(2)))
    }

    fun getPitchFromLocation(v: Vec3): Double {
        val length = v.length()
        if (length == 0.0) return 0.0
        return asin(v.y / length)
    }

    /** 获取在start-end线段内的count个点集合 */
    fun getLineLocations(start: Vec3, end: Vec3, count: Int): List<RelativeLocation> {
        val origin = RelativeLocation.of(start)
        val res = mutableListOf(origin)
        val step = start.distanceTo(end) / count
        val direction = end.subtract(start).normalize().scale(step)
        val relativeDirection = RelativeLocation.of(direction)
        var next = origin
        for (i in 2..count) {
            val pos = next + relativeDirection
            next = pos.clone()
            res.add(next)
        }
        res.add(end.asRelative())
        return res
    }

    fun getLineLocations(start: RelativeLocation, end: RelativeLocation, count: Int): List<RelativeLocation> {
        return getLineLocations(start.toVector(), end.toVector(), count)
    }

    /** 获取 从origin 向 direction方向的射线上 每个间距为 step 且总数量为count的点集合 */
    fun getLineLocations(origin: Vec3, direction: Vec3, step: Double, count: Int): List<RelativeLocation> {
        val originRel = RelativeLocation.of(origin)
        val res = mutableListOf(originRel)
        val relativeDirection =
            RelativeLocation.of(Vec3(direction.x, direction.y, direction.z).normalize().scale(step))
        var next = originRel
        for (i in 2..count) {
            val pos = next + relativeDirection
            next = pos.clone()
            res.add(next)
        }
        return res
    }

    /**
     * 获取圆面 圆面在XZ上
     *
     * @param r 圆的半径
     * @param step 圆环之间的间距
     * @param preCircleCount 每个圆环的粒子个数
     */
    fun getRoundScapeLocations(r: Double, step: Double, preCircleCount: Int): MutableList<RelativeLocation> {
        val res = mutableListOf<RelativeLocation>()
        if (step <= 0 || r < step) {
            return res
        }
        var varR = step
        while (varR < r) {
            val stepCircle = 2 * PI / preCircleCount
            for (i in 1..preCircleCount) {
                val x = varR * cos(stepCircle * i)
                val z = varR * sin(stepCircle * i)
                res.add(
                    RelativeLocation(x, 0.0, z)
                )
            }
            varR += step
        }

        return res
    }

    /**
     * 获取圆面 圆面在XZ上
     *
     * @param r 圆的半径
     * @param step 圆环之间的间距
     * @param minCircleCount 一个圆环粒子个数的最小值
     * @param maxCircleCount 一个圆环粒子个数的最大值
     */
    fun getRoundScapeLocations(
        r: Double,
        step: Double,
        minCircleCount: Int,
        maxCircleCount: Int
    ): MutableList<RelativeLocation> {
        val res = mutableListOf<RelativeLocation>()
        if (step <= 0 || r < step) {
            return res
        }
        // 一共拥有圆环的个数
        val circleTotalCount = (r / step).toInt()
        var varR = step
        // 当前圆环的编号
        var currentCircle = 1
        // 小圆环到大圆环之间 粒子的差异
        val countStep = (maxCircleCount - minCircleCount) / circleTotalCount
        while (varR < r) {
            val currentCircleParticleCount =
                minCircleCount + currentCircle * countStep
            val stepCircle = 2 * PI / currentCircleParticleCount
            for (i in 1..currentCircleParticleCount) {
                val x = varR * cos(stepCircle * i)
                val z = varR * sin(stepCircle * i)
                res.add(
                    RelativeLocation(x, 0.0, z)
                )
            }
            varR += step
            currentCircle++
        }
        return res
    }

    /**
     * @param height 圆柱的高
     * @param heightStep 圆柱面之间的间距
     * @param r 圆柱的底面积半径
     * @param step 圆柱底面积圆环之间的间距
     * @param preCircleCount 圆柱底面积圆环的粒子个数
     */
    fun getCylinderLocations(
        height: Double,
        heightStep: Double,
        r: Double,
        step: Double,
        preCircleCount: Int
    ): MutableList<RelativeLocation> {
        if (height < heightStep) {
            return mutableListOf()
        }
        val start = getRoundScapeLocations(r, step, preCircleCount)
        val end = getRoundScapeLocations(r, step, preCircleCount).onEach {
            it.y += height
        }
        val heightCount = (height / heightStep).toInt()
        val res = mutableListOf<RelativeLocation>()
        for ((index, startLoc) in start.withIndex()) {
            val endLoc = end[index]
            res.addAll(
                getLineLocations(
                    startLoc.toVector(), endLoc.toVector(), heightStep, heightCount
                )
            )
        }
        return res
    }

    /**
     * @param height 圆柱的高
     * @param heightStep 圆柱面之间的间距
     * @param r 圆柱的底面积半径
     * @param step 圆柱底面积圆环之间的间距
     * @param preCircleCount 圆柱底面积圆环的粒子个数
     */
    fun getCylinderLocations(
        height: Double,
        heightStep: Double,
        r: Double,
        step: Double,
        minCircleCount: Int,
        maxCircleCount: Int
    ): MutableList<RelativeLocation> {
        if (height < heightStep) {
            return mutableListOf()
        }
        val start = getRoundScapeLocations(r, step, minCircleCount, maxCircleCount)
        val end = getRoundScapeLocations(r, step, minCircleCount, maxCircleCount).onEach {
            it.y += height
        }
        val heightCount = (height / heightStep).toInt()
        val res = mutableListOf<RelativeLocation>()
        for ((index, startLoc) in start.withIndex()) {
            val endLoc = end[index]
            res.addAll(
                getLineLocations(
                    startLoc.toVector(), endLoc.toVector(), heightStep, heightCount
                )
            )
        }
        return res
    }

    /** 生成三次贝塞尔曲线（兼容旧的原点起笔二维曲线，Z 固定为 0） */
    fun generateBezierCurve(
        target: RelativeLocation,
        /** 起点的曲柄向量 */
        startHandle: RelativeLocation,
        /** 终点的曲柄向量 (以 target为原点) 在Pr ae的速度,值曲线的下一个关键帧曲柄中 方向和startHandle相反 因此这里也要相反 */
        endHandle: RelativeLocation,
        count: Int
    ): List<RelativeLocation> {
        require(count >= 1) { "Number of points must be at least 1" }
        val end = target + endHandle
        return List(count) { i ->
            val t = when (count) {
                1 -> 1.0
                else -> i.toDouble() / (count - 1)
            }

            val u = 1 - t
            val u2 = u * u
            val t2 = t * t

            // 三次贝塞尔曲线公式
            val x = (u2 * u * 0.0) +          // P0 (0,0)
                    (3 * u2 * t * startHandle.x) +  // P1 control point
                    (3 * u * t2 * end.x) +    // P2 control point
                    (t2 * t * target.x)           // P3 (end point)

            val y = (u2 * u * 0.0) +
                    (3 * u2 * t * startHandle.y) +
                    (3 * u * t2 * end.y) +
                    (t2 * t * target.y)

            RelativeLocation(x, y, 0.0)
        }
    }

    /**
     * 生成三次贝塞尔曲线（空间曲线）。
     *
     * @param start 起点
     * @param end 终点
     * @param startHandle 起点的曲柄向量（以 start 为原点）
     * @param endHandle 终点的曲柄向量（以 end 为原点）
     * @param count 采样点数量，count > 1 时会包含起点与终点
     */
    fun generateBezierCurve(
        start: RelativeLocation,
        end: RelativeLocation,
        startHandle: RelativeLocation,
        endHandle: RelativeLocation,
        count: Int
    ): List<RelativeLocation> {
        require(count >= 1) { "Number of points must be at least 1" }
        val startControlPoint = start + startHandle
        val endControlPoint = end + endHandle
        return List(count) { i ->
            val t = when (count) {
                1 -> 1.0
                else -> i.toDouble() / (count - 1)
            }
            cubicBezierPoint(t, start, startControlPoint, endControlPoint, end)
        }
    }

    fun evaluateBezierCurveYAtX(
        target: RelativeLocation,
        startHandle: RelativeLocation,
        endHandle: RelativeLocation,
        x: Double,
        iterations: Int = 26
    ): Double {
        if (target.x <= 0.0) return target.y
        val actualX = x.coerceIn(0.0, target.x)
        if (actualX <= 0.0) return 0.0
        if (actualX >= target.x) return target.y
        val end = target + endHandle
        var lo = 0.0
        var hi = 1.0
        var mid = 0.5
        repeat(iterations.coerceAtLeast(1)) {
            mid = (lo + hi) * 0.5
            val bx = cubicBezier(mid, 0.0, startHandle.x, end.x, target.x)
            if (bx < actualX) {
                lo = mid
            } else {
                hi = mid
            }
        }
        return cubicBezier(mid, 0.0, startHandle.y, end.y, target.y)
    }

    private fun cubicBezierPoint(
        t: Double,
        p0: RelativeLocation,
        p1: RelativeLocation,
        p2: RelativeLocation,
        p3: RelativeLocation
    ): RelativeLocation {
        return RelativeLocation(
            cubicBezier(t, p0.x, p1.x, p2.x, p3.x),
            cubicBezier(t, p0.y, p1.y, p2.y, p3.y),
            cubicBezier(t, p0.z, p1.z, p2.z, p3.z)
        )
    }

    fun cubicBezier(t: Double, p0: Double, p1: Double, p2: Double, p3: Double): Double {
        val u = 1 - t
        val u2 = u * u
        val t2 = t * t
        return u2 * u * p0 +
                3 * u2 * t * p1 +
                3 * u * t2 * p2 +
                t2 * t * p3
    }

    fun calculateEulerAnglesToPoint(target: Vector3f): Triple<Float, Float, Float> {
        // 处理零向量特例
        if (target.x == 0f && target.y == 0f && target.z == 0f) {
            return Triple(0f, 0f, 0f)
        }

        // 计算俯仰角（Pitch，绕 X 轴）
        val pitch = atan2(target.y, sqrt(target.x * target.x + target.z * target.z))

        // 计算偏航角（Yaw，绕 Y 轴）
        val yaw = -atan2(target.z, target.x)

        // 绕 Z 轴的滚动角（Roll）默认为 0，因为纯指向不需要 Z 轴旋转
        val roll = 0f

        return Triple(pitch, yaw, roll)
    }

    /**
     * 生成螺旋上升的一个图案
     *
     * @param startRadius 起始半径
     * @param endRadius 到达height时结束半径
     * @param height 螺旋高度
     * @param step 上升时从0-height的点数
     * @param rotateSpeed 螺旋速度 弧度制
     * @param radiusBias 半径变化曲线系数 (设置为1则是平均分布在start-end)
     * @param heightBias 高度变化曲线系数 (设置为1则是平均分布在0-height)
     * @return 图案集合
     */
    fun generateSpiralCircleXZ(
        startRadius: Double,
        endRadius: Double,
        height: Double,
        step: Double,
        rotateSpeed: Double,
        radiusBias: Double = 1.0,
        heightBias: Double = 1.0
    ): List<RelativeLocation> {
        val count = (height / step).roundToInt()
        return generateSpiralCircleXZ(startRadius, endRadius, height, count, rotateSpeed, radiusBias, heightBias)
    }

    /**
     * 生成螺旋上升的一个图案
     *
     * @param startRadius 起始半径
     * @param endRadius 到达height时结束半径
     * @param height 螺旋高度
     * @param count 点的个数
     * @param rotateSpeed 螺旋速度 弧度制
     * @param radiusBias 半径变化曲线系数 (设置为1则是平均分布在start-end)
     * @param heightBias 高度变化曲线系数 (设置为1则是平均分布在0-height)
     * @return 图案集合
     */
    fun generateSpiralCircleXZ(
        startRadius: Double,
        endRadius: Double,
        height: Double,
        count: Int,
        rotateSpeed: Double,
        radiusBias: Double = 1.0,
        heightBias: Double = 1.0
    ): List<RelativeLocation> {
        val res = mutableListOf<RelativeLocation>()
        var currentRadian = 0.0
        repeat(count) {
            val process = it.toDouble() / (count - 1).coerceAtLeast(1)
            val biasedRadius = process.pow(radiusBias)
            val biasedHeight = process.pow(heightBias)
            val currentRadius = GraphMathHelper.lerp(biasedRadius, startRadius, endRadius)
            val currentHeight = GraphMathHelper.lerp(biasedHeight, 0.0, height)
            res.add(
                RelativeLocation(
                    cos(currentRadian) * currentRadius, currentHeight, sin(currentRadian) * currentRadius
                )
            )
            currentRadian += rotateSpeed
        }
        return res
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
        seed: Long? = null,
        mode: NoiseMode = NoiseMode.AXIS_UNIFORM,
        offsetLenMin: Double? = null,
        offsetLenMax: Double? = null,
    ) {
        if (points.isEmpty()) return
        if (noiseX == 0.0 && noiseY == 0.0 && noiseZ == 0.0) return

        // 统一检查一下长度限制参数
        if (offsetLenMin != null && offsetLenMax != null) {
            require(offsetLenMin <= offsetLenMax) { "offsetLenMin must <= offsetLenMax" }
        }

        // 基础随机源：不传 seed 就用当前时间（或者 Random.Default 也行）
        val baseRand = if (seed != null) Random(seed) else Random(System.nanoTime())

        for (i in points.indices) {
            val p = points[i]

            val rnd = if (seed != null) Random(seed + i * 0x9E3779B97F4A7C15_UL.toLong()) else baseRand

            var ox: Double
            var oy: Double
            var oz: Double

            when (mode) {
                NoiseMode.AXIS_UNIFORM -> {
                    // 立方体噪声：xyz 各自独立均匀
                    ox = (rnd.nextDouble() * 2.0 - 1.0) * noiseX
                    oy = (rnd.nextDouble() * 2.0 - 1.0) * noiseY
                    oz = (rnd.nextDouble() * 2.0 - 1.0) * noiseZ
                }

                NoiseMode.SPHERE_UNIFORM -> {
                    // 单位球内均匀：用 rejection sampling
                    var x: Double
                    var y: Double
                    var z: Double
                    while (true) {
                        x = rnd.nextDouble() * 2.0 - 1.0
                        y = rnd.nextDouble() * 2.0 - 1.0
                        z = rnd.nextDouble() * 2.0 - 1.0
                        val r2 = x * x + y * y + z * z
                        if (r2 > 1e-12 && r2 <= 1.0) {
                            // 按轴拉伸到椭球噪声
                            ox = x * noiseX
                            oy = y * noiseY
                            oz = z * noiseZ
                            break
                        }
                    }
                }

                NoiseMode.SHELL_UNIFORM -> {
                    val u = rnd.nextDouble()
                    val v = rnd.nextDouble()
                    val theta = 2.0 * Math.PI * u
                    val n = 2.0 * v - 1.0
                    val t = sqrt(1.0 - n * n)
                    val xDir = t * cos(theta)
                    val yDir = t * sin(theta)
                    val w = rnd.nextDouble()
                    val r = cbrt(w)
                    val (x, y, z) = Triple(xDir * r, yDir * r, n * r)
                    ox = x * noiseX
                    oy = y * noiseY
                    oz = z * noiseZ
                }
            }

            // 可选：对偏移向量长度做限制（注意：这里限制的是偏移量，不是点本身）
            if (offsetLenMin != null || offsetLenMax != null) {
                val len = sqrt(ox * ox + oy * oy + oz * oz)
                if (len > 1e-12) {
                    var scale = 1.0
                    if (offsetLenMin != null && len < offsetLenMin) scale = offsetLenMin / len
                    if (offsetLenMax != null && len > offsetLenMax) scale = offsetLenMax / len
                    ox *= scale
                    oy *= scale
                    oz *= scale
                } else {
                    // len 太小，直接不偏移（也可以改成给一个固定方向的最小偏移）
                    ox = 0.0; oy = 0.0; oz = 0.0
                }
            }

            // 就地修改点对象
            p.x += ox
            p.y += oy
            p.z += oz
        }
    }


    /**
     * 生成爆炸曲线点
     *
     * @param power 爆炸威力
     * @param maxHeight 爆炸点的最高高度
     * @param handleRadius 处理爆炸的最大范围
     * @param step 处理圆环的步长
     * @param minCircleCount 圆环的最小点个数
     * @param maxCircleCount 圆环的最大点个数
     */
    fun generateExplosionCurve(
        power: Double,
        maxHeight: Double,
        handleRadius: Double,
        step: Double = 1.0,
        minCircleCount: Int = 8,
        maxCircleCount: Int = 24
    ): List<RelativeLocation> {
        // 参数有效性校验
        if (handleRadius <= 0 || maxHeight <= 0 || step <= 0) return emptyList()
        if (minCircleCount <= 0 || maxCircleCount < minCircleCount) return emptyList()

        val points = mutableListOf<RelativeLocation>().apply {
            // 添加爆炸中心点
            add(RelativeLocation(0.0, maxHeight, 0.0))
        }

        val maxRadius = handleRadius.coerceAtLeast(step)
        val totalCircles = (maxRadius / step).toInt()

        // 计算粒子数增量步长
        val countStep = if (totalCircles > 0) {
            (maxCircleCount - minCircleCount).toDouble() / totalCircles
        } else 0.0

        var currentRadius = step
        repeat(totalCircles) { circleIndex ->
            // 计算当前圆环粒子数量
            val particleCount = minCircleCount + (countStep * circleIndex).toInt()

            // 环形坐标生成
            val angleStep = 2 * Math.PI / particleCount
            repeat(particleCount) { particleIndex ->
                val angle = angleStep * particleIndex
                val x = currentRadius * cos(angle)
                val z = currentRadius * sin(angle)

                // 计算破坏强度
                val normalized = currentRadius / handleRadius
                val intensity = maxHeight * (1 - normalized).pow(power)

                points.add(RelativeLocation(x, intensity, z))
            }
            currentRadius += step
        }

        return points
    }

    fun rotateQuatToPoint(rotation: Quaternionf, to: Vec3) {
        rotateQuatToPoint(rotation, to.toVector3f())
    }

    fun rotateQuatToPoint(rotation: Quaternionf, to: RelativeLocation) {
        rotateQuatToPoint(rotation, to.toVector3f())
    }

    fun rotateQuatToPoint(rotation: Quaternionf, to: Vector3f) {
        if (to.length() < 1e-6) return
        to.normalize()

        val worldUp = Vector3f(0f, 1f, 0f)

        val localForward = Vector3f(0f, 0f, 1f)

        val qAlign = Quaternionf().rotateTo(localForward, to)

        val localUp = Vector3f(0f, 1f, 0f)
        val curUp = localUp.rotate(qAlign, Vector3f()) // 当前 up（世界空间）
        val upProj = projectOnPlane(worldUp, to)
        val curUpProj = projectOnPlane(curUp, to)

        if (upProj.lengthSquared() < 1e-8f || curUpProj.lengthSquared() < 1e-8f) {
            val altUp = Vector3f(0f, 0f, 1f)
            val upProj2 = projectOnPlane(altUp, to)
            val curUpProj2 = projectOnPlane(curUp, to)
            if (upProj2.lengthSquared() >= 1e-8f && curUpProj2.lengthSquared() >= 1e-8f) {
                upProj2.normalize()
                curUpProj2.normalize()
                val twist = signedAngleAroundAxis(curUpProj2, upProj2, to)
                val qTwist = Quaternionf().rotateAxis(twist, to.x, to.y, to.z)
                rotation.set(qTwist.mul(qAlign))
                return
            }
            rotation.set(qAlign)
            return
        }

        upProj.normalize()
        curUpProj.normalize()

        val twist = signedAngleAroundAxis(curUpProj, upProj, to)
        val qTwist = Quaternionf().rotateAxis(twist, to.x, to.y, to.z)

        rotation.set(qTwist.mul(qAlign))
    }

    /**
     * # 判断点是否被包含在球囊中
     * - 一般用于判断碰撞是否穿过目标点
     * @param current 球心当前位置
     * @param tickVelocity 球心高度和高度方向
     * @param checkRadius 球的半径
     * @param targetPos 目标检测点
     * @return
     */
    fun isPointCrossBySphere(current: Vec3, tickVelocity: Vec3, checkRadius: Number, targetPos: Vec3): Boolean {
        if (tickVelocity.lengthSqr() <= 1e-12) return targetPos.distanceToSqr(current) <= checkRadius.toDouble()

        val deltaTarget = targetPos - current
        val radius = checkRadius.toDouble()

        if (tickVelocity.x == 0.0 && tickVelocity.y == 0.0 && tickVelocity.z == 0.0) {
            val delta = targetPos - current
            return delta.lengthSqr() <= radius * radius
        }


        // 线段最近点
        val t = (deltaTarget.dot(tickVelocity) / tickVelocity.lengthSqr()).coerceIn(0.0, 1.0)

        val closest = current + tickVelocity * t

        val len = closest.distanceToSqr(targetPos)

        return (radius * radius) >= len
    }

    /**
     * # 判断点是否被包含在 Box 射线中
     * - 一般用于判断碰撞盒是否穿过目标点
     * @param current Box 中心当前位置
     * @param tickVelocity Box 中心本 tick 位移
     * @param checkBox 检测 Box
     * @param targetPos 目标检测点
     * @return
     */
    fun isPointCrossByBox(current: Vec3, tickVelocity: Vec3, checkBox: HitBox, targetPos: Vec3): Boolean {
        val minX = targetPos.x - checkBox.x2
        val minY = targetPos.y - checkBox.y2
        val minZ = targetPos.z - checkBox.z2
        val maxX = targetPos.x - checkBox.x1
        val maxY = targetPos.y - checkBox.y1
        val maxZ = targetPos.z - checkBox.z1

        if (tickVelocity.lengthSqr() <= 1e-12) {
            return current.x in minX..maxX
                    && current.y in minY..maxY
                    && current.z in minZ..maxZ
        }

        var minTime = 0.0
        var maxTime = 1.0

        fun updateAxisRange(start: Double, velocity: Double, min: Double, max: Double): Boolean {
            if (abs(velocity) <= 1e-12) {
                return start in min..max
            }

            var startTime = (min - start) / velocity
            var endTime = (max - start) / velocity

            if (startTime > endTime) {
                val cache = startTime
                startTime = endTime
                endTime = cache
            }

            minTime = max(minTime, startTime)
            maxTime = min(maxTime, endTime)
            return minTime <= maxTime
        }

        return updateAxisRange(current.x, tickVelocity.x, minX, maxX)
                && updateAxisRange(current.y, tickVelocity.y, minY, maxY)
                && updateAxisRange(current.z, tickVelocity.z, minZ, maxZ)
    }

    /**
     * 判断从 [start] 沿 [direction] 偏移、半径为 [radius] 的圆柱是否和以 [center] 为中心的 [hitBox] 相交。
     */
    fun isIntersectsBox(
        start: Vec3,
        direction: Vec3,
        radius: Double,
        center: Vec3,
        hitBox: HitBox,
    ): Boolean {
        return intersectsCylinder(start, start.add(direction), radius, center, hitBox)
    }

    fun isIntersectsBox(
        start: Vec3,
        direction: RelativeLocation,
        radius: Double,
        center: Vec3,
        hitBox: HitBox,
    ): Boolean {
        return isIntersectsBox(start, direction.toVector(), radius, center, hitBox)
    }

    /**
     * 判断 start -> end、半径为 [radius] 的圆柱是否和世界坐标 [box] 相交。
     */
    fun isIntersectsBox(start: Vec3, end: Vec3, radius: Double, box: AABB): Boolean {
        if (radius < 0.0) return false
        if (start.distanceToSqr(end) <= EPS * EPS) {
            return distanceSqrToAabb(start, box) <= radius * radius + EPS
        }
        return intersectsCylinderWithBoxCorners(start, end, radius, aabbCorners(box))
    }

    fun intersectsCylinder(start: Vec3, end: Vec3, radius: Double, box: AABB): Boolean {
        return isIntersectsBox(start, end, radius, box)
    }

    fun intersectsCylinder(start: Vec3, end: Vec3, radius: Double, center: Vec3, hitBox: HitBox): Boolean {
        if (radius < 0.0) return false
        if (start.distanceToSqr(end) <= EPS * EPS) {
            return distanceSqrToHitBox(start, center, hitBox) <= radius * radius + EPS
        }
        return intersectsCylinderWithBoxCorners(start, end, radius, hitBoxCorners(center, hitBox))
    }

    fun intersectsBox(first: AABB, second: AABB): Boolean {
        return first.maxX >= second.minX && first.minX <= second.maxX
                && first.maxY >= second.minY && first.minY <= second.maxY
                && first.maxZ >= second.minZ && first.minZ <= second.maxZ
    }

    fun intersectRange(first: AABB, second: AABB): AABB? {
        val minX = max(first.minX, second.minX)
        val minY = max(first.minY, second.minY)
        val minZ = max(first.minZ, second.minZ)
        val maxX = min(first.maxX, second.maxX)
        val maxY = min(first.maxY, second.maxY)
        val maxZ = min(first.maxZ, second.maxZ)
        if (maxX < minX || maxY < minY || maxZ < minZ) return null
        return AABB(minX, minY, minZ, maxX, maxY, maxZ)
    }

    fun intersectsBox(center: Vec3, hitBox: HitBox, box: AABB): Boolean {
        return intersectsBoxes(hitBoxCorners(center, hitBox), hitBoxAxes(hitBox), aabbCorners(box), AABB_AXES)
    }

    fun intersectsBox(firstCenter: Vec3, first: HitBox, secondCenter: Vec3, second: HitBox): Boolean {
        return intersectsBoxes(
            hitBoxCorners(firstCenter, first),
            hitBoxAxes(first),
            hitBoxCorners(secondCenter, second),
            hitBoxAxes(second),
        )
    }

    private fun intersectsCylinderWithBoxCorners(
        start: Vec3,
        end: Vec3,
        radius: Double,
        corners: List<Vec3>,
    ): Boolean {
        val axis = end.subtract(start)
        val length = axis.length()
        val axisUnit = axis.scale(1.0 / length)
        val base = if (abs(axisUnit.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
        val sideX = axisUnit.cross(base).normalize()
        val sideY = axisUnit.cross(sideX).normalize()
        val points = ArrayList<Point2>()

        fun addProjected(point: Vec3) {
            val relative = point.subtract(start)
            val projected = Point2(relative.dot(sideX), relative.dot(sideY))
            if (points.none { it.distanceSqr(projected) <= EPS * EPS }) {
                points += projected
            }
        }

        fun axisDistance(point: Vec3): Double {
            return point.subtract(start).dot(axisUnit)
        }

        val distances = DoubleArray(corners.size) { axisDistance(corners[it]) }
        for (i in corners.indices) {
            if (distances[i] >= -EPS && distances[i] <= length + EPS) {
                addProjected(corners[i])
            }
        }

        for (edge in BOX_EDGES) {
            val firstIndex = edge[0]
            val secondIndex = edge[1]
            val firstDistance = distances[firstIndex]
            val secondDistance = distances[secondIndex]
            val delta = secondDistance - firstDistance
            if (abs(delta) <= EPS) continue

            for (plane in doubleArrayOf(0.0, length)) {
                val t = (plane - firstDistance) / delta
                if (t >= -EPS && t <= 1.0 + EPS) {
                    addProjected(lerp(corners[firstIndex], corners[secondIndex], t.coerceIn(0.0, 1.0)))
                }
            }
        }

        if (points.isEmpty()) return false
        return projectedHullIntersectsCircle(points, radius)
    }

    private fun projectedHullIntersectsCircle(points: List<Point2>, radius: Double): Boolean {
        val radiusSqr = radius * radius + EPS
        if (points.any { it.lengthSqr() <= radiusSqr }) return true

        val hull = convexHull(points)
        if (hull.size >= 3 && containsOrigin(hull)) return true
        if (hull.size == 1) return hull[0].lengthSqr() <= radiusSqr

        for (i in hull.indices) {
            val next = hull[(i + 1) % hull.size]
            if (distanceSqrToOriginSegment(hull[i], next) <= radiusSqr) return true
        }
        return false
    }

    private fun hitBoxCorners(center: Vec3, hitBox: HitBox): List<Vec3> {
        val rotation = Quaterniond().rotateY(-hitBox.yaw).rotateX(-hitBox.pitch)
        val vector = Vector3d()
        val corners = ArrayList<Vec3>(8)
        for (x in doubleArrayOf(hitBox.x1, hitBox.x2)) {
            for (y in doubleArrayOf(hitBox.y1, hitBox.y2)) {
                for (z in doubleArrayOf(hitBox.z1, hitBox.z2)) {
                    vector.set(x, y, z).rotate(rotation)
                    corners += Vec3(center.x + vector.x, center.y + vector.y, center.z + vector.z)
                }
            }
        }
        return corners
    }

    private fun aabbCorners(box: AABB): List<Vec3> {
        val corners = ArrayList<Vec3>(8)
        for (x in doubleArrayOf(box.minX, box.maxX)) {
            for (y in doubleArrayOf(box.minY, box.maxY)) {
                for (z in doubleArrayOf(box.minZ, box.maxZ)) {
                    corners += Vec3(x, y, z)
                }
            }
        }
        return corners
    }

    private fun hitBoxAxes(hitBox: HitBox): List<Vec3> {
        val rotation = Quaterniond().rotateY(-hitBox.yaw).rotateX(-hitBox.pitch)
        return listOf(
            Vector3d(1.0, 0.0, 0.0).rotate(rotation).toVec3(),
            Vector3d(0.0, 1.0, 0.0).rotate(rotation).toVec3(),
            Vector3d(0.0, 0.0, 1.0).rotate(rotation).toVec3(),
        )
    }

    private fun intersectsBoxes(
        firstCorners: List<Vec3>,
        firstAxes: List<Vec3>,
        secondCorners: List<Vec3>,
        secondAxes: List<Vec3>,
    ): Boolean {
        fun separatedOn(axis: Vec3): Boolean {
            if (axis.lengthSqr() <= EPS * EPS) return false
            val unit = axis.normalize()
            val firstRange = projectCorners(firstCorners, unit)
            val secondRange = projectCorners(secondCorners, unit)
            return firstRange.second < secondRange.first - EPS || secondRange.second < firstRange.first - EPS
        }

        for (axis in firstAxes) {
            if (separatedOn(axis)) return false
        }
        for (axis in secondAxes) {
            if (separatedOn(axis)) return false
        }
        for (firstAxis in firstAxes) {
            for (secondAxis in secondAxes) {
                if (separatedOn(firstAxis.cross(secondAxis))) return false
            }
        }
        return true
    }

    private fun projectCorners(corners: List<Vec3>, axis: Vec3): Pair<Double, Double> {
        var min = Double.POSITIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY
        for (corner in corners) {
            val projected = corner.dot(axis)
            if (projected < min) min = projected
            if (projected > max) max = projected
        }
        return min to max
    }

    private fun Vector3d.toVec3(): Vec3 {
        return Vec3(x, y, z)
    }

    private fun distanceSqrToHitBox(point: Vec3, center: Vec3, hitBox: HitBox): Double {
        val rotation = Quaterniond().rotateY(-hitBox.yaw).rotateX(-hitBox.pitch).conjugate()
        val local = Vector3d(point.x - center.x, point.y - center.y, point.z - center.z).rotate(rotation)
        val closestX = local.x.coerceIn(hitBox.x1, hitBox.x2)
        val closestY = local.y.coerceIn(hitBox.y1, hitBox.y2)
        val closestZ = local.z.coerceIn(hitBox.z1, hitBox.z2)
        val dx = local.x - closestX
        val dy = local.y - closestY
        val dz = local.z - closestZ
        return dx * dx + dy * dy + dz * dz
    }

    private fun distanceSqrToAabb(point: Vec3, box: AABB): Double {
        val closestX = point.x.coerceIn(box.minX, box.maxX)
        val closestY = point.y.coerceIn(box.minY, box.maxY)
        val closestZ = point.z.coerceIn(box.minZ, box.maxZ)
        val dx = point.x - closestX
        val dy = point.y - closestY
        val dz = point.z - closestZ
        return dx * dx + dy * dy + dz * dz
    }

    private fun lerp(start: Vec3, end: Vec3, t: Double): Vec3 {
        return Vec3(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
            start.z + (end.z - start.z) * t,
        )
    }

    private fun convexHull(points: List<Point2>): List<Point2> {
        if (points.size <= 2) return points
        val sorted = points.sortedWith(compareBy<Point2> { it.x }.thenBy { it.y })
        val lower = ArrayList<Point2>()
        for (point in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), point) <= EPS) {
                lower.removeAt(lower.lastIndex)
            }
            lower += point
        }
        val upper = ArrayList<Point2>()
        for (point in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), point) <= EPS) {
                upper.removeAt(upper.lastIndex)
            }
            upper += point
        }
        return (lower.dropLast(1) + upper.dropLast(1)).ifEmpty { sorted.take(1) }
    }

    private fun containsOrigin(hull: List<Point2>): Boolean {
        var sign = 0
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            val cross = (b.x - a.x) * -a.y - (b.y - a.y) * -a.x
            if (abs(cross) <= EPS) continue
            val currentSign = if (cross > 0.0) 1 else -1
            if (sign != 0 && sign != currentSign) return false
            sign = currentSign
        }
        return true
    }

    private fun cross(a: Point2, b: Point2, c: Point2): Double {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    }

    private fun distanceSqrToOriginSegment(a: Point2, b: Point2): Double {
        val abX = b.x - a.x
        val abY = b.y - a.y
        val abLengthSqr = abX * abX + abY * abY
        if (abLengthSqr <= EPS * EPS) return a.lengthSqr()
        val t = (-(a.x * abX + a.y * abY) / abLengthSqr).coerceIn(0.0, 1.0)
        val closestX = a.x + abX * t
        val closestY = a.y + abY * t
        return closestX * closestX + closestY * closestY
    }

    private fun projectOnPlane(v: Vector3f, nUnit: Vector3f): Vector3f {
        // v_proj = v - n*(v·n)
        val dot = v.dot(nUnit)
        return Vector3f(
            v.x - nUnit.x * dot,
            v.y - nUnit.y * dot,
            v.z - nUnit.z * dot
        )
    }

    /**
     * 返回把 a 绕 axisUnit 旋转到 b 的有符号角度（-pi..pi）
     * axisUnit 必须单位化
     */
    private fun signedAngleAroundAxis(a: Vector3f, b: Vector3f, axisUnit: Vector3f): Float {
        // atan2( axis·(a×b), a·b )
        val cross = Vector3f(a).cross(b)
        val sin = cross.dot(axisUnit)
        val cos = a.dot(b)
        return atan2(sin.toDouble(), cos.toDouble()).toFloat()
    }

    /** 旋转是通过旋转x/z 轴来坐标值的 由于sqrt pow 是恒大于0的值因此不能用于坐标求值 */
    private fun getAxisSymbol(loc: Vec3): Int {
        val quadrants = getQuadrants(getYawFromLocation(loc))
        return when (quadrants) { // 1
            1 -> if (loc.x >= 0 && loc.z >= 0) 1 else -1
            2 -> if (loc.x <= 0 && loc.z >= 0) 1 else -1
            3 -> if (loc.x <= 0 && loc.z <= 0) 1 else -1
            4 -> if (loc.x >= 0 && loc.z <= 0) 1 else -1
            else -> 1
        }
    }

    private fun getQuadrants(rad: Double): Int {
        val sin = sin(rad)
        val cos = cos(rad)
        return if (sin > 0 && cos > 0) 1 else if (sin < 0 && cos > 0) 4 else if (sin > 0 && cos < 0) 2 else if (sin < 0 && cos < 0) 3
        else if (sin == 0.0 && cos > 0) {
            // X轴上
            1
        } else if (sin == 0.0 && cos < 0) {
            // X负半轴
            3
        } else if (sin > 0) {
            2
        } else {
            4
        }
    }

    private data class Point2(val x: Double, val y: Double) {
        fun lengthSqr(): Double = x * x + y * y

        fun distanceSqr(other: Point2): Double {
            val dx = x - other.x
            val dy = y - other.y
            return dx * dx + dy * dy
        }
    }

}
