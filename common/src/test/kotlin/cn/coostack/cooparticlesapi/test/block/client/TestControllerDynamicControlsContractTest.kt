package cn.coostack.cooparticlesapi.test.block.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证位置和 forward 的动态控件直接位于控制器主界面。
 *
 * 示例：位置切到动态后，同一行右侧会出现“编辑”按钮。
 * 禁止重新引入占据整行的动态效果入口。
 */
class TestControllerDynamicControlsContractTest {
    /**
     * 主界面应分别持有两个通道的模式按钮和条件编辑按钮。
     *
     * 示例：`animationDraft.positionDynamic` 为 `true` 时位置编辑按钮可见。
     * 禁止通过独立动态选择页中转打开曲线编辑器。
     */
    @Test
    fun dynamicControlsLiveBesideTheirVectorRows() {
        val source = Files.readString(projectFile(SCREEN_PATH))

        assertFalse("dynamicButton" in source)
        assertFalse("TestControllerDynamicScreen" in source)
        assertTrue("positionModeButton" in source)
        assertTrue("forwardModeButton" in source)
        assertTrue("positionEditButton" in source)
        assertTrue("forwardEditButton" in source)
        assertTrue("positionEditButton.visible = mainPage && animationDraft.positionDynamic" in source)
        assertTrue("forwardEditButton.visible = mainPage && animationDraft.forwardDynamic" in source)
        assertTrue("TestControllerCurveEditorScreen" in source)
        assertTrue("MIN_INLINE_ANIMATION_WIDTH" in source)
        assertTrue("ANIMATION_STACK_X" in source)
        assertTrue("animationInline = width >= MIN_INLINE_ANIMATION_WIDTH" in source)
        assertTrue("animationRowExtra" in source)
    }

    /**
     * 从任意 Gradle 模块目录定位控制器源码。
     *
     * 示例：测试从 `common` 目录运行时会向上找到仓库根目录。
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

    /**
     * 保存主界面源码的固定仓库相对路径。
     *
     * 示例：[dynamicControlsLiveBesideTheirVectorRows] 使用该路径读取源码。
     * 禁止改成编译输出目录。
     */
    companion object {
        /**
         * 控制器主界面的源码路径。
         *
         * 示例：[projectFile] 使用该值定位手写源码。
         * 禁止在测试期间修改该值。
         */
        private const val SCREEN_PATH =
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerScreen.kt"
    }
}
