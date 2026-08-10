package cn.coostack.cooparticlesapi.network.packet.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientRenderEntityPacketHandlerV2Test {
    @Test
    fun `packet handler resolves client render entity types from v2 registry`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/client/listener/ClientRenderEntityPacketHandler.kt"
        )

        assertTrue("ClientRenderEntityRegistry.get(id)" in source)
        assertFalse("ClientRenderEntityManager.getCodecFromID(id)" in source)
    }

    /** 验证 CREATE 路径通过注册表取得共享 renderer。 */
    @Test
    fun `packet handler create toggle and remove paths operate on instance wrappers`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/client/listener/ClientRenderEntityPacketHandler.kt"
        )

        assertTrue("RenderEntityInstance(" in source)
        assertTrue("ClientRenderEntityRegistry.resolveRenderer(id)" in source)
        assertFalse("factory.invoke()" in source)
        assertTrue("ClientRenderEntityManager.add(instance)" in source)
        assertTrue(".updateFrom(entity)" in source)
        assertTrue(".markRemoved()" in source)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(projectFile(relativePath))
    }

    private fun projectFile(relativePath: String): Path {
        val repoRoot = findRepoRoot()
        return repoRoot.resolve(relativePath)
    }

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) {
                return cursor
            }
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
