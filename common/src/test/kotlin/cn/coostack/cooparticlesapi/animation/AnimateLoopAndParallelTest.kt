package cn.coostack.cooparticlesapi.animation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnimateLoopAndParallelTest {
    @Test
    fun `fixed loop uses zero based indices and clamped interval`() {
        val everyTick = mutableListOf<Pair<Int, Int>>()
        val spaced = mutableListOf<Pair<Int, Int>>()
        var currentTick = 0
        val animate = Animate()
            .thenLoop(3, intervalTicks = 0) { iterationIndex ->
                then(action(onStart = { everyTick.add(currentTick to iterationIndex) }))
            }
            .thenLoop(3, intervalTicks = 2) { iterationIndex ->
                then(action(onStart = { spaced.add(currentTick to iterationIndex) }))
            }

        animate.start()
        while (!animate.done) {
            animate.tick()
            currentTick++
        }

        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), everyTick)
        assertEquals(listOf(3 to 0, 5 to 1, 7 to 2), spaced)
    }

    @Test
    fun `zero fixed loop completes without invoking body`() {
        var loopCalls = 0
        var afterCalls = 0
        val animate = Animate()
            .thenLoop(0) {
                loopCalls++
                then(action())
            }
            .then(action(onStart = { afterCalls++ }))

        animate.start()
        animate.tick()
        animate.tick()

        assertEquals(0, loopCalls)
        assertEquals(1, afterCalls)
        assertTrue(animate.done)
    }

    @Test
    fun `conditional loop checks at iteration boundaries and lets active iteration finish`() {
        var enabled = false
        var conditionCalls = 0
        var bodyCalls = 0
        val initiallyFalse = Animate()
            .thenLoopCondition(
                intervalTicks = 2,
                condition = {
                    conditionCalls++
                    enabled
                },
            ) {
                then(action(onStart = { bodyCalls++ }))
            }

        initiallyFalse.start()
        initiallyFalse.tick()

        assertEquals(1, conditionCalls)
        assertEquals(0, bodyCalls)
        assertTrue(initiallyFalse.done)

        enabled = true
        conditionCalls = 0
        val iterationTicks = mutableListOf<Int>()
        var doneCalls = 0
        val running = Animate().thenLoopCondition(
            intervalTicks = 2,
            condition = {
                conditionCalls++
                enabled
            },
        ) { iterationIndex ->
            then(
                action(
                    durationTicks = 2,
                    onDone = { doneCalls++ },
                ) {
                    iterationTicks.add(iterationIndex)
                }
            )
        }

        running.start()
        running.tick()
        enabled = false
        running.tick()
        running.tick()
        running.tick()

        assertEquals(listOf(0, 0), iterationTicks)
        assertEquals(1, doneCalls)
        assertEquals(2, conditionCalls)
        assertTrue(running.done)
    }

    @Test
    fun `fixed loop restarts full action lifecycle every iteration`() {
        val events = mutableListOf<String>()
        val animate = Animate().thenLoop(times = 3) { iterationIndex ->
            then(
                action(
                    durationTicks = 2,
                    onStart = { events.add("$iterationIndex:start:$tickCount") },
                    onDone = { events.add("$iterationIndex:done:$tickCount") },
                ) {
                    events.add("$iterationIndex:tick:$tickCount")
                }
            )
        }

        animate.start()
        while (!animate.done) {
            animate.tick()
        }

        assertEquals(
            listOf(
                "0:start:0", "0:tick:0", "0:tick:1", "0:done:2",
                "1:start:0", "1:tick:0", "1:tick:1", "1:done:2",
                "2:start:0", "2:tick:0", "2:tick:1", "2:done:2",
            ),
            events,
        )
    }

    @Test
    fun `parallel actions wait for the slowest action`() {
        val events = mutableListOf<String>()
        val animate = Animate()
            .thenParallel(
                action(1, onDone = { events.add("fast") }),
                action(3, onDone = { events.add("slow") }),
            )
            .then(action(onStart = { events.add("after") }))

        animate.start()
        repeat(3) { animate.tick() }

        assertEquals(listOf("fast", "slow"), events)

        animate.tick()

        assertEquals(listOf("fast", "slow", "after"), events)
        assertTrue(animate.done)
    }

    @Test
    fun `nested parallel branches preserve branch order and join reliably`() {
        val events = mutableListOf<String>()
        val animate = Animate()
            .thenParallel(
                branch {
                    then(mark("a", events))
                    then(mark("b", events))
                },
                branch {
                    then(mark("c", events))
                    thenParallel(
                        branch { then(action(2, onStart = { events.add("d") })) },
                        branch { then(mark("e", events)) },
                    )
                },
            )
            .then(mark("after", events))

        animate.start()
        animate.tick()
        assertEquals(listOf("a", "c"), events)

        animate.tick()
        assertEquals(listOf("a", "c", "b", "d", "e"), events)

        animate.tick()
        assertEquals(listOf("a", "c", "b", "d", "e"), events)

        animate.tick()
        assertEquals(listOf("a", "c", "b", "d", "e", "after"), events)
        assertTrue(animate.done)
    }

    private fun mark(name: String, events: MutableList<String>): AnimateAction {
        return action(onStart = { events.add(name) })
    }
}
