package cn.coostack.cooparticlesapi.test.block.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 验证世界拾取在容器等方块执行默认交互前消费 Shift + 右键。
 *
 * 示例：拾取箱子中心时不会打开箱子界面，拾取状态机会直接提交命中点。
 * 禁止只在客户端 tick 中轮询右键；容器界面会先打开并取消拾取请求。
 */
class TestControllerPickInteractionContractTest {
    /** 拾取模式必须在 Minecraft 分发方块交互前消费本次使用输入。 */
    @Test
    fun worldPickConsumesUseBeforeBlockInteraction() {
        val useMixinSource = Files.readString(projectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/TestControllerPickUseMixin.java"
        ))
        val mouseMixinSource = Files.readString(projectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/TestControllerPickMouseMixin.java"
        ))
        val clientSource = Files.readString(projectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerPickClient.kt"
        ))
        val mixinConfig = Files.readString(projectFile("common/src/main/resources/cooparticlesapi.mixins.json"))

        assertTrue("\"TestControllerPickUseMixin\"" in mixinConfig)
        assertTrue("\"TestControllerPickMouseMixin\"" in mixinConfig)
        assertTrue("@Mixin(Minecraft.class)" in useMixinSource)
        assertTrue("method = \"startUseItem\"" in useMixinSource)
        assertTrue("at = @At(\"HEAD\")" in useMixinSource)
        assertTrue("cancellable = true" in useMixinSource)
        assertTrue("TestControllerPickClient.consumePickUseInput()" in useMixinSource)
        assertTrue("ci.cancel()" in useMixinSource)
        assertTrue("fun consumePickUseInput(): Boolean" in clientSource)
        assertTrue("@Shadow" in useMixinSource)
        assertTrue("private int rightClickDelay;" in useMixinSource)
        val cooldownAssignment = useMixinSource.indexOf("this.rightClickDelay = 4;")
        val cancellation = useMixinSource.indexOf("ci.cancel();")
        assertTrue(cooldownAssignment >= 0)
        assertTrue(cancellation > cooldownAssignment)
        assertTrue("suppressUseUntilRelease" in clientSource)
        assertTrue("if (suppressUseUntilRelease)" in clientSource)
        assertTrue("suppressUseUntilRelease = true" in clientSource)
        assertTrue("@Mixin(MouseHandler.class)" in mouseMixinSource)
        assertTrue("method = \"onPress\"" in mouseMixinSource)
        assertTrue("button == GLFW.GLFW_MOUSE_BUTTON_RIGHT" in mouseMixinSource)
        assertTrue("action == GLFW.GLFW_RELEASE" in mouseMixinSource)
        assertTrue("TestControllerPickClient.releaseUseSuppression()" in mouseMixinSource)
        assertTrue("fun releaseUseSuppression()" in clientSource)
    }

    /** 从任意 Gradle 模块目录定位仓库内源码。 */
    private fun projectFile(relativePath: String): Path {
        var root = Path.of("").toAbsolutePath()
        repeat(5) {
            val candidate = root.resolve(relativePath)
            if (Files.exists(candidate)) return candidate
            root = root.parent ?: return@repeat
        }
        error("找不到项目文件: $relativePath")
    }
}
