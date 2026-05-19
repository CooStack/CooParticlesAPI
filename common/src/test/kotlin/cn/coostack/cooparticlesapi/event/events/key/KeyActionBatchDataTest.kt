package cn.coostack.cooparticlesapi.event.events.key

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyActionBatchDataTest {
    private val keyA = "test:a"
    private val keyB = "test:b"
    private val keyC = "test:c"
    private val missing = "test:missing"

    @Test
    fun `batch data exposes per-key actions press ticks and release state`() {
        val batch = KeyActionBatch(
            listOf(
                KeyActionData(keyA, listOf(KeyActionType.LONG_PRESS), 3, false),
                KeyActionData(keyB, listOf(KeyActionType.LONG_PRESS, KeyActionType.SINGLE_CLICK), 1, true),
                KeyActionData(keyC, listOf(KeyActionType.DOUBLE_CLICK), 2, true)
            )
        )

        assertContentEquals(listOf(keyA, keyB, keyC), batch.getKeys())
        assertContentEquals(listOf(KeyActionType.LONG_PRESS), batch.getAction(keyA))
        assertContentEquals(listOf(KeyActionType.LONG_PRESS, KeyActionType.SINGLE_CLICK), batch.getAction(keyB))
        assertContentEquals(listOf(KeyActionType.DOUBLE_CLICK), batch.getAction(keyC))
        assertEquals(3, batch.getPressTick(keyA))
        assertEquals(1, batch.getPressTick(keyB))
        assertEquals(2, batch.getPressTick(keyC))
        assertFalse(batch.isReleased(keyA))
        assertTrue(batch.isReleased(keyB))
        assertTrue(batch.isReleased(keyC))
        assertTrue(batch.isLongPress(keyA))
        assertFalse(batch.isSingleClick(keyA))
        assertTrue(batch.isLongPress(keyB))
        assertTrue(batch.isSingleClick(keyB))
        assertTrue(batch.isDoubleClick(keyC))
        assertFalse(batch.isLongPress(keyC))
    }

    @Test
    fun `missing keys return empty list zero tick and false booleans`() {
        val batch = KeyActionBatch(
            listOf(KeyActionData(keyA, listOf(KeyActionType.LONG_PRESS), 4, false))
        )

        assertContentEquals(emptyList(), batch.getAction(missing))
        assertEquals(0, batch.getPressTick(missing))
        assertFalse(batch.isReleased(missing))
        assertFalse(batch.isSingleClick(missing))
        assertFalse(batch.isDoubleClick(missing))
        assertFalse(batch.isLongPress(missing))
    }

    @Test
    fun `duplicate key entries merge actions in order and preserve release snapshot`() {
        val batch = KeyActionBatch(
            listOf(
                KeyActionData(keyA, listOf(KeyActionType.LONG_PRESS), 5, true),
                KeyActionData(keyA, listOf(KeyActionType.DOUBLE_CLICK), 5, true)
            )
        )

        assertContentEquals(listOf(keyA), batch.getKeys())
        assertContentEquals(
            listOf(KeyActionType.LONG_PRESS, KeyActionType.DOUBLE_CLICK),
            batch.getAction(keyA)
        )
        assertEquals(5, batch.getPressTick(keyA))
        assertTrue(batch.isReleased(keyA))
    }

    @Test
    fun `empty action entries are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            KeyActionData(keyA, emptyList(), 1, true)
        }
    }
}
