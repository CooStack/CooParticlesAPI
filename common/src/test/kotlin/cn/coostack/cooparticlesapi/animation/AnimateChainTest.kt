package cn.coostack.cooparticlesapi.animation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimateChainTest {
    @Test
    fun `then accepts custom AnimateAction implementations`() {
        val events = mutableListOf<String>()
        val animate = Animate()
            .then(CustomAction(events))
            .then(action(onStart = { events.add("next") }))

        animate.start()
        tickUntilDone(animate)

        assertEquals(
            listOf("custom:start", "custom:tick:0", "custom:tick:1", "custom:done", "next"),
            events,
        )
    }

    @Test
    fun `action defaults to one tick and preserves callback order`() {
        val events = mutableListOf<String>()
        val animate = Animate().then(
            action(
                onStart = { events.add("start:$tickCount") },
                onDone = { events.add("done:$tickCount") },
            ) {
                events.add("tick:$tickCount")
            }
        )

        animate.start()
        animate.tick()

        assertEquals(listOf("start:0", "tick:0", "done:1"), events)
        assertTrue(animate.done)
    }

    @Test
    fun `then preserves order and delay is relative to previous completion`() {
        val events = mutableListOf<String>()
        var currentTick = 0
        val animate = Animate()
            .then(action(1, onStart = { events.add("first-start:$currentTick") }))
            .then(action(1, onStart = { events.add("second-start:$currentTick") }), delayTicks = 2)

        animate.start()
        repeat(4) {
            animate.tick()
            currentTick++
        }

        assertEquals(listOf("first-start:0", "second-start:3"), events)
        assertTrue(animate.done)
    }

    @Test
    fun `raw addNode does not move then cursor`() {
        val events = mutableListOf<String>()
        val animate = Animate()
            .then(action(1, onStart = { events.add("chain-1") }))
            .addNode(AnimateNode().addAction(action(2, onStart = { events.add("root") })))
            .then(action(1, onStart = { events.add("chain-2") }))

        animate.start()
        animate.tick()

        assertEquals(listOf("chain-1", "root"), events)

        animate.tick()

        assertEquals(listOf("chain-1", "root", "chain-2"), events)
        assertTrue(animate.done)
    }

    @Test
    fun `lambda action duration zero one and n have exact tick counts`() {
        val starts = IntArray(3)
        val ticks = IntArray(3)
        val dones = IntArray(3)
        val animate = Animate()
            .then(counterAction(0, 0, starts, ticks, dones))
            .then(counterAction(1, 1, starts, ticks, dones))
            .then(counterAction(4, 2, starts, ticks, dones))

        animate.start()
        tickUntilDone(animate)

        assertEquals(listOf(1, 1, 1), starts.toList())
        assertEquals(listOf(0, 1, 4), ticks.toList())
        assertEquals(listOf(1, 1, 1), dones.toList())
    }

    @Test
    fun `waitTicks and waitUntil pause only the timeline`() {
        var ready = false
        var conditionCalls = 0
        val events = mutableListOf<String>()
        val animate = Animate()
            .waitTicks(2)
            .waitUntil {
                conditionCalls++
                ready
            }
            .then(action(onStart = { events.add("done") }))

        animate.start()
        animate.tick()
        animate.tick()
        animate.tick()

        assertEquals(1, conditionCalls)
        assertFalse(animate.done)

        ready = true
        animate.tick()
        animate.tick()

        assertEquals(2, conditionCalls)
        assertEquals(listOf("done"), events)
        assertTrue(animate.done)
    }

    private fun counterAction(
        durationTicks: Int,
        index: Int,
        starts: IntArray,
        ticks: IntArray,
        dones: IntArray,
    ): AnimateAction {
        return action(
            durationTicks = durationTicks,
            onStart = { starts[index]++ },
            onDone = { dones[index]++ },
        ) { ticks[index]++ }
    }

    private fun tickUntilDone(animate: Animate, limit: Int = 100) {
        repeat(limit) {
            if (animate.done) return
            animate.tick()
        }
        error("Animation did not complete within $limit ticks")
    }

    private class CustomAction(
        private val events: MutableList<String>,
    ) : AnimateAction() {
        override fun checkDone(): Boolean {
            return tickCount >= 2
        }

        override fun tick() {
            events.add("custom:tick:$tickCount")
        }

        override fun onStart() {
            events.add("custom:start")
        }

        override fun onDone() {
            events.add("custom:done")
        }
    }
}
