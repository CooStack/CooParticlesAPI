package cn.coostack.cooparticlesapi.animation.motion

import cn.coostack.cooparticlesapi.animation.timeline.Ease
import cn.coostack.cooparticlesapi.animation.timeline.Eases
import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import net.minecraft.world.phys.Vec3

/**
 * 函数式路径运动。
 *
 * 适合贝塞尔、圆形、螺旋、周期函数这类通过进度直接计算位置的运动。
 */
class FunctionPathMotion<T : ServerControler<*>> : AbstractPathMotion<T>() {
    /**
     * 总持续tick数
     */
    var durationTick = 20

    /**
     * 进度缓动函数
     */
    var ease: Ease = Eases.linear

    /**
     * 进度到位置的映射
     *
     * 入参范围通常是0.0..1.0。
     */
    var path: (Double) -> Vec3 = { Vec3.ZERO }

    override fun nextPosition(controler: T): Vec3? {
        if (durationTick <= 0) {
            return null
        }
        if (tickCount >= durationTick) {
            return null
        }

        val progress = if (durationTick == 1) {
            1.0
        } else {
            tickCount.toDouble() / (durationTick - 1).toDouble()
        }
        val eased = ease.cal(progress).coerceIn(0.0, 1.0)
        return path(eased)
    }
}
