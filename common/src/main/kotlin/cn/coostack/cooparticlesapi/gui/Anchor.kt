package cn.coostack.cooparticlesapi.gui

/**
 * 水平锚点 —— 描述一个元素在屏幕水平方向上"贴"在哪里。
 *
 * 用锚点而不是写死的 x 坐标, 是为了让界面在任何分辨率/GUI缩放下都能正确归位:
 * 你只需要说"贴右边", 工具会根据当前屏幕宽度算出真实坐标。
 */
enum class AnchorX {
    /** 贴屏幕左边 */
    LEFT,

    /** 屏幕水平正中 */
    CENTER,

    /** 贴屏幕右边 */
    RIGHT
}

/**
 * 垂直锚点 —— 描述一个元素在屏幕垂直方向上"贴"在哪里。
 */
enum class AnchorY {
    /** 贴屏幕顶部 */
    TOP,

    /** 屏幕垂直正中 */
    CENTER,

    /** 贴屏幕底部 */
    BOTTOM
}
