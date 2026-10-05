package cn.coostack.cooparticlesapi.utils.interpolator.emitters

import cn.coostack.cooparticlesapi.utils.CircularQueue
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.interpolator.Interpolator
import cn.coostack.cooparticlesapi.utils.interpolator.RefinedPointConsumer
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

    /**
     * 线性插值热路径：不创建中间 List/RelativeLocation，直接回调每个采样点。
     * 点序和 [getRefinedResult] 完全一致（当前点、内部点、上一点）。
     */
    override fun forEachRefined(consumer: RefinedPointConsumer) {
        if (queue.empty()) return
        if (queue.notNullSize() == 1) {
            val point = queue[0]
            consumer.accept(point.x, point.y, point.z, 0, 1)
            return
        }

        val start = queue[0]
        val end = queue[1]
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        if (distance > limit) {
            // 与 getRefinedResult 一致：传送跨度过大时只保留上一点，避免拉出长尾。
            consumer.accept(end.x, end.y, end.z, 0, 1)
            return
        }

        // Math3DUtil.fillLine 使用 roundToInt 后生成 count + 1 个点。
        // count=0 时旧实现仍返回起点和终点，因此这里保留至少两个采样点。
        val segments = (distance * refinerCount).roundToInt()
        val safeSegments = segments.coerceAtLeast(1)
        val count = safeSegments + 1
        consumer.accept(start.x, start.y, start.z, 0, count)
        for (index in 1 until safeSegments) {
            val progress = index.toDouble() / safeSegments.toDouble()
            consumer.accept(
                start.x + dx * progress,
                start.y + dy * progress,
                start.z + dz * progress,
                index,
                count,
            )
        }
        consumer.accept(end.x, end.y, end.z, safeSegments, count)
    }
}
