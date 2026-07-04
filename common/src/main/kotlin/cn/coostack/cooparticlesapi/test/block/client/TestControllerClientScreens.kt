package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenBoundTestSelectionScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import net.minecraft.client.Minecraft

object TestControllerClientScreens {
    fun openController(packet: PacketOpenTestControllerScreenS2C) {
        Minecraft.getInstance().setScreen(TestControllerScreen(packet))
    }

    fun openBoundSelection(packet: PacketOpenBoundTestSelectionScreenS2C) {
        Minecraft.getInstance().setScreen(BoundTestControllerSelectionScreen(packet))
    }
}
