package cn.coostack.cooparticlesapi.network.packet.client

import cn.coostack.cooparticlesapi.event.events.key.KeyActionBatch
import cn.coostack.cooparticlesapi.event.events.key.KeyActionData
import cn.coostack.cooparticlesapi.event.events.key.KeyActionType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PacketKeyActionC2STest {
    @Test
    fun `unknown action ids fall back to single click`() {
        assertEquals(KeyActionType.SINGLE_CLICK, KeyActionType.fromId(-1))
        assertEquals(KeyActionType.SINGLE_CLICK, KeyActionType.fromId(99))
    }

    @Test
    fun `packet input batch stays unique and ordered before serialization`() {
        val batch = KeyActionBatch(
            listOf(
                KeyActionData("test:a", listOf(KeyActionType.LONG_PRESS), 4, true),
                KeyActionData("test:a", listOf(KeyActionType.DOUBLE_CLICK), 4, true),
                KeyActionData("test:b", listOf(KeyActionType.LONG_PRESS), 2, false)
            )
        )

        assertContentEquals(listOf("test:a", "test:b"), batch.getKeys())
        assertContentEquals(
            listOf(KeyActionType.LONG_PRESS, KeyActionType.DOUBLE_CLICK),
            batch.getAction("test:a")
        )
        assertEquals(4, batch.getPressTick("test:a"))
        assertTrue(batch.isReleased("test:a"))
        assertFalse(batch.isReleased("test:b"))
    }
}
