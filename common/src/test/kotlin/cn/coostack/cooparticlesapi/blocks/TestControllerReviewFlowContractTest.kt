package cn.coostack.cooparticlesapi.blocks

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class TestControllerReviewFlowContractTest {
    @Test
    fun `manual review is exposed through block entity packet and screen`() {
        val blockEntity = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/blocks/TestControllerBlockEntity.kt"
        )
        val packet = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/testblock/PacketReviewTestControllerC2S.kt"
        )
        val openPacket = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/testblock/PacketOpenTestControllerScreenS2C.kt"
        )
        val screen = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerScreen.kt"
        )
        val packetDrafts = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerPacketDrafts.kt"
        )

        val reviewCurrentBody = blockEntity
            .substringAfter("fun reviewCurrent(result: BlockTestOptionResult)")
            .substringBefore("fun reviewCurrent(result: BlockTestGroup.OptionResult)")

        assertTrue("val wasRunning = runLoop.isRunning()" in reviewCurrentBody)
        assertTrue("if (wasRunning && !runLoop.isRunning())" in reviewCurrentBody)
        assertTrue("freezeAnimation()" in reviewCurrentBody)
        assertTrue("fun reviewCurrent(result: BlockTestGroup.OptionResult)" in blockEntity)
        assertTrue("fun updateConfig(\n        groupId: String" in blockEntity)
        assertTrue("enum class OptionResult" in readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/BlockTestGroup.kt"
        ))
        assertTrue("@CooAutoRegister" in packet)
        assertTrue("reviewCurrent(result)" in packet)
        assertTrue("pendingReview = blockEntity.hasPendingReview()" in openPacket)
        assertTrue("PacketReviewTestControllerC2S.PASS" in screen)
        assertTrue("PacketReviewTestControllerC2S.FAIL" in screen)
        assertTrue("PacketReviewTestControllerC2S.SKIP" in screen)
        assertTrue("fun reopenPacket(" in packetDrafts)
        assertTrue("it.pendingReview = source.pendingReview" in packetDrafts)
    }

    @Test
    fun `review buttons submit pending config before sending the review result`() {
        val screen = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerScreen.kt"
        )
        val reviewButtonBody = screen
            .substringAfter("private fun reviewButton(label: String, action: String, x: Int, y: Int, width: Int)")
            .substringBefore("}.bounds(x, y, width, 20).build()")

        val update = reviewButtonBody.indexOf("CooClientPacketManager.sendTo(updatePacket())")
        val review = reviewButtonBody.indexOf("PacketReviewTestControllerC2S(packet.dimension, packet.blockPos, action)")
        assertTrue(update >= 0, "复核按钮必须先提交当前界面配置")
        assertTrue(review > update, "配置更新包必须早于复核包发送")
    }

    @Test
    fun `config update keeps the pending review instead of restarting the group`() {
        val updatePacket = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/testblock/PacketUpdateTestControllerC2S.kt"
        )

        assertTrue("val pendingReview = blockEntity.hasPendingReview()" in updatePacket)
        assertTrue("if (wasRunning && changed && !pendingReview) {" in updatePacket)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(findRepoRoot().resolve(relativePath))
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
