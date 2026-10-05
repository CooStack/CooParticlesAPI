package cn.coostack.cooparticlesapi.utils.interpolator

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * 点插值器
 * 使用队列结构
 * 队列长度为2
 * 使用驱逐队列实现
 */
interface Interpolator {
    /**
     * 直接遍历插值结果，避免调用方为每个采样点创建结果列表。
     *
     * 默认实现保持旧自定义插值器的兼容性；线性发射器会覆盖此方法，直接用标量
     * 公式回调坐标。回调参数为 x、y、z、当前索引和总点数。
     */
    fun forEachRefined(consumer: RefinedPointConsumer) {
        val result = getRefinedResult()
        val count = result.size
        result.forEachIndexed { index, point ->
            consumer.accept(point.x, point.y, point.z, index, count)
        }
    }

    /**
     * 细分程度
     */
    val refinerCount: Double

    /**
     * 输入不可变的向量坐标点
     */
    fun insertPoint(vec: Vec3): Interpolator
    fun insertPoint(vec: Vector3f): Interpolator
    fun insertPoint(vec: RelativeLocation): Interpolator

    fun setLimit(limit: Double): Interpolator

    /**
     * 细分器
     * @param refiner 细分程度
     * 用于细分2点(队列内)之间点的个数
     */
    fun setRefiner(refiner: Double): Interpolator

    /**
     * 获取细分结果
     * @return empty list if queue is empty
     * @return array list size 1 if queue.size() == 1
     * @return normal if queue.size() >= 2
     */
    fun getRefinedResult(): List<RelativeLocation>

}

/** 插值采样点回调；坐标使用 Double 以保持旧插值器的精度语义。 */
fun interface RefinedPointConsumer {
    fun accept(x: Double, y: Double, z: Double, index: Int, count: Int)
}
