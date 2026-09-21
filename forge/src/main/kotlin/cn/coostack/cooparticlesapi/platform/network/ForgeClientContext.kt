package cn.coostack.cooparticlesapi.platform.network

import net.minecraft.client.Minecraft

class ForgeClientContext(
    val packet: cn.coostack.cooparticlesapi.network.packet.api.CooPacket,
    val kind: cn.coostack.cooparticlesapi.network.packet.api.CooPacketKind,
    val correlationId: Long,
    val timeoutTicks: Int,
) : cn.coostack.cooparticlesapi.platform.network.ClientContext {
    override fun player(): net.minecraft.world.entity.player.Player = Minecraft.getInstance().player!!
    override fun client(): Minecraft = Minecraft.getInstance()
}
