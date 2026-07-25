package cn.coostack.cooparticlesapi.blocks

import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import cn.coostack.cooparticlesapi.test.block.BlockTestMode
import cn.coostack.cooparticlesapi.test.block.BlockTestPlayer
import sun.misc.Unsafe
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TestControllerBlockEntityReplayTest {
    @BeforeTest
    fun bootstrapMinecraftRegistries() {
        bootstrapMinecraft()
    }

    @Test
    fun `index repeat is not blocked by manual visual review`() {
        val fixture = ReplayFixture(repeat = true, delay = 0)

        assertTrue(fixture.runLoop.start())
        fixture.runLoop.tick()

        assertEquals(2, fixture.groups.size)
        assertFalse(fixture.groups.first().hasPendingReview())
        assertTrue(fixture.runLoop.isRunning())
    }

    @Test
    fun `repeat delay waits configured number of ticks`() {
        val fixture = ReplayFixture(repeat = true, delay = 3)

        fixture.runLoop.start()
        assertTrue(fixture.runLoop.tick())
        assertEquals(1, fixture.groups.size)

        assertTrue(fixture.runLoop.tick())
        assertTrue(fixture.runLoop.tick())
        assertEquals(1, fixture.groups.size)

        assertTrue(fixture.runLoop.tick())
        assertEquals(2, fixture.groups.size)
    }

    @Test
    fun `each replay rebuilds group option and render entity`() {
        val fixture = ReplayFixture(repeat = true, delay = 0)

        fixture.runLoop.start()
        fixture.runLoop.tick()

        val first = fixture.options[0]
        val second = fixture.options[1]
        assertNotSame(fixture.groups[0], fixture.groups[1])
        assertNotSame(first, second)
        assertNotSame(first.entity, second.entity)
    }

    @Test
    fun `index without repeat keeps manual review semantics`() {
        val fixture = ReplayFixture(repeat = false, delay = 0)

        fixture.runLoop.start()
        fixture.runLoop.tick()

        assertEquals(1, fixture.groups.size)
        assertTrue(fixture.groups.single().hasPendingReview())
        assertTrue(fixture.runLoop.isRunning())
    }

    @Test
    fun `review action is ignored until an option is pending review`() {
        val fixture = ReplayFixture(repeat = false, delay = 0)

        fixture.runLoop.start()

        assertFalse(fixture.runLoop.reviewCurrent(BlockTestGroup.OptionResult.PASSED))
        assertEquals(1, fixture.groups.size)
        assertTrue(fixture.runLoop.isRunning())
    }

    @Test
    fun `restoring persisted state cancels the current option`() {
        val fixture = ReplayFixture(repeat = false, delay = 0)

        fixture.runLoop.start()
        val option = fixture.options.single()
        fixture.runLoop.restore(shouldAutoRun = false, waitTicks = 0)

        assertEquals(1, option.stopCount)
        assertFalse(fixture.runLoop.isRunning())
    }

    private class ReplayFixture(
        repeat: Boolean,
        delay: Int
    ) {
        val groups = ArrayList<BlockTestGroup>()
        val options = ArrayList<RenderEntityLikeOption>()
        val runLoop = TestControllerRunLoop(
            groupFactory = {
                val option = RenderEntityLikeOption(Any())
                options.add(option)
                BlockTestGroup(uninitializedBlockTestPlayer(), "replay")
                    .also {
                        it.statusAnnouncer = {}
                        it.announceGroupFinished = false
                        it.appendOption { option }
                        groups.add(it)
                    }
            },
            modeProvider = { BlockTestMode.INDEX },
            repeatIndexProvider = { repeat },
            repeatDelayTicksProvider = { delay }
        )
    }

    private class RenderEntityLikeOption(
        val entity: Any
    ) : TestOption {
        private var valid = true
        var stopCount = 0
            private set

        override fun start() = Unit

        override fun stop() {
            stopCount++
        }

        override fun isValid(): Boolean = valid

        override fun onFailed() = Unit

        override fun onSuccess() = Unit

        override fun optionID(): String = "render-entity-like"

        override fun doTick() {
            valid = false
        }

        override fun reviewMode(): TestReviewMode = TestReviewMode.MANUAL_VISUAL
    }

    companion object {
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
    }
}
