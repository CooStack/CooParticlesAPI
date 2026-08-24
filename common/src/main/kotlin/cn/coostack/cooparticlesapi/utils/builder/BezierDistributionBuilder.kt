package cn.coostack.cooparticlesapi.utils.builder

import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.BezierNode
import cn.coostack.cooparticlesapi.utils.RelativeLocation

/**
 * 空间三次贝塞尔曲线分布构建器。
 *
 * 每个节点包含一个曲线位置，以及从该位置出发和进入该位置的控制柄。调用
 * [applyBuilder]、[applyPoint] 或 [applyPoints] 设置的点会复制到曲线的每个采样位置。
 * 未设置曲线节点但设置了点集时，路径位置按原点处理；未设置点集时，构建结果为空。
 */
class BezierDistributionBuilder {
    private val nodes = ArrayList<BezierNode>()
    private var appliedPoints: List<RelativeLocation>? = null
    private var requestedCount: Int? = null

    /**
     * 添加一个曲线节点。
     *
     * [startHandle] 是从当前节点指向下一段曲线的控制柄，[endHandle] 是从上一段曲线
     * 指向当前节点的控制柄。首尾节点未使用的控制柄可以保持默认值。
     *
     * @param point 当前节点位置
     * @param startHandle 当前节点的出射控制柄相对偏移
     * @param endHandle 当前节点的入射控制柄相对偏移
     * @return 当前构建器
     */
    fun addNode(
        point: RelativeLocation = RelativeLocation(),
        startHandle: RelativeLocation = RelativeLocation(),
        endHandle: RelativeLocation = RelativeLocation()
    ): BezierDistributionBuilder {
        nodes += BezierNode(point.clone(), startHandle.clone(), endHandle.clone())
        return this
    }

    /**
     * 设置曲线采样点数量。
     *
     * @param count 返回曲线位置数量，至少为 1；未设置时默认使用 16 个位置
     * @return 当前构建器
     */
    fun count(count: Int): BezierDistributionBuilder {
        require(count >= 1) { "Number of points must be at least 1" }
        requestedCount = count
        return this
    }

    /**
     * 设置要沿曲线放置的点集。
     *
     * @param builder 点集构建器，调用时会复制其当前快照
     * @return 当前构建器
     */
    fun applyBuilder(builder: PointsBuilder): BezierDistributionBuilder =
        applyPoints(builder.create())

    /**
     * 设置一个要沿曲线放置的点。
     *
     * @param point 要复制到每个曲线采样位置的点
     * @return 当前构建器
     */
    fun applyPoint(point: RelativeLocation): BezierDistributionBuilder =
        applyPoints(listOf(point))

    /**
     * 设置要沿曲线放置的点集快照。
     *
     * @param points 点集；调用时会复制集合及其中的坐标对象
     * @return 当前构建器
     */
    fun applyPoints(points: Collection<RelativeLocation>): BezierDistributionBuilder {
        appliedPoints = points.map { it.clone() }
        return this
    }

    /**
     * 构建沿曲线复制放置后的点集。
     *
     * @return 复制放置后的点集；没有源点时返回空集合
     */
    fun build(): List<RelativeLocation> {
        val sourcePoints = appliedPoints ?: emptyList()
        if (sourcePoints.isEmpty()) {
            return emptyList()
        }

        val path = createPath()
        return path.flatMap { pathPoint ->
            sourcePoints.map { sourcePoint -> sourcePoint + pathPoint }
        }
    }

    private fun createPath(): List<RelativeLocation> {
        if (nodes.isEmpty()) {
            return listOf(RelativeLocation())
        }
        if (nodes.size == 1) {
            return listOf(nodes[0].point.clone())
        }

        val count = requestedCount ?: 16
        return Math3DUtil.generateBezierCurve(nodes, count)
    }
}
