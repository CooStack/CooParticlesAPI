package cn.coostack.cooparticlesapi.test.block.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证曲线编辑器只在用户结束总时长输入后提交轨道。
 *
 * 示例：输入 `100` 的逐字符阶段不会先把轨道压缩到 2 tick。
 * 禁止用本测试替代游戏内输入框焦点检查；这里只保护提交调用位置。
 */
class TestControllerCurveEditorScreenContractTest {
    /**
     * 渲染循环不得提交总时长，Enter、鼠标离开和返回必须提交。
     *
     * 示例：`render` 只读取输入框焦点，完整文本由 `syncDuration` 处理。
     * 禁止把 `syncDuration()` 放回每帧无条件执行路径。
     */
    @Test
    fun durationCommitsAfterEditingInsteadOfEveryFrame() {
        val source = Files.readString(projectFile(SCREEN_PATH))
        val keyPressed = source.substringAfter("override fun keyPressed").substringBefore("override fun mouseClicked")
        val mouseClicked = source.substringAfter("override fun mouseClicked").substringBefore("override fun mouseDragged")
        val render = source.substringAfter("override fun render").substringBefore("private fun drawTimeline")
        val finish = source.substringAfter("private fun finish").substringBefore("private fun inTimeline")

        assertTrue("GLFW.GLFW_KEY_ENTER" in keyPressed)
        assertTrue("syncDuration()" in keyPressed)
        assertTrue("!durationBox.isMouseOver" in mouseClicked)
        assertTrue("syncDuration()" in mouseClicked)
        val focusGuard = render.indexOf("if (durationWasFocused && !durationBox.isFocused)")
        val renderCommit = render.indexOf("syncDuration()")
        assertTrue(focusGuard >= 0)
        assertTrue(renderCommit > focusGuard)
        assertTrue("syncDuration()" in finish)
    }

    /**
     * 时间线和曲线区域必须遮住世界背景，并给关键帧留出稳定的点击范围。
     *
     * 示例：模糊世界背景较亮时，关键帧和坐标轴仍能清楚显示并被点中。
     * 禁止恢复半透明面板或把命中范围缩回仅覆盖标记中心的尺寸。
     */
    @Test
    fun timelinePanelsAreOpaqueAndKeyframesHaveUsableHitTargets() {
        val source = Files.readString(projectFile(SCREEN_PATH))
        val render = source.substringAfter("override fun render").substringBefore("private fun drawTimeline")
        val timeline = source.substringAfter("private fun drawTimeline").substringBefore("private fun drawCurve")
        val curve = source.substringAfter("private fun drawCurve").substringBefore("private fun drawHandle")

        assertFalse("renderBackground(" in render)
        assertTrue(render.indexOf("super.render") < render.indexOf("drawTimeline"))
        assertTrue("0xFF151515" in timeline)
        assertTrue("0xFF101010" in curve)
        assertFalse("0xAA151515" in timeline)
        assertFalse("0xAA101010" in curve)
        assertTrue("MARKER_RADIUS = 10.0" in source)
        assertTrue("x - 5" in timeline)
        assertTrue("x + 6" in timeline)
        assertTrue("x + 2" in curve)
        assertTrue("x - 1" in source)
        assertTrue("nearestDistance" in source)
        assertTrue("distance < nearestDistance" in source)
    }

    /**
     * 从任意 Gradle 模块目录定位仓库内源码。
     *
     * 示例：测试在 `common` 模块启动时会向上找到根目录。
     * 禁止返回不存在的路径；找不到文件时测试应立即失败。
     *
     * @param relativePath 仓库根目录下的相对路径
     * @return 已存在的源码路径
     */
    private fun projectFile(relativePath: String): Path {
        var root = Path.of("").toAbsolutePath()
        repeat(5) {
            val candidate = root.resolve(relativePath)
            if (Files.exists(candidate)) return candidate
            root = root.parent ?: return@repeat
        }
        error("找不到项目文件: $relativePath")
    }

    /**
     * 保存契约测试的固定源码定位信息。
     *
     * 示例：[durationCommitsAfterEditingInsteadOfEveryFrame] 从这里取得目标路径。
     * 禁止在此保存测试执行期间变化的目录状态。
     */
    companion object {
        /**
         * 曲线编辑器相对仓库根目录的源码路径。
         *
         * 示例：[projectFile] 使用该值定位手写源码。
         * 禁止改成编译输出路径；契约必须检查源文件。
         */
        private const val SCREEN_PATH =
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerCurveEditorScreen.kt"
    }
}
