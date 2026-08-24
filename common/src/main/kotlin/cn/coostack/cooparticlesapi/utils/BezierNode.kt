package cn.coostack.cooparticlesapi.utils

/**
 * 空间贝塞尔曲线节点。
 *
 * @param point 节点位置
 * @param startHandle 从当前节点指向下一段曲线的出射控制柄相对偏移
 * @param endHandle 从上一段曲线指向当前节点的入射控制柄相对偏移
 */
data class BezierNode(
    val point: RelativeLocation,
    val startHandle: RelativeLocation = RelativeLocation(),
    val endHandle: RelativeLocation = RelativeLocation()
)
