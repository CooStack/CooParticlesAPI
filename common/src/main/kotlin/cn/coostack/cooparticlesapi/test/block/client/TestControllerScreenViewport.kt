package cn.coostack.cooparticlesapi.test.block.client

import kotlin.math.floor

/**
 * 保存测试控制器的内容画布尺寸和相对真实 GUI 视口的缩放比例。
 *
 * 示例：真实视口为 `512x288` 时，内容使用至少 `600x320` 的坐标系并整体缩小。
 * 禁止把这里的宽高写回 Minecraft 的 [net.minecraft.client.gui.screens.Screen]，否则背景和原生事件会使用错误尺寸。
 *
 * @property scale 内容坐标转换到真实 GUI 坐标时使用的比例，最大为 `1.0`
 * @property width 内容画布宽度，单位为内容坐标像素
 * @property height 内容画布高度，单位为内容坐标像素
 */
internal data class TestControllerScreenViewport(
    val scale: Double,
    val width: Int,
    val height: Int,
) {
    /**
     * 把真实 GUI 坐标或拖动距离还原到内容坐标系。
     *
     * 示例：比例为 `0.5` 时，真实坐标 `100.0` 对应内容坐标 `200.0`。
     * 禁止对已经处于内容坐标系的值重复调用。
     *
     * @param value 真实 GUI 坐标或距离
     * @return 对应的内容坐标或距离
     */
    fun unscale(value: Double): Double = value / scale

    /**
     * 提供测试控制器的参考布局尺寸并创建视口。
     *
     * 示例：[calculate] 可在 Screen 初始化或尺寸变化后重新计算布局。
     * 禁止在这里保存某个 Screen 实例的可变状态。
     */
    companion object {
        /**
         * 主界面保持动态按钮同行显示所需的参考宽度。
         *
         * 示例：小于该宽度的真实视口会缩小内容画布；禁止把它当作窗口像素宽度。
         */
        private const val REFERENCE_WIDTH = 600

        /**
         * 主界面为底部状态和操作按钮保留间距所需的参考高度。
         *
         * 示例：小于该高度的真实视口会缩小内容画布；禁止用它裁剪参数行。
         */
        private const val REFERENCE_HEIGHT = 320

        /**
         * 按真实 GUI 尺寸计算不超过 `1.0` 的内容缩放和可用画布。
         *
         * 示例：`calculate(512, 288)` 返回宽度至少为 `600` 的内容画布。
         * 禁止传入零或负数；Minecraft 初始化 Screen 时应提供有效尺寸。
         *
         * @param screenWidth 真实 GUI 逻辑宽度
         * @param screenHeight 真实 GUI 逻辑高度
         * @return 能完整容纳参考布局的内容视口
         */
        fun calculate(screenWidth: Int, screenHeight: Int): TestControllerScreenViewport {
            val scale = minOf(
                1.0,
                screenWidth.toDouble() / REFERENCE_WIDTH.toDouble(),
                screenHeight.toDouble() / REFERENCE_HEIGHT.toDouble(),
            )
            return TestControllerScreenViewport(
                scale = scale,
                width = floor(screenWidth.toDouble() / scale).toInt().coerceAtLeast(REFERENCE_WIDTH),
                height = floor(screenHeight.toDouble() / scale).toInt().coerceAtLeast(REFERENCE_HEIGHT),
            )
        }
    }
}
