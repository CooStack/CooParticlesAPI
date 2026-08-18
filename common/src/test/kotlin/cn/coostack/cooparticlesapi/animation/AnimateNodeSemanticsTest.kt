package cn.coostack.cooparticlesapi.animation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimateNodeSemanticsTest {
    @Test
    fun `root nodes run in parallel from animate start`() {
        val events = mutableListOf<String>()
        val animate = Animate()
            .addNode(AnimateNode().addAction(recordingAction("a", 2, events)))
            .addNode(AnimateNode().addAction(recordingAction("b", 2, events)))

        animate.start()
        animate.tick()

        assertEquals(listOf("a:0", "b:0"), events)
        assertFalse(animate.done)

        animate.tick()

        assertEquals(listOf("a:0", "b:0", "a:1", "b:1"), events)
        assertTrue(animate.done)
    }

    @Test
    fun `actions in one node run in parallel and node waits for all`() {
        val events = mutableListOf<String>()
        val node = AnimateNode()
            .addAction(recordingAction("fast", 1, events))
            .addAction(recordingAction("slow", 2, events))
        node.addNode().addAction(recordingAction("child", 1, events))
        val animate = Animate().addNode(node)

        animate.start()
        animate.tick()
        animate.tick()

        assertEquals(listOf("fast:0", "slow:0", "slow:1"), events)

        animate.tick()

        assertEquals(listOf("fast:0", "slow:0", "slow:1", "child:0"), events)
        assertTrue(animate.done)
    }

    @Test
    fun `sibling child nodes start together after parent completes`() {
        val events = mutableListOf<String>()
        val parent = AnimateNode().addAction(recordingAction("parent", 1, events))
        parent.addNode().addAction(recordingAction("left", 1, events))
        parent.addNode().addAction(recordingAction("right", 1, events))
        val animate = Animate().addNode(parent)

        animate.start()
        animate.tick()
        animate.tick()

        assertEquals(listOf("parent:0", "left:0", "right:0"), events)
        assertTrue(animate.done)
    }

    @Test
    fun `root and child intervals use their documented reference points`() {
        val events = mutableListOf<String>()
        val parent = AnimateNode().addAction(recordingAction("parent", 1, events))
        parent.addNode(AnimateNode().addAction(recordingAction("child", 1, events)), 1)
        val animate = Animate()
            .addNode(parent, 1)
            .addNode(AnimateNode().addAction(recordingAction("root", 1, events)), 2)

        animate.start()
        repeat(4) { animate.tick() }

        assertEquals(listOf("parent:0", "root:0", "child:0"), events)
        assertTrue(animate.done)
    }

    private fun recordingAction(
        name: String,
        durationTicks: Int,
        events: MutableList<String>,
    ): AnimateAction {
        return action(durationTicks) { events.add("$name:$tickCount") }
    }
}
