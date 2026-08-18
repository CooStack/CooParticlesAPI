package cn.coostack.cooparticlesapi.animation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimateConditionAndLifecycleTest {
    @Test
    fun `thenWhen chooses first match once and supports otherwise`() {
        val events = mutableListOf<String>()
        var secondChecks = 0
        var unselectedStarts = 0
        val firstMatch = Animate().thenWhen(
            case({ true }) { then(mark("first", events)) },
            case({
                secondChecks++
                true
            }) { then(action(onStart = { unselectedStarts++ })) },
            otherwise { then(action(onStart = { unselectedStarts++ })) },
        )

        firstMatch.start()
        firstMatch.tick()

        assertEquals(listOf("first"), events)
        assertEquals(0, secondChecks)
        assertEquals(0, unselectedStarts)
        assertTrue(firstMatch.done)

        val noMatch = Animate()
            .thenWhen(case({ false }) { then(mark("unreachable", events)) })
            .then(mark("skipped", events))
        noMatch.start()
        noMatch.tick()
        noMatch.tick()

        assertEquals(listOf("first", "skipped"), events)

        val fallback = Animate().thenWhen(
            case({ false }) { then(mark("unreachable", events)) },
            otherwise { then(mark("otherwise", events)) },
        )
        fallback.start()
        fallback.tick()

        assertEquals(listOf("first", "skipped", "otherwise"), events)
    }

    @Test
    fun `thenWhenBlocked waits locks selected branch and rejects otherwise`() {
        var firstReady = false
        var secondReady = false
        var firstChecks = 0
        var secondChecks = 0
        val events = mutableListOf<String>()
        val animate = Animate()
            .thenWhenBlocked(
                case({
                    firstChecks++
                    firstReady
                }) {
                    then(action(2, onStart = { events.add("first") }))
                },
                case({
                    secondChecks++
                    secondReady
                }) {
                    then(mark("second", events))
                },
            )
            .then(mark("after", events))

        animate.start()
        animate.tick()
        assertFalse(animate.done)
        assertEquals(1, firstChecks)
        assertEquals(1, secondChecks)

        firstReady = true
        animate.tick()
        assertEquals(listOf("first"), events)

        firstReady = false
        secondReady = true
        animate.tick()
        animate.tick()

        assertEquals(listOf("first", "after"), events)
        assertEquals(2, firstChecks)
        assertEquals(1, secondChecks)
        assertTrue(animate.done)

        assertFailsWith<IllegalArgumentException> {
            Animate().thenWhenBlocked(otherwise { then(mark("invalid", events)) })
        }
    }

    @Test
    fun `cancel terminates nested branches and invokes onDone once`() {
        var leftDone = 0
        var rightDone = 0
        var afterStarts = 0
        val animate = Animate()
            .thenParallel(
                branch { then(action(10, onDone = { leftDone++ })) },
                branch { then(action(10, onDone = { rightDone++ })) },
            )
            .then(action(onStart = { afterStarts++ }))

        animate.start()
        animate.tick()
        animate.cancel()
        animate.cancel()

        assertEquals(1, leftDone)
        assertEquals(1, rightDone)
        assertEquals(0, afterStarts)
        assertTrue(animate.done)
        assertFalse(animate.display)
    }

    @Test
    fun `skip completes current stage and continues outer chain`() {
        var currentDone = 0
        var afterStarts = 0
        val animate = Animate()
            .then(action(10, onDone = { currentDone++ }))
            .then(action(1, onStart = { afterStarts++ }))

        animate.start()
        animate.tick()
        animate.skip()

        assertEquals(1, currentDone)
        assertEquals(1, afterStarts)

        animate.tick()

        assertEquals(1, currentDone)
        assertTrue(animate.done)
    }

    @Test
    fun `replay resets loops conditions parallel branches and callbacks`() {
        var chooseFirst = true
        val iterations = mutableListOf<Int>()
        val choices = mutableListOf<String>()
        var parallelDone = 0
        val animate = Animate()
            .thenLoop(2) { iterationIndex ->
                then(action(onStart = { iterations.add(iterationIndex) }))
            }
            .thenWhen(
                case({ chooseFirst }) { then(mark("first", choices)) },
                otherwise { then(mark("other", choices)) },
            )
            .thenParallel(
                action(1, onDone = { parallelDone++ }),
                action(2, onDone = { parallelDone++ }),
            )

        animate.start()
        tickUntilDone(animate)
        chooseFirst = false
        animate.start()
        tickUntilDone(animate)

        assertEquals(listOf(0, 1, 0, 1), iterations)
        assertEquals(listOf("first", "other"), choices)
        assertEquals(4, parallelDone)
    }

    @Test
    fun `start while running finishes current playback before replay`() {
        var starts = 0
        var dones = 0
        val animate = Animate().then(
            action(
                durationTicks = 3,
                onStart = { starts++ },
                onDone = { dones++ },
            )
        )

        animate.start()
        animate.tick()
        animate.start()

        assertEquals(1, starts)
        assertEquals(1, dones)

        animate.tick()
        animate.cancel()

        assertEquals(2, starts)
        assertEquals(2, dones)
    }

    @Test
    fun `action canceled during tick invokes onDone exactly once`() {
        var doneCalls = 0
        val events = mutableListOf<String>()
        lateinit var cancelingAction: AnimateAction
        cancelingAction = action(
            durationTicks = 10,
            onDone = {
                doneCalls++
                events.add("done:$tickCount")
            },
        ) {
            events.add("tick:$tickCount")
            cancel()
            events.add("tick-return")
        }
        val animate = Animate().then(cancelingAction)

        animate.start()
        animate.tick()
        animate.cancel()

        assertEquals(1, doneCalls)
        assertEquals(listOf("tick:0", "tick-return", "done:1"), events)
    }

    @Test
    fun `framework evaluates custom completion at most once per active tick`() {
        val custom = CompletionCountingAction()
        val animate = Animate().then(custom)

        animate.start()
        animate.tick()
        animate.tick()

        assertEquals(2, custom.checkCalls)
        assertEquals(1, custom.doneCalls)
        assertTrue(animate.done)
    }

    @Test
    fun `precompleted delayed action runs onDone without running tick`() {
        val events = mutableListOf<String>()
        val completed = object : AnimateAction() {
            override fun checkDone(): Boolean = true

            override fun tick() {
                events.add("tick")
            }

            override fun onStart() {
                events.add("start")
            }

            override fun onDone() {
                events.add("done")
            }
        }.apply {
            timeInterval = 10
        }
        val animate = Animate().addNode(AnimateNode().addAction(completed))

        animate.start()
        animate.tick()

        assertEquals(listOf("start", "done"), events)
        assertTrue(animate.done)
    }

    @Test
    fun `cancel does not invoke callbacks for pending actions`() {
        var starts = 0
        var dones = 0
        val animate = Animate().then(
            action(
                durationTicks = 1,
                onStart = { starts++ },
                onDone = { dones++ },
            ),
            delayTicks = 10,
        )

        animate.start()
        animate.cancel()

        assertEquals(0, starts)
        assertEquals(0, dones)
        assertTrue(animate.done)
    }

    @Test
    fun `cancel during onStart stops remaining parallel actions`() {
        var firstDone = 0
        var secondStarts = 0
        var secondDone = 0
        lateinit var animate: Animate
        animate = Animate().thenParallel(
            action(
                onStart = { animate.cancel() },
                onDone = { firstDone++ },
            ),
            action(
                onStart = { secondStarts++ },
                onDone = { secondDone++ },
            ),
        )

        animate.start()
        animate.tick()

        assertEquals(1, firstDone)
        assertEquals(0, secondStarts)
        assertEquals(0, secondDone)
        assertTrue(animate.done)
        assertFalse(animate.display)
    }

    @Test
    fun `cancel during onTick ends current action without advancing next stage`() {
        var doneCalls = 0
        var nextStarts = 0
        lateinit var animate: Animate
        animate = Animate()
            .then(
                action(
                    durationTicks = 10,
                    onDone = { doneCalls++ },
                ) {
                    animate.cancel()
                }
            )
            .then(action(onStart = { nextStarts++ }))

        animate.start()
        animate.tick()

        assertEquals(1, doneCalls)
        assertEquals(0, nextStarts)
        assertTrue(animate.done)
        assertFalse(animate.display)
    }

    private fun mark(name: String, events: MutableList<String>): AnimateAction {
        return action(onStart = { events.add(name) })
    }

    private fun tickUntilDone(animate: Animate, limit: Int = 100) {
        repeat(limit) {
            if (animate.done) return
            animate.tick()
        }
        error("Animation did not complete within $limit ticks")
    }

    private class CompletionCountingAction : AnimateAction() {
        var checkCalls = 0
        var doneCalls = 0

        override fun checkDone(): Boolean {
            checkCalls++
            return false
        }

        override fun tick() {
            if (tickCount == 1) {
                cancel()
            }
        }

        override fun onStart() = Unit

        override fun onDone() {
            doneCalls++
        }
    }
}
