package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import net.minecraft.world.entity.player.Player
import sun.misc.Unsafe
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BlockTestGroupTest {
    @BeforeTest
    fun bootstrapMinecraftRegistries() {
        bootstrapMinecraft()
    }

    @Test
    fun `auto option completes normally`() {
        val option = RecordingOption(TestReviewMode.AUTO)
        val group = groupOf(option)

        group.start()
        group.doTick()

        assertTrue(group.isDone())
        assertFalse(group.hasPendingReview())
        assertEquals(1, option.successCount)
        assertEquals(0, option.failedCount)
    }

    @Test
    fun `auto group policy passes a manual visual option`() {
        val option = RecordingOption(TestReviewMode.MANUAL_VISUAL)
        val group = groupOf(option).also {
            it.reviewMode = BlockTestReviewMode.AUTO
        }

        group.start()
        group.doTick()

        assertTrue(group.isDone())
        assertFalse(group.hasPendingReview())
        assertEquals(1, option.successCount)
        assertEquals(0, option.failedCount)
    }

    @Test
    fun `manual visual option enters pending review by default`() {
        val option = RecordingOption(TestReviewMode.MANUAL_VISUAL)
        val group = groupOf(option)

        group.start()
        group.doTick()

        assertFalse(group.isDone())
        assertTrue(group.hasPendingReview())
        assertEquals(0, option.successCount)
        assertEquals(0, option.failedCount)
    }

    @Test
    fun `complete clears pending review and passes option`() {
        val option = RecordingOption(TestReviewMode.MANUAL_VISUAL)
        val group = pendingGroup(option)

        assertSame(option, group.completeCurrent())

        assertTrue(group.isDone())
        assertFalse(group.hasPendingReview())
        assertEquals(1, option.successCount)
        assertEquals(0, option.failedCount)
    }

    @Test
    fun `fail clears pending review and fails option`() {
        val option = RecordingOption(TestReviewMode.MANUAL_VISUAL)
        val group = pendingGroup(option)

        assertSame(option, group.failCurrent())

        assertTrue(group.isDone())
        assertFalse(group.hasPendingReview())
        assertEquals(0, option.successCount)
        assertEquals(1, option.failedCount)
    }

    @Test
    fun `skip clears pending review and marks option failed`() {
        val option = RecordingOption(TestReviewMode.MANUAL_VISUAL)
        val group = pendingGroup(option)

        assertSame(option, group.skipCurrent())

        assertTrue(group.isDone())
        assertFalse(group.hasPendingReview())
        assertEquals(0, option.successCount)
        assertEquals(1, option.failedCount)
        assertNull(group.currentOption)
    }

    /**
     * 玩家更新回调应在 Option tick 前收到当前模拟玩家和参数目标。
     *
     * 示例：依赖玩家位置的粒子目标会先同步位置，再执行自身 tick。
     * 禁止把待复核 Option 当作仍在运行的 Option 重复更新。
     */
    @Test
    fun `player update callback receives typed context before option tick`() {
        val events = ArrayList<String>()
        val target = StringBuilder()
        var callbackReceiver: TestOption<StringBuilder>? = null
        var callbackPlayer: Player? = null
        var valid = true
        val option = object : TestOption<StringBuilder> {
            override fun paramTarget(): StringBuilder = target
            override fun start() = Unit
            override fun stop() = Unit
            override fun isValid(): Boolean = valid
            override fun onFailed() = Unit
            override fun onSuccess() = Unit
            override fun optionID(): String = "player-update"
            override fun doTick() {
                events += "tick"
                valid = false
            }
        }.onPlayerUpdate { player, receivedTarget ->
            callbackReceiver = this
            callbackPlayer = player
            assertSame(target, receivedTarget)
            events += "player-update"
        }
        val group = groupOf(option)

        group.start()
        group.doTick()
        group.doTick()

        assertEquals(listOf("player-update", "tick"), events)
        assertSame(option, callbackReceiver)
        assertSame(group.testPlayer, callbackPlayer)
    }

    private fun pendingGroup(option: RecordingOption): BlockTestGroup {
        return groupOf(option).also {
            it.start()
            it.doTick()
            assertTrue(it.hasPendingReview())
        }
    }

    private fun groupOf(option: TestOption<*>): BlockTestGroup {
        return BlockTestGroup(uninitializedBlockTestPlayer(), "test")
            .also {
                it.statusAnnouncer = {}
                it.announceGroupFinished = false
            }
            .appendOption { option }
    }

    private fun uninitializedBlockTestPlayer(): BlockTestPlayer {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return (field.get(null) as Unsafe).allocateInstance(BlockTestPlayer::class.java) as BlockTestPlayer
    }

    private fun bootstrapMinecraft() {
        val sharedConstants = Class.forName("net.minecraft.SharedConstants")
        sharedConstants.getMethod("tryDetectVersion").invoke(null)
        val bootstrap = Class.forName("net.minecraft.server.Bootstrap")
        bootstrap.getMethod("bootStrap").invoke(null)
    }

    private class RecordingOption(
        private val mode: TestReviewMode
    ) : TestOption<RecordingOption> {
        var successCount = 0
        var failedCount = 0
        private var valid = true

        override fun paramTarget(): RecordingOption = this

        override fun start() = Unit

        override fun stop() = Unit

        override fun isValid(): Boolean = valid

        override fun onFailed() {
            failedCount++
        }

        override fun onSuccess() {
            successCount++
        }

        override fun optionID(): String = "recording"

        override fun doTick() {
            valid = false
        }

        override fun reviewMode(): TestReviewMode = mode
    }
}
