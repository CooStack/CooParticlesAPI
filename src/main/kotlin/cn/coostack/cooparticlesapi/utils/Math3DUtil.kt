package cn.coostack.cooparticlesapi.utils

import net.minecraft.util.math.Vec3d
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import java.util.ArrayList
import kotlin.math.*
import kotlin.random.Random

object Math3DUtil {
    private val random = Random(System.currentTimeMillis())

    /**
     * 将RGB值转换为Minecraft粒子使用的 rgb值(/255)
     */
    fun colorOf(r: Int, g: Int, b: Int): Vector3f {
        return Vector3f(r.toFloat() / 255, g.toFloat() / 255, b.toFloat() / 255)
    }

    /**
     * 闪电
     */
    fun getLightningEffectNodes(
        start: RelativeLocation, end: RelativeLocation, counts: Int
    ): List<RelativeLocation> {
        // 二分 start - > end 位置
        // 先获取中点
        val mid = start + (end - start).multiply(0.5)
        // 让中点进行偏移
        val len = end.distance(start)
        val offsetStep = len / 4
        mid.x += random.nextDouble(-offsetStep, offsetStep)
        mid.y += random.nextDouble(-offsetStep, offsetStep)
        mid.z += random.nextDouble(-offsetStep, offsetStep)
        val res = mutableListOf(mid)
        if (counts <= 1) {
            return res
        }
        val left = getLightningEffectNodes(start, mid, counts - 1)
        val right = getLightningEffectNodes(mid, end, counts - 1)
        // 合并点集合
        return left + right
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

    /**
     * 在 XZ 平面上生成离散化的三维环形分布点集
     * @param r       目标圆环的基础半径（单位：方块），建议非负值
     * @param pointRadius  在discrete属性设置为0时 点所在的圆环的位置角度参数 输入弧度制
     * @param discrete 最大分散距离（单位：方块），控制点与标准圆环的偏离程度：
     *                - = 0 时所有点严格位于圆环上
     *                - > 0 时点会在三维空间中以该值为最大半径随机偏移
     *                实际偏移量为 [0, discrete] 的随机值，负值会被自动归零
     */
    fun getSingleDiscreteOnCircleXZ(r: Double, discrete: Double, pointRadius: Double): RelativeLocation {
        val effectiveDiscrete = discrete.coerceAtLeast(0.0)  // 确保非负分散度
        // 生成标准圆环坐标 (y 固定为 0)
        val x0 = r * cos(pointRadius)
        val z0 = r * sin(pointRadius)
        // 生成三维随机偏移向量（球坐标系）
        val u = Random.nextDouble()
        val v = Random.nextDouble()
        val azimuth = 2 * PI * u      // 方位角 [0, 2π)
        val polar = acos(2 * v - 1)   // 极角 [0, π] 保证均匀分布
        val offsetMag = Random.nextDouble() * effectiveDiscrete  // 偏移量 [0, discrete]
        // 转换为笛卡尔坐标系的偏移量
        val horizontal = offsetMag * sin(polar)
        val dx = horizontal * cos(azimuth)
        val dz = horizontal * sin(azimuth)
        val dy = offsetMag * cos(polar)  // 垂直方向偏移
        // 合成最终坐标
        return RelativeLocation(
            x = x0 + dx,
            y = dy,  // 原 y 坐标为 0，直接使用偏移量
            z = z0 + dz
        )
    }

    /**
     * 在 XZ 平面上生成离散化的三维环形分布点集
     * @param r       目标圆环的基础半径（单位：方块），建议非负值
     * @param count   需要生成的离散点数量，必须为正整数
     * @param discrete 最大分散距离（单位：方块），控制点与标准圆环的偏离程度：
     *                - = 0 时所有点严格位于圆环上
     *                - > 0 时点会在三维空间中以该值为最大半径随机偏移
     *                实际偏移量为 [0, discrete] 的随机值，负值会被自动归零
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
     * 生成以 r为半径的圆的 内接正n边形
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
            val direction = Vec3d(vj.x - vi.x, vj.y - vi.y, vj.z - vi.z)
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
     * 让两个点集合
     * 连线规则如下
     * 前提: points.size > to.size
     * 建议输入的点集合的个数 points.size % to.size == 0
     * 如果不为0 则会有points.size % to.size 个点不会被链接
     * 如果输入的点集合大小相反则链接规则也会相反
     * 令 step = points.size / to.size (整除)
     * points 的第i个点到第i+step -1个点会链接 to的第i个点
     *
     * 如果你使用了两个圆(Math3DUtil.getCircleXZ())上平均分布的点来调用函数
     * 会发现两个圆的第一个点其实角度相同
     * 所以你需要使用 Math3DUtil.rotateAsAxis() 对小的圆进行旋转
     * 旋转角度为 -PI / points.size 这样得到的线是均匀分布的
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
     * @see getCycloidGraphic 获取此函数生成的图像的顶点
     * 参数要求必须和 getCycloidGraphic 生成的参数完全一致
     * @param count 填写你使用  getCycloidGraphic方法时 输入的count
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


    /**
     * 求最大公约数
     */
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
     * 傅里叶级数
     * 生成以r1为半径的圆上的动点A为圆心 r2为半径 上的动点P的轨迹 点A的移动速度为w1 点P的移动速度为w2
     * @param r1 中心圆的半径
     * @param r2 中心圆上的圆的半径
     * @param w1 中心圆的角速度
     * @param w2 中心圆上的圆的角速度
     * r1:r2 与 w1:w2 和 生成的图形有紧密的关系
     * 例如
     * r1:r2 = 3:2 w1:w2 = 2:-3 时 图像是一个五角星
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

    fun getBallLocations(r: Double, countPow: Int): MutableList<RelativeLocation> {
        val result = ArrayList<RelativeLocation>()
        var rx = 0.0
        var ry = 0.0
        val step = 2 * PI / countPow
        for (i in 1..countPow) {
            for (j in 1..countPow) {
                // 将PI 分割成 countPow份
                result.add(
                    RelativeLocation(
                        r * cos(rx) * cos(ry),
                        r * sin(rx),
                        r * sin(ry) * cos(rx)
                    )
                )
                ry += step
            }
            ry = 0.0
            rx += step
        }
        return result
    }

    /**
     * from new bing
     * 将一个相对位置按照axis旋转 n度
     * @param angle 角度 输入时使用弧度制的角度
     */
    fun rotateVector(point: RelativeLocation, axis: RelativeLocation, angle: Double): RelativeLocation {
        return RotationMatrix.fromAxisAngle(axis, angle).applyToClone(point)
    }


    /**
     * 向量图形绕轴旋转N度
     * @param angle 角度 输入一个弧度制角度
     */
    fun rotateAsAxis(locList: List<RelativeLocation>, axis: RelativeLocation, angle: Double): List<RelativeLocation> {
        for (loc in locList) {
            RotationMatrix.fromAxisAngle(axis, angle).applyTo(loc)
        }
        return locList
    }

    /**
     * 让图形的对称轴指向某个点(图形跟着转变)
     */
    fun rotatePointsToPoint(
        locList: List<RelativeLocation>,
        toPoint: RelativeLocation,
        axis: RelativeLocation
    ): List<RelativeLocation> {
        // 同向共线
        if (axis.cross(toPoint).length() in -1e-5..1e-5 && axis.dot(toPoint) > 0) {
            return locList
        }
        // 计算旋转角度
        // 首先，将目标点（toPoint）和当前轴（axis）都归一化
        val normalizedAxis = axis.normalize()
        val normalizedToPoint = toPoint.normalize()

        // 计算两个向量之间的夹角
        val angle = acos(normalizedAxis.dot(normalizedToPoint))

        // 计算旋转轴，它是当前轴和目标点的叉乘
        val rotationAxis = normalizedAxis.cross(normalizedToPoint).normalize()
        // 使用rotateAsAxis函数旋转locList
        return rotateAsAxis(locList, rotationAxis, angle)
    }

    /**
     * 旋转到对应的yaw和pitch 上
     *
     * TODO 莫名其妙的旋转错误
     *
     */
    fun rotatePointsToWithAngle(
        shape: List<RelativeLocation>,
        yaw: Double,
        pitch: Double,
        axis: RelativeLocation
    ): List<RelativeLocation> {
        val axisYaw = getYawFromLocation(axis)
        val axisPitch = getPitchFromLocation(axis)
        val yawDelta = (yaw - axisYaw)
        val pitchDelta = -(pitch - axisPitch).coerceIn(
            -PI / 2, PI / 2
        )
        val q = Quaterniond()
        q.rotationXYZ(
            -pitchDelta,
            yawDelta,
            0.0
        )
        shape.onEach {
            val v = Vector3d(it.x,it.y,it.z)
            v.rotate(q)
            it.x = v.x
            it.y = v.y
            it.z = v.z
        }
        return shape
    }

    /**
     * 旋转到对应的yaw和pitch 上
     */
    fun rotatePointsToWithAngle(
        shape: List<RelativeLocation>,
        to: RelativeLocation,
        axis: RelativeLocation
    ): List<RelativeLocation> {
        val toYaw = getYawFromLocation(to)
        val toPitch = getPitchFromLocation(to)
        return rotatePointsToWithAngle(shape, toYaw, toPitch, axis)
    }


    /**
     * 让图形的对称轴指向某个点(图形跟着转变)
     */
    fun rotatePointsToPoint(
        locList: List<RelativeLocation>,
        origin: Vec3d,
        toPoint: Vec3d,
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
     * @param yaw 输入弧度制yaw
     */
    fun toMinecraftYaw(yaw: Double): Double = yaw - PI / 2

    fun getYawFromLocation(loc: Vec3d): Double {
        return atan2(loc.z, loc.x)
    }

    fun getYawFromLocation(loc: RelativeLocation): Double {
        return atan2(loc.z, loc.x)
    }

    fun getPitchFromLocation(v: RelativeLocation): Double {
        val length = v.length()
        if (length == 0.0) return 0.0
        return asin(v.y / length)
    }

    fun getPitchFromLocation(v: Vec3d): Double {
        val length = v.length()
        if (length == 0.0) return 0.0
        return asin(v.y / length)
    }

    /**
     * 获取在start-end线段内的count个点集合
     */
    fun getLineLocations(start: Vec3d, end: Vec3d, count: Int): List<RelativeLocation> {
        val origin = RelativeLocation.of(start)
        val res = mutableListOf(origin, RelativeLocation.of(end))
        val step = start.distanceTo(end) / count
        val direction = start.relativize(end).normalize().multiply(step)
        val relativeDirection = RelativeLocation.of(direction)
        var next = origin
        for (i in 2..count) {
            val pos = next + relativeDirection
            next = pos.clone()
            res.add(next)
        }
        return res
    }

    fun getLineLocations(start: RelativeLocation, end: RelativeLocation, count: Int): List<RelativeLocation> {
        return getLineLocations(start.toVector(), end.toVector(), count)
    }

    /**
     * 获取 从origin 向 direction方向的射线上 每个间距为 step 且总数量为count的点集合
     */
    fun getLineLocations(origin: Vec3d, direction: Vec3d, step: Double, count: Int): List<RelativeLocation> {
        val originRel = RelativeLocation.of(origin)
        val res = mutableListOf(originRel)
        val relativeDirection =
            RelativeLocation.of(Vec3d(direction.x, direction.y, direction.z).normalize().multiply(step))
        var next = originRel
        for (i in 2..count) {
            val pos = next + relativeDirection
            next = pos.clone()
            res.add(next)
        }
        return res
    }

    /**
     * 获取圆面
     * 圆面在XZ上
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
     * 获取圆面
     * 圆面在XZ上
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

    /**
     * 生成三次贝塞尔曲线 (二维)
     */
    fun generateBezierCurve(
        target: RelativeLocation,
        /**
         * 起点的曲柄向量
         */
        startHandle: RelativeLocation,
        /**
         * 终点的曲柄向量 (以 target为原点)
         * 在Pr ae的速度,值曲线的下一个关键帧曲柄中
         * 方向和startHandle相反
         * 因此这里也要相反
         */
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
     * 生成爆炸曲线点
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

    /**
     * 旋转是通过旋转x/z 轴来坐标值的
     * 由于sqrt pow 是恒大于0的值因此不能用于坐标求值
     */
    private fun getAxisSymbol(loc: Vec3d): Int {
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


}