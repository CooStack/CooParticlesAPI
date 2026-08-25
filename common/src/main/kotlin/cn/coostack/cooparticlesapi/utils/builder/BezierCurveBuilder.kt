package cn.coostack.cooparticlesapi.utils.builder

import cn.coostack.cooparticlesapi.utils.BezierNode
import cn.coostack.cooparticlesapi.utils.RelativeLocation

/**
 * 空间贝塞尔曲线节点构建器。
 *
 * 按调用顺序保存节点，可直接传给 [PointsBuilder.addBezierCurve] 生成曲线点集。
 */
class BezierCurveBuilder {
    private val nodes = ArrayList<BezierNode>()

    /**
     * 添加一个曲线节点。
     *
     * @param point 节点位置
     * @param startHandle 从当前节点指向下一段曲线的出射控制柄相对偏移
     * @param endHandle 从上一段曲线指向当前节点的入射控制柄相对偏移
     * @return 当前构建器
     */
    fun addNode(
        point: RelativeLocation,
        startHandle: RelativeLocation = RelativeLocation(),
        endHandle: RelativeLocation = RelativeLocation()
    ): BezierCurveBuilder {
        nodes += BezierNode(point.clone(), startHandle.clone(), endHandle.clone())
        return this
    }

    /**
     * 创建当前节点列表的副本。
     *
     * @return 按添加顺序排列的贝塞尔节点
     */
    fun build(): List<BezierNode> = nodes.map {
        BezierNode(it.point.clone(), it.startHandle.clone(), it.endHandle.clone())
    }
}
