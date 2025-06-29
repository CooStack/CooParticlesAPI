package cn.coostack.cooparticlesapi.network.animation.api

import net.minecraft.util.math.Vec3d

/**
 * 方便设置各种路径运动的基类
 * 支持书写旋转表达式, 位移表达式(偏移)
 * 提供以 origin为起点的偏移量
 *
 * 其中 origin是路径的原点(可以通过teleport进行修改)
 *
 * 此类修改路径的原理如下 (直接修改目标类(Style 或者 Group 或者 Emitter) 的pos属性)
 */
interface PathMotion {
    /**
     * 从开始运动时计次,作为时间单位使用
     * 每执行一次 next方法时, 都会递增一次此类
     */
    var currentTick: Int

    /**
     * 目标原点
     */
    var origin: Vec3d

    /**
     * 应用目标路径
     */
    fun apply(actualPos: Vec3d)


    fun next(): Vec3d

    /**
     * 获取被控制器的有效性
     */
    fun checkValid(): Boolean
}