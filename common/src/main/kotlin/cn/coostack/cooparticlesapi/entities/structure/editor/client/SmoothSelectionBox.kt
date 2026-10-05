package cn.coostack.cooparticlesapi.entities.structure.editor.client

import net.minecraft.world.phys.AABB
import kotlin.math.exp

/** 只平滑显示边界，不把小数坐标写回逻辑选区；使用指数趋近消除帧率对动画速度的影响。 */
internal class SmoothSelectionBox {
    /** 上一帧显示的边界，目标消失后清空，首次出现直接定位。 */
    private var current: AABB? = null

    /**
     * 推进一帧线框过渡。
     * @param target 精确方块边界；空值表示隐藏并清理动画
     * @param seconds 本帧经过的秒数，由调用方限制长时间暂停产生的突跳
     * @return 当前应绘制的小数边界，隐藏时为空
     */
    fun advance(target: AABB?, seconds: Double): AABB? {
        if (target == null) { current = null; return null }
        val old = current
        if (old == null) { current = target; return target }
        // 约 80 毫秒的时间常数使快速连点和移动准星保持连续，又不引入长期拖尾。
        val alpha = 1.0 - exp(-seconds.coerceAtLeast(0.0) / 0.08)
        val result = AABB(
            old.minX + (target.minX - old.minX) * alpha,
            old.minY + (target.minY - old.minY) * alpha,
            old.minZ + (target.minZ - old.minZ) * alpha,
            old.maxX + (target.maxX - old.maxX) * alpha,
            old.maxY + (target.maxY - old.maxY) * alpha,
            old.maxZ + (target.maxZ - old.maxZ) * alpha
        )
        current = result
        return result
    }
}
