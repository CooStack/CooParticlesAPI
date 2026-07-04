package cn.coostack.cooparticlesapi.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.phys.Vec2

/**
 * 原版 HUD 坐标速查工具。
 *
 * 【为什么需要它】
 * 原版的血条、饥饿条、经验条、物品栏的坐标全是写死在 Gui.java 内部的局部变量,
 * Minecraft 不对外暴露任何 "获取饥饿条坐标" 的 API。
 * 所以唯一可靠的办法, 就是照抄原版的坐标公式 —— 这个 object 就是把那些公式封装好,
 * 让你一行代码拿到任意一栏的坐标。
 *
 * 【坐标系说明】
 * 这里返回的全部是 "GUI 逻辑坐标"(已经过 GUI Scale 换算), 也就是 GuiGraphics 里直接能用的坐标。
 * 你拿到坐标后直接 blit/drawString 即可, 不需要自己再乘 guiScale。
 *
 * 【数字怎么来的 (1.21.1)】
 * - 整条状态栏(物品栏)宽 182, 水平居中, 所以左右各占 91。
 * - 物品栏底边贴屏幕底部, 高 22。
 * - 经验条在物品栏上方一点。
 * - 血条(左半) / 饥饿条(右半) 在经验条上方, 基线大约 屏幕底 - 39。
 */
object HudAnchors {

    /** 状态栏总宽度(物品栏宽度), 原版固定 182 */
    const val HOTBAR_WIDTH = 182

    /** 状态栏一半宽度, 用于左右分边 */
    const val HALF_WIDTH = HOTBAR_WIDTH / 2 // 91

    private val mc: Minecraft
        get() = Minecraft.getInstance()

    /** 当前 GUI 逻辑屏幕宽度 */
    fun screenWidth(): Int = mc.window.guiScaledWidth

    /** 当前 GUI 逻辑屏幕高度 */
    fun screenHeight(): Int = mc.window.guiScaledHeight

    /** 屏幕水平中心 X —— 一切 HUD 坐标的原点 */
    fun centerX(): Int = screenWidth() / 2

    /** 屏幕垂直中心 Y */
    fun centerY(): Int = screenHeight() / 2

    // -----------------------------------------------------------------
    // 坐标统一用 MC 自带的 Vec2 返回(注意 Vec2 的 x/y 是 Float)
    // -----------------------------------------------------------------

    /**
     * 物品栏(快捷栏)左下角坐标。
     * 物品栏整体: 左上角 (centerX - 91, 屏幕底 - 22), 尺寸 182 x 22。
     */
    fun hotbarBottomLeft(): Vec2 = Vec2((centerX() - HALF_WIDTH).toFloat(), screenHeight().toFloat())

    /**
     * 物品栏左上角坐标(更常用, 贴图通常从左上角画)。
     */
    fun hotbarTopLeft(): Vec2 = Vec2((centerX() - HALF_WIDTH).toFloat(), (screenHeight() - 22).toFloat())

    /**
     * 经验条左下角坐标。
     * 经验条: 左上角 (centerX - 91, 屏幕底 - 29), 尺寸 182 x 5。
     */
    fun experienceBottomLeft(): Vec2 = Vec2((centerX() - HALF_WIDTH).toFloat(), (screenHeight() - 29 + 5).toFloat())

    /** 经验条左上角坐标 */
    fun experienceTopLeft(): Vec2 = Vec2((centerX() - HALF_WIDTH).toFloat(), (screenHeight() - 29).toFloat())

    /**
     * 血条(生命值)左下角坐标。
     * 血条占状态栏左半边, 一行十颗心, 基线在 屏幕底 - 39, 每颗心高约 9。
     */
    fun healthBottomLeft(): Vec2 = Vec2((centerX() - HALF_WIDTH).toFloat(), (screenHeight() - 39 + 9).toFloat())

    /** 血条左上角坐标 */
    fun healthTopLeft(): Vec2 = Vec2((centerX() - HALF_WIDTH).toFloat(), (screenHeight() - 39).toFloat())

    /**
     * 饥饿条左下角坐标。
     * 饥饿条占状态栏右半边(从 centerX 到 centerX + 91), 与血条同一基线 屏幕底 - 39。
     * "左下角" = 右半边的最左侧、底部, 也就是 (centerX, 屏幕底 - 39 + 9)。
     */
    fun hungerBottomLeft(): Vec2 = Vec2(centerX().toFloat(), (screenHeight() - 39 + 9).toFloat())

    /** 饥饿条左上角坐标(右半边的左上角) */
    fun hungerTopLeft(): Vec2 = Vec2(centerX().toFloat(), (screenHeight() - 39).toFloat())

    /** 饥饿条右上角坐标(贴图右对齐时用) */
    fun hungerTopRight(): Vec2 = Vec2((centerX() + HALF_WIDTH).toFloat(), (screenHeight() - 39).toFloat())

    // -----------------------------------------------------------------
    // 便捷: 也可以从 GuiGraphics 取屏幕尺寸(HUD 渲染回调里更直接)
    // -----------------------------------------------------------------

    /** 从 GuiGraphics 取屏幕宽度 */
    fun screenWidth(g: GuiGraphics): Int = g.guiWidth()

    /** 从 GuiGraphics 取屏幕高度 */
    fun screenHeight(g: GuiGraphics): Int = g.guiHeight()
}
