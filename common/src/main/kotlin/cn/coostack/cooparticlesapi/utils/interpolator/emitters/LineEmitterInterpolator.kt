package cn.coostack.cooparticlesapi.utils.interpolator.emitters

import cn.coostack.cooparticlesapi.utils.CircularQueue
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.interpolator.Interpolator
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * 线段插值器
 * 由于粒子在1tick内的移动速度可能比粒子大
 * 导致在进行轨迹制作时会出现一些粒子空缺导致不美观
 *
 * 适合在粒子发射器本身进行移动时使用
 */
class LineEmitterInterpolator : Interpolator {
    /**
     * 为了防止超远距离的 "传送" 导致超级长的粒子条
     * 设置一个上限可以防止出现这种问题
     */
    private var limit = 256.0
    private val queue = CircularQueue<RelativeLocation>(2)

    /**
     * 细分程度
     * 可以理解为 1个单位长度下填充的粒子个数
     * lineTotalParticleCount = dis * refinerCount
     */
    override var refinerCount: Double = 1.0
    override fun insertPoint(vec: Vec3): LineEmitterInterpolator {
        queue.addFirst(RelativeLocation.Companion.of(vec))
        return this
    }

    override fun insertPoint(vec: Vector3f): LineEmitterInterpolator {
        queue.addFirst(RelativeLocation(vec.x, vec.y, vec.z))
        return this
    }

    override fun insertPoint(vec: RelativeLocation): LineEmitterInterpolator {
        queue.addFirst(vec.clone())
        return this
    }

    override fun setLimit(limit: Double): LineEmitterInterpolator {
        this.limit = limit
        return this
    }

    override fun setRefiner(refiner: Double): LineEmitterInterpolator {
        refinerCount = refiner.coerceAtLeast(0.001)
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
        return Math3DUtil.fillLine(queue[0], queue[1], refinerCount)
    }
}