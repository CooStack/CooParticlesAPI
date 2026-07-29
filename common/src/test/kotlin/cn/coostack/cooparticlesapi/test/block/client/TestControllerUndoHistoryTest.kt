package cn.coostack.cooparticlesapi.test.block.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 验证测试方块界面历史的去重、分叉和容量限制。 */
class TestControllerUndoHistoryTest {
    @Test
    fun recordsUndoRedoWithoutDuplicateStates() {
        val history = TestControllerUndoHistory(initial = 0)
        assertFalse(history.record(0))
        assertTrue(history.record(1))
        assertTrue(history.record(2))
        assertEquals(1, history.undo())
        assertEquals(0, history.undo())
        assertNull(history.undo())
        assertEquals(1, history.redo())
        assertEquals(2, history.redo())
        assertNull(history.redo())
    }

    @Test
    fun newRecordClearsRedoAndHonorsCapacity() {
        val history = TestControllerUndoHistory<Int>(capacity = 2, initial = 0)
        history.record(1)
        history.record(2)
        history.record(3)
        assertEquals(2, history.undo())
        assertEquals(1, history.undo())
        assertNull(history.undo())
        history.record(7)
        assertFalse(history.canRedo)
    }
}
