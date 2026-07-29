package cn.coostack.cooparticlesapi.test.block.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 验证关键帧窗口提供独立的世界取点模式，并能在取点结束后恢复草稿。
 *
 * 示例：点击“拾取模式”后用 Shift+右键取点，成功结果回填当前关键帧。
 * 禁止把该流程改成直接发送控制器更新包；关键帧仍需点击“保存”提交。
 */
class TestControllerKeyframeScreenContractTest {
    /**
     * 关键帧窗口必须显示拾取模式按钮并复用通用取点客户端。
     *
     * 示例：位置轨道通过 `TestControllerPickKind.OFFSET` 取相对坐标。
     * 禁止只保留“拾取玩家”按钮而没有 Shift+右键流程。
     */
    @Test
    fun keyframeScreenProvidesWorldPickMode() {
        val source = Files.readString(projectFile(SCREEN_PATH))

        assertTrue("pickModeButton" in source)
        assertTrue("Component.literal(\"拾取模式\")" in source)
        assertTrue(".bounds(left, 120, 80, 20)" in source)
        assertTrue("TestControllerPickClient.begin" in source)
        assertTrue("minecraft?.setScreen(null)" in source)
        assertTrue("onPicked =" in source)
        assertTrue("onCancelled =" in source)
        assertTrue("captureInputValues()" in source)
        assertTrue("preservedX" in source)
    }

    /**
     * 曲线编辑器打开关键帧窗口时必须传递控制器快照和客户端草稿。
     *
     * 示例：取点线的起点使用控制器方块中心。
     * 禁止在关键帧窗口内重新读取服务端状态覆盖用户草稿。
     */
    @Test
    fun curveEditorPassesPickContextToKeyframe() {
        val source = Files.readString(projectFile(CURVE_PATH))
        assertTrue("Vec3.atCenterOf(packet.blockPos)," in source)
        assertTrue("                        packet," in source)
        assertTrue("                        draft," in source)
    }

    /**
     * 从任意 Gradle 模块目录定位仓库内源码。
     *
     * 示例：测试在 `common` 模块运行时会向上找到根目录。
     * 禁止返回不存在的路径。
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

    /** 保存关键帧和曲线编辑器源码路径。 */
    companion object {
        /** 关键帧窗口源码路径。 */
        private const val SCREEN_PATH =
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerKeyframeScreen.kt"
        /** 曲线编辑器源码路径。 */
        private const val CURVE_PATH =
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerCurveEditorScreen.kt"
    }
}
