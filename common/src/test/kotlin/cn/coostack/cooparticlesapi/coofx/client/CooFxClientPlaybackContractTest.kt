package cn.coostack.cooparticlesapi.coofx.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CooFxClientPlaybackContractTest {
    @Test
    fun `public model updater preserves handle based client tick integration contract`() {
        val client = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFXClient.kt"
        )
        val transforms = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxPlayerRelativeTransforms.kt"
        )

        val updater = client
            .substringAfter("fun updateModel(")
            .substringBefore("/** 客户端 scene registry 用于原位更新 emitter 世界变换。 */")

        assertTrue("@JvmStatic\n    fun updateModel(" in client)
        assertTrue("runtime?.updateModel(handle.instanceId, request) == true" in updater)
        assertFalse("playModel(" in updater)
        assertFalse("play(" in updater)
        assertTrue("CooFxModelPlayResult.Started" in client)
        assertTrue("CooFxPlayerRelativeTransforms.fromPlayerView" in client)
        assertTrue("CooFxPlayerRelativeTransforms.fromView" in client)
        assertTrue("客户端线程" in client)
        assertTrue("world clear、资源 reload 或客户端停止后" in client)
        assertTrue("fun fromPlayerView(" in transforms)
        assertTrue("fun fromView(" in transforms)
        assertFalse("sampleCamera" in updater)
        assertFalse("CooFxCameraTrackingManager" in updater)
        assertFalse("Mouse" in updater)
        assertFalse("Minecraft.getInstance" in updater)
    }

    private fun readProjectFile(relativePath: String): String =
        Files.readString(findRepoRoot().resolve(relativePath))

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("找不到项目根目录")
    }
}
