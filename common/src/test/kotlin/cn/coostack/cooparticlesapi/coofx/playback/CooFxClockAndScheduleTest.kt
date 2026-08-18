package cn.coostack.cooparticlesapi.coofx.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CooFxClockAndScheduleTest {
    @Test
    fun `clock converts ticks and preserves loop endpoints`() {
        assertEquals(0.5F, CooFxPlaybackClock(10L, 1F).timeAt(20L).seconds)
        val once = CooFxPlaybackClock(0L, 1F, loopMode = CooFxLoopMode.ONCE).timeAt(20L)
        assertEquals(1F, once.seconds)
        assertTrue(once.completed)
        val loop = CooFxPlaybackClock(0L, 1F, loopMode = CooFxLoopMode.LOOP).timeAt(20L)
        assertEquals(0F, loop.seconds)
        assertFalse(loop.completed)
    }

    @Test
    fun `ping pong reverses after duration`() {
        val clock = CooFxPlaybackClock(0L, 1F, loopMode = CooFxLoopMode.PING_PONG)
        assertEquals(1F, clock.timeAt(20L).seconds)
        assertEquals(0.5F, clock.timeAt(30L).seconds)
        assertEquals(0F, clock.timeAt(40L).seconds)
    }

    @Test
    fun `schedule distributes every ordinal exactly once`() {
        val schedule = CooFxEmissionSchedule(delayTicks = 2, count = 5L, durationTicks = 3)
        assertTrue(schedule.emissionsAt(1).isEmpty())
        assertEquals(listOf(0L), schedule.emissionsAt(2).toList())
        assertEquals(listOf(1L, 2L), schedule.emissionsAt(3).toList())
        assertEquals(listOf(3L, 4L), schedule.emissionsAt(4).toList())
        assertTrue(schedule.emissionsAt(5).isEmpty())
    }
}
