package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证跨窗口草稿会保留当前 Option 索引对应的参数值。 */
class TestControllerPacketDraftsTest {
    @Test
    fun draftUsesSelectedOptionParameterValue() {
        val packet = PacketOpenTestControllerScreenS2C().also {
            it.selectedIndex = 1
            it.optionParamValues = listOf("first", "selected")
        }

        val draft = TestControllerPacketDrafts.draftFrom(packet)

        assertEquals(1, draft.optionParamIndex)
        assertEquals("selected", draft.optionParamValues)
    }

    @Test
    fun snapshotUpdatesOnlyEditedOptionParameterValue() {
        val packet = PacketOpenTestControllerScreenS2C().also {
            it.mode = "index"
            it.selectedIndex = 1
            it.optionParamValues = listOf("keep", "old")
        }
        val update = PacketUpdateTestControllerC2S().also {
            it.mode = "index"
            it.selectedIndex = 1
            it.optionParamIndex = 1
            it.optionParamValues = "new"
        }

        val restored = TestControllerPacketDrafts.snapshotFrom(packet, update).toPacket()

        assertEquals(listOf("keep", "new"), restored.optionParamValues)
    }
}
