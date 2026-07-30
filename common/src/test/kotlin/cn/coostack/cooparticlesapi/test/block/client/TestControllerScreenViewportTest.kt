package cn.coostack.cooparticlesapi.test.block.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证测试控制器在不同 GUI 逻辑尺寸下都能得到完整的内容画布。
 *
 * 示例：自动 GUI scale 产生 `512x288` 视口时，内容会缩放到至少 `600x320` 的画布。
 * 禁止只压缩控件间距来适配小视口，否则底部状态与操作按钮仍可能重叠。
 */
class TestControllerScreenViewportTest {
    /**
     * 验证可容纳参考布局的视口不会被放大。
     *
     * 示例：`854x480` 保持 `1.0` 倍；禁止把大视口放大到超出原始边界。
     */
    @Test
    fun keepsNativeCoordinatesWhenScreenFitsReferenceLayout() {
        val viewport = TestControllerScreenViewport.calculate(854, 480)

        assertEquals(1.0, viewport.scale)
        assertEquals(854, viewport.width)
        assertEquals(480, viewport.height)
    }

    /**
     * 验证自动 GUI scale 常见的小视口会恢复足够大的内容画布。
     *
     * 示例：`512x288` 使用宽度约束缩放；禁止让虚拟画布窄于动态控件的同行布局宽度。
     */
    @Test
    fun scalesAutomaticGuiViewportIntoReferenceCanvas() {
        val viewport = TestControllerScreenViewport.calculate(512, 288)

        assertEquals(512.0 / 600.0, viewport.scale, 1.0E-9)
        assertEquals(600, viewport.width)
        assertTrue(viewport.height >= 320)
        assertTrue(viewport.width * viewport.scale <= 512.001)
        assertTrue(viewport.height * viewport.scale <= 288.001)
    }

    /**
     * 验证更小的视口仍保留完整参考画布。
     *
     * 示例：`320x240` 以宽度为准缩放；禁止裁掉底部操作按钮或状态文字。
     */
    @Test
    fun smallViewportStillKeepsReferenceCanvasVisible() {
        val viewport = TestControllerScreenViewport.calculate(320, 240)

        assertEquals(320.0 / 600.0, viewport.scale, 1.0E-9)
        assertTrue(viewport.width >= 600)
        assertTrue(viewport.height >= 320)
        assertTrue(viewport.height * viewport.scale <= 240.001)
    }

    /**
     * 验证鼠标坐标会映射回控件使用的内容坐标系。
     *
     * 示例：缩放后屏幕中点仍对应内容画布中点；禁止把原始屏幕坐标直接交给缩放后的控件。
     */
    @Test
    fun unscaleMapsMouseCoordinatesIntoContentCanvas() {
        val viewport = TestControllerScreenViewport.calculate(512, 288)

        assertEquals(300.0, viewport.unscale(256.0), 1.0E-9)
        assertEquals(150.0, viewport.unscale(128.0), 1.0E-9)
    }
}
