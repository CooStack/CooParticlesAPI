package cn.coostack.cooparticlesapi.utils.interpolator

import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.utils.CircularQueue
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * 线段插值器
 * 由于粒子在1tick内的移动速度可能比粒子大
 * 导致在进行轨迹制作时会出现一些粒子空缺导致不美观
 *
 * 适合固定发射器, 然后粒子进行移动时使用
 * 请在tick调用 putParticleArgs 用来设置粒子生成
 * 然后在genParticles调用getRefinedResult 获取粒子生成的结果
 * @see putParticleArgs
 */
class DirectInterpolator : Interpolator {
    val queue = CircularQueue<RelativeLocation>(2)

    /**
     * 细分程度
     * 可以理解为 1个单位长度下填充的粒子个数
     * lineTotalParticleCount = dis * refinerCount
     */
    var refinerCount = 1.0
    override fun insertPoint(vec: Vec3): DirectInterpolator {
        queue.addFirst(RelativeLocation.of(vec))
        return this
    }

    override fun insertPoint(vec: Vector3f): DirectInterpolator {
        queue.addFirst(RelativeLocation(vec.x, vec.y, vec.z))
        return this
    }

    override fun insertPoint(vec: RelativeLocation): DirectInterpolator {
        queue.addFirst(vec.clone())
        return this
    }

    override fun setRefiner(refiner: Double): DirectInterpolator {
        refinerCount = refiner.coerceAtLeast(0.001)
        return this
    }

    /**
     * 设置一个粒子
     * @param pos 一般输入为发射器的位置
     * @param dir 粒子移动方向
     * @param speed 粒子移动速度
     */
    fun putParticleArgs(pos: Vec3, dir: Vec3, speed: Double): DirectInterpolator {
        insertPoint(pos)
        insertPoint(pos + dir.normalize().scale(speed))
        return this
    }

    override fun getRefinedResult(): List<RelativeLocation> {
        if (queue.empty()) {
            return arrayListOf()
        }

        if (queue.notNullSize() == 1) {
            return arrayListOf(queue[0])
        }
        return Math3DUtil.fillLine(queue[0], queue[1], refinerCount)
    }
}