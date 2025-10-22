package cn.coostack.cooparticlesapi.utils.interpolator.particle

import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.utils.CircularQueue
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.interpolator.Interpolator
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 适用于固定半径的圆周运动的粒子插值
 *
 * 需要输入半径 圆心坐标获取器
 */
class CircleParticleInterpolator(val originProvider: Supplier<RelativeLocation>) : Interpolator {
    override var refinerCount: Double = 2.0
    private var limit = 256.0
    private val queue = CircularQueue<RelativeLocation>(2)

    private var rotater: CircleParticleInterpolator.(List<RelativeLocation>) -> Unit = {}
    private var rotaterBack: CircleParticleInterpolator.(List<RelativeLocation>) -> Unit = {}

    override fun insertPoint(vec: Vec3): CircleParticleInterpolator {
        insertPoint(vec.asRelative())
        return this
    }

    override fun insertPoint(vec: Vector3f): CircleParticleInterpolator {
        insertPoint(vec.asRelative())
        return this
    }

    override fun insertPoint(vec: RelativeLocation): CircleParticleInterpolator {
        val origin = originProvider.get()
        if ((vec - origin).length() <= 1e-6) return this
        queue.addFirst(vec)
        return this
    }


    override fun setLimit(limit: Double): CircleParticleInterpolator {
        this.limit = limit
        return this
    }

    override fun setRefiner(refiner: Double): CircleParticleInterpolator {
        this.refinerCount = refiner
        return this
    }

    /**
     * 可能这个圆不是在XZ平面上的
     * 需要调用旋转
     *
     * ```
     * // 让圆面朝着目标方向 (垂直于to)
     * Math3DUtil.rotatePointsToPoint(it,to,RelativeLocation.yAxis())
     * // 绕着axis进行旋转rad角度 (一般用于roll旋转)
     * Math3DUtil.rotateAsAxis(it,axis,rad)
     * ```
     */
    fun rotater(rotater: CircleParticleInterpolator.(List<RelativeLocation>) -> Unit): CircleParticleInterpolator {
        this.rotater = rotater
        return this
    }

    /**
     * 如果你的圆不是在xz平面上 （没有被y轴垂直）
     * 那么就需要实现这个方法 使用数学运算将你的圆映射到XZ平面上
     *
     * ```
     * Math3DUtil.rotatePointsToPoint(it,RelativeLocation.yAxis(),圆所在平面的法向量)
     * ```
     */
    fun rotaterBack(rotater: CircleParticleInterpolator.(List<RelativeLocation>) -> Unit): CircleParticleInterpolator {
        this.rotaterBack = rotater
        return this
    }

    override fun getRefinedResult(): List<RelativeLocation> {
        if (queue.empty()) {
            return arrayListOf()
        }

        if (queue.notNullSize() == 1) {
            return arrayListOf(queue[0])
        }

        if (queue[0].distance(queue[1]) > limit) {
            return arrayListOf(queue[1])
        }
        val origin = originProvider.get()
        // 计算出相对圆radius

        val relPos1 = queue[0] - origin
        val relPos2 = queue[1] - origin
        rotaterBack(listOf(relPos1, relPos2))

        // 这个时候计算radius r
        val r = max(relPos1.length(), relPos2.length())
        val rad1 = acos(relPos1.x / r)
        val rad2 = acos(relPos2.x / r)
        // r1 .. r2 插值
        // 计算弧度差
        val dr = abs(rad1 - rad2)
        val count = (dr * refinerCount).roundToInt().coerceAtLeast(1)
        val res = arrayListOf<RelativeLocation>()
        repeat(count + 1) {
            val progress = GraphMathHelper.lerp(it / count.toDouble(), rad1, rad2)

            val x = cos(progress) * r
            val z = sin(progress) * r
            res.add(RelativeLocation(x, 0.0, z))
        }
        rotater(res)
        return res
    }
}