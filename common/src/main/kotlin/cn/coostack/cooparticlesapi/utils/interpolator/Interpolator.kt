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