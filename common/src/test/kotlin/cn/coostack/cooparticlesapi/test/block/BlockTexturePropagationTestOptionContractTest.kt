package cn.coostack.cooparticlesapi.test.block

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证方块纹理传播测试项只依赖测试玩家，并在服务端使用主线程传播入口。 */
class BlockTexturePropagationTestOptionContractTest {
    /** 防止测试项重新依赖控制器实体或异步读取服务端世界。 */
    @Test
    fun `propagation option depends on test player instead of controller block`() {
        val source = projectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/" +
                "BlockTexturePropagationTestOption.kt"
        ).readText()

        assertTrue("private val player: Player" in source)
        assertTrue("val level = player.level() as? ServerLevel" in source)
        assertTrue("CooTerrainEffectManager.apply" in source)
        assertTrue("CooTerrainEffectManager.append" in source)
        assertTrue("CooTerrainEffectManager.remove" in source)
        assertTrue("override fun onFailed() = stop()" in source)
        assertTrue("override fun onSuccess() = stop()" in source)
        assertTrue("CooTerrainEffectGroup(effectGroupId, CooTerrainPropagation.pipeline)" in source)
        assertFalse("PacketTerrainPropagationS2C" in source)
        assertFalse("CooServerPacketManager" in source)
        assertFalse("player.publishTerrainPropagation" in source)
        assertFalse("player.clearTerrainPropagation" in source)
        assertFalse("TestControllerBlockEntity" in source)
        assertFalse("getBlockEntity" in source)
        assertTrue("condition = { state ->" in source)
        assertTrue("stepOnMainThread(level)" in source)
        assertTrue("spread" in source)
        assertTrue("PROPAGATION_TICKS = 60L" in source)
        assertFalse("TOTAL_TICKS" in source)
        assertTrue("val latestFinish = discoveredAt.values.maxOrNull()?.plus(90L)" in source)
        assertTrue("return level.gameTime < latestFinish" in source)
        assertTrue("createSpread()" in source)
        assertFalse("spread?.step(player.level)" in source)
    }

    private fun projectFile(relativePath: String): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) {
                return cursor.resolve(relativePath)
            }
            cursor = cursor.parent
        }
        error("找不到仓库根目录：${System.getProperty("user.dir")}")
    }
}
