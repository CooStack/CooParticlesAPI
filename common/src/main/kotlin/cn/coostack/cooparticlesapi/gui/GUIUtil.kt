package cn.coostack.cooparticlesapi.gui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.phys.Vec2

/**
 * GUI 布局工具 —— 居中/居左/居右对齐, 以及"缩放后位置不变"的 DSL。
 *
 * 核心痛点: 玩家的分辨率和 GUI 缩放各不相同, 写死坐标在别人电脑上就会跑偏。
 * 这个工具让你只描述"贴哪个锚点 + 偏移多少", 真实坐标交给它算。
 */
object GUIUtil {

    /**
     * 根据锚点 + 偏移, 算出元素左上角的真实 X 坐标。
     *
     * @param screenWidth 当前屏幕逻辑宽度
     * @param elementWidth 元素自身宽度
     * @param anchor 贴左 / 居中 / 贴右
     * @param offset 在锚点基础上的额外偏移(正数向右)
     */
    fun alignX(screenWidth: Int, elementWidth: Int, anchor: AnchorX, offset: Int = 0): Int {
        val base = when (anchor) {
            AnchorX.LEFT -> 0
            AnchorX.CENTER -> (screenWidth - elementWidth) / 2
            AnchorX.RIGHT -> screenWidth - elementWidth
        }
        return base + offset
    }

    /**
     * 根据锚点 + 偏移, 算出元素左上角的真实 Y 坐标。
     *
     * @param screenHeight 当前屏幕逻辑高度
     * @param elementHeight 元素自身高度
     * @param anchor 贴顶 / 居中 / 贴底
     * @param offset 在锚点基础上的额外偏移(正数向下)
     */
    fun alignY(screenHeight: Int, elementHeight: Int, anchor: AnchorY, offset: Int = 0): Int {
        val base = when (anchor) {
            AnchorY.TOP -> 0
            AnchorY.CENTER -> (screenHeight - elementHeight) / 2
            AnchorY.BOTTOM -> screenHeight - elementHeight
        }
        return base + offset
    }

    /**
     * 一步算出元素左上角坐标(同时处理 X 和 Y)。
     *
     * 例: 把一个 80x10 的条放在屏幕正中:
     *     val pos = GUIUtil.align(w, h, 80, 10, AnchorX.CENTER, AnchorY.CENTER)
     */
    fun align(
        screenWidth: Int,
        screenHeight: Int,
        elementWidth: Int,
        elementHeight: Int,
        anchorX: AnchorX,
        anchorY: AnchorY,
        offsetX: Int = 0,
        offsetY: Int = 0
    ): Vec2 {
        return Vec2(
            alignX(screenWidth, elementWidth, anchorX, offsetX).toFloat(),
            alignY(screenHeight, elementHeight, anchorY, offsetY).toFloat()
        )
    }

    // =====================================================================
    // placeLayout —— "缩放后, 元素在屏幕中的位置不变" 的 DSL
    // =====================================================================

    /**
     * 布局作用域: 在 [placeLayout] 的代码块里, 你拿到的就是这个对象。
     * 它已经把"平移到锚点"和"缩放"处理好了, 你只管从 (0, 0) 开始画。
     *
     * @property graphics 当前的 GuiGraphics, 直接用它画图
     * @property scale 实际生效的缩放倍率
     */
    class LayoutScope(
        val graphics: GuiGraphics,
        val scale: Float
    ) {
        /**
         * 在缩放坐标系里, 元素应该从这个原点开始画。
         * 永远从 (0, 0) 画, 因为平移已经由 placeLayout 做好了。
         */
        val originX: Int = 0
        val originY: Int = 0
    }

    /**
     * 把一段绘制逻辑"钉"在屏幕的某个锚点上, 并按需缩放, 让它**无论玩家怎么调 GUI 缩放,
     * 在屏幕上的位置和大小都保持稳定**。
     *
     * 【为什么能做到位置不变】
     * 真实坐标是用"当前屏幕尺寸 + 锚点"实时算出来的(见 alignX/alignY), 而不是写死。
     * 屏幕变大变小, 锚点跟着变, 元素自然贴在该贴的地方。
     *
     * 【缩放的正确姿势 (重点)】
     * 顺序必须是: 先平移到锚点 -> 再缩放 -> 然后从 (0,0) 画。
     * 如果反过来(先缩放再用真实坐标画), 你的坐标会被缩放倍率污染, 位置就乱了。
     * 这个方法已经帮你按正确顺序处理好了。
     *
     * 【用法示例】
     * ```
     * // 把魔力条钉在饥饿条上方(右半边、底部对齐), 放大 1.5 倍, 缩放后位置不变
     * val hunger = HudAnchors.hungerTopLeft()
     * GUIUtil.placeLayout(graphics, hunger.x, hunger.y - 12, scale = 1.5f) {
     *     // 这里直接从 (0,0) 画, 不用管缩放
     *     graphics.blit(MANA_TEXTURE, originX, originY, 0, 0, 80, 8)
     * }
     * ```
     *
     * @param graphics 当前 GuiGraphics
     * @param x 元素左上角的目标 X(逻辑坐标, 可直接用 HudAnchors 的值)
     * @param y 元素左上角的目标 Y
     * @param scale 缩放倍率, 1f 表示不缩放
     * @param block 绘制逻辑, 在里面从 (originX, originY) 即 (0,0) 开始画
     */
    inline fun placeLayout(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        scale: Float = 1f,
        block: LayoutScope.() -> Unit
    ) {
        val pose = graphics.pose()
        pose.pushPose()
        // 1. 先平移到锚点(此时还是干净的逻辑坐标)
        pose.translate(x.toFloat(), y.toFloat(), 0f)
        // 2. 再缩放(只影响后续从原点画出的内容大小, 不污染定位)
        if (scale != 1f) {
            pose.scale(scale, scale, 1f)
        }
        // 3. 交给用户, 让它从 (0,0) 画
        LayoutScope(graphics, scale).block()
        pose.popPose()
    }

    /**
     * placeLayout 的锚点版: 直接用锚点 + 偏移定位, 内部自动算真实坐标。
     * 适合"始终贴右下角"这种需求, 连坐标都不用自己算。
     *
     * @param graphics 当前 GuiGraphics
     * @param elementWidth 元素宽度(用于居中/居右计算)
     * @param elementHeight 元素高度
     * @param anchorX 水平锚点
     * @param anchorY 垂直锚点
     * @param offsetX 额外横向偏移
     * @param offsetY 额外纵向偏移
     * @param scale 缩放倍率
     * @param block 绘制逻辑, 从 (0,0) 开始画
     */
    inline fun placeLayout(
        graphics: GuiGraphics,
        elementWidth: Int,
        elementHeight: Int,
        anchorX: AnchorX,
        anchorY: AnchorY,
        offsetX: Int = 0,
        offsetY: Int = 0,
        scale: Float = 1f,
        block: LayoutScope.() -> Unit
    ) {
        val w = graphics.guiWidth()
        val h = graphics.guiHeight()
        val px = alignX(w, elementWidth, anchorX, offsetX)
        val py = alignY(h, elementHeight, anchorY, offsetY)
        placeLayout(graphics, px, py, scale, block)
    }
}
