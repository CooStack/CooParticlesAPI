package cn.coostack.cooparticlesapi.gui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.phys.Vec2
import kotlin.math.abs

/**
 * 吸附工具 —— 拖动 UI 元素时, 自动对齐到附近的参考线(屏幕中线、屏幕边缘、其它元素的边等)。
 *
 * 这是"拖拽编辑 HUD"体验里最关键的一环: 当被拖动元素的某条边离某条参考线足够近时,
 * 就把它"钉"到那条线上, 同时画一条高亮线给玩家视觉反馈。
 *
 * 【典型用法】
 * 1. 在拖动开始/每帧, 用 [buildScreenGuides] 收集屏幕中线和边缘等参考线。
 * 2. 拖动时把当前坐标丢给 [snap], 拿回吸附后的坐标。
 * 3. 渲染时把 [snap] 返回的命中参考线用 [drawGuide] 画出来。
 */
object SnapHelper {

    /** 默认吸附阈值(逻辑像素): 元素边离参考线小于这个距离就吸附 */
    const val DEFAULT_THRESHOLD = 4

    /**
     * 一条吸附参考线。
     * @property pos 参考线在对应轴上的坐标值
     * @property vertical true = 竖直线(约束 X), false = 水平线(约束 Y)
     */
    data class Guide(val pos: Int, val vertical: Boolean)

    /**
     * 吸附结果。
     * @property value 吸附后的坐标(若没吸附则原样返回)
     * @property hit 命中的参考线; null 表示这一轴没有发生吸附
     */
    data class SnapResult(val value: Int, val hit: Guide?)

    /**
     * 收集"屏幕级"的常用参考线: 水平中线、垂直中线、四条边缘。
     * 这是拖拽编辑时最常用的一组吸附目标。
     *
     * @param screenWidth 屏幕逻辑宽度
     * @param screenHeight 屏幕逻辑高度
     */
    fun buildScreenGuides(screenWidth: Int, screenHeight: Int): List<Guide> {
        return listOf(
            Guide(0, vertical = true),                    // 左边缘
            Guide(screenWidth / 2, vertical = true),      // 垂直中线
            Guide(screenWidth, vertical = true),          // 右边缘
            Guide(0, vertical = false),                   // 顶边缘
            Guide(screenHeight / 2, vertical = false),    // 水平中线
            Guide(screenHeight, vertical = false)         // 底边缘
        )
    }

    /**
     * 把一个候选坐标值, 吸附到一组参考线中最近的那条(若在阈值内)。
     *
     * 一般你会对元素的左边和右边各调一次(或上边/下边), 取先命中的那个。
     *
     * @param value 当前候选坐标(比如元素左边的 x, 或顶边的 y)
     * @param guides 参考线集合
     * @param vertical 当前要吸附的是竖直线(true)还是水平线(false)
     * @param threshold 吸附阈值, 默认 [DEFAULT_THRESHOLD]
     * @return 吸附后的坐标 + 命中的参考线
     */
    fun snap(
        value: Int,
        guides: List<Guide>,
        vertical: Boolean,
        threshold: Int = DEFAULT_THRESHOLD
    ): SnapResult {
        var best: Guide? = null
        var bestDist = threshold + 1
        for (guide in guides) {
            if (guide.vertical != vertical) continue
            val dist = abs(guide.pos - value)
            if (dist <= threshold && dist < bestDist) {
                bestDist = dist
                best = guide
            }
        }
        return if (best != null) SnapResult(best.pos, best) else SnapResult(value, null)
    }

    /**
     * 同时对元素的 X 和 Y 做吸附。会优先尝试用"左边/上边"对齐,
     * 也会尝试用"右边/下边"对齐(右边吸附后换算回左上角坐标), 取更近的。
     *
     * @param left 元素左上角 X
     * @param top 元素左上角 Y
     * @param width 元素宽
     * @param height 元素高
     * @param guides 参考线集合
     * @param threshold 吸附阈值
     * @return Pair(吸附后的左上角坐标, 命中的参考线列表[可能 0~2 条])
     */
    fun snapBox(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        guides: List<Guide>,
        threshold: Int = DEFAULT_THRESHOLD
    ): Pair<Vec2, List<Guide>> {
        val hits = ArrayList<Guide>(2)

        // --- X 轴: 比较左边、中心、右边, 谁先吸附用谁 ---
        var resultLeft = left
        val leftSnap = snap(left, guides, vertical = true, threshold)
        val centerSnap = snap(left + width / 2, guides, vertical = true, threshold)
        val rightSnap = snap(left + width, guides, vertical = true, threshold)
        when {
            leftSnap.hit != null -> {
                resultLeft = leftSnap.value
                hits.add(leftSnap.hit)
            }
            centerSnap.hit != null -> {
                resultLeft = centerSnap.value - width / 2
                hits.add(centerSnap.hit)
            }
            rightSnap.hit != null -> {
                resultLeft = rightSnap.value - width
                hits.add(rightSnap.hit)
            }
        }

        // --- Y 轴: 比较上边、中心、下边 ---
        var resultTop = top
        val topSnap = snap(top, guides, vertical = false, threshold)
        val midSnap = snap(top + height / 2, guides, vertical = false, threshold)
        val bottomSnap = snap(top + height, guides, vertical = false, threshold)
        when {
            topSnap.hit != null -> {
                resultTop = topSnap.value
                hits.add(topSnap.hit)
            }
            midSnap.hit != null -> {
                resultTop = midSnap.value - height / 2
                hits.add(midSnap.hit)
            }
            bottomSnap.hit != null -> {
                resultTop = bottomSnap.value - height
                hits.add(bottomSnap.hit)
            }
        }

        return Vec2(resultLeft.toFloat(), resultTop.toFloat()) to hits
    }

    /**
     * 画出一条吸附参考线(贯穿整个屏幕), 用于拖拽时的视觉提示。
     *
     * @param graphics 当前 GuiGraphics
     * @param guide 要画的参考线
     * @param color 线颜色(ARGB), 默认半透明青色
     */
    fun drawGuide(graphics: GuiGraphics, guide: Guide, color: Int = 0xAA00E5FFu.toInt()) {
        val w = graphics.guiWidth()
        val h = graphics.guiHeight()
        if (guide.vertical) {
            graphics.fill(guide.pos, 0, guide.pos + 1, h, color)
        } else {
            graphics.fill(0, guide.pos, w, guide.pos + 1, color)
        }
    }
}
