package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
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

    private fun pendingGroup(option: RecordingOption): BlockTestGroup {
        return groupOf(option).also {
            it.start()
            it.doTick()
            assertTrue(it.hasPendingReview())
        }
    }

    private fun groupOf(option: TestOption): BlockTestGroup {
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
    ) : TestOption {
        var successCount = 0
        var failedCount = 0
        private var valid = true

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
