package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S
import kotlin.math.round

internal data class TestControllerPickRequest(
    val screenPacket: PacketOpenTestControllerScreenS2C,
    val packet: PacketUpdateTestControllerC2S,
    val kind: TestControllerPickKind,
    val precisionUnlocked: Boolean
) {
    fun format(value: Double): Double {
        if (precisionUnlocked) {
            return value
        }
        val scaled = round(value * 1_000_000.0)
        return scaled / 1_000_000.0
    }
}
