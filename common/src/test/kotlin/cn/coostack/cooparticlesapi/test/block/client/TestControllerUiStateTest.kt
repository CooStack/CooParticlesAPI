package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.blocks.BoundControllerEntry
import cn.coostack.cooparticlesapi.blocks.defaultTestControllerGroupId
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenBoundTestSelectionScreenS2C
import net.minecraft.core.BlockPos
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestControllerUiStateTest {
    @Test
    fun `default group id uses built in block api test group`() {
        assertEquals(
            "cooparticlesapi:block-api-test-group-builder",
            defaultTestControllerGroupId().toString()
        )
    }

    @Test
    fun `running status appends current option id`() {
        assertEquals(
            "运行中 当前索引: 3/15 ID: particle-style-test",
            testControllerRunningStatus(3, 15, "particle-style-test")
        )
        assertEquals("运行中 当前索引: 3/15", testControllerRunningStatus(3, 15, ""))
        assertEquals("运行中", testControllerRunningStatus(0, 15, "particle-style-test"))
    }

    @Test
    fun `pending review status includes current option id`() {
        val screen = Files.readString(findRepoRoot().resolve(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/client/TestControllerScreen.kt"
        ))
        val pendingReviewBody = screen
            .substringAfter("if (packet.pendingReview)")
            .substringBefore("if (packet.running)")

        assertTrue("testControllerStatusWithOptionId(" in pendingReviewBody)
        assertTrue("\"等待人工复核\"" in pendingReviewBody)
        assertTrue("packet.currentIndex" in pendingReviewBody)
        assertTrue("packet.optionIds.getOrNull(packet.currentIndex - 1)" in pendingReviewBody)
    }

    @Test
    fun `bound selection packet preserves current option ids`() {
        val packet = PacketOpenBoundTestSelectionScreenS2C.fromEntries(listOf(
            BoundControllerEntry(
                dimension = "minecraft:overworld",
                pos = BlockPos(1, 2, 3),
                groupId = "cooparticlesapi:block-api-test-group-builder",
                status = "运行中",
                currentIndex = 3,
                currentOptionId = "particle-style-test",
                optionCount = 15,
                loaded = true,
                running = true
            )
        ))

        assertEquals(listOf("particle-style-test"), packet.currentOptionIds)
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
