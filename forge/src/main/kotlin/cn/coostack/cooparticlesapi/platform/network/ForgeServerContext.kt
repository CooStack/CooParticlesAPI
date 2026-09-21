package cn.coostack.cooparticlesapi.platform.network

import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import net.minecraft.server.level.ServerPlayer

class ForgeServerContext(
    val sender: ServerPlayer,
    val packet: cn.coostack.cooparticlesapi.network.packet.api.CooPacket,
    val kind: cn.coostack.cooparticlesapi.network.packet.api.CooPacketKind,
    val correlationId: Long,
    val timeoutTicks: Int,
) : cn.coostack.cooparticlesapi.platform.network.ServerContext {
    override fun player(): net.minecraft.world.entity.player.Player = sender
    override fun server(): net.minecraft.server.MinecraftServer = sender.server
    override fun reply(packet: CooPacket) {
        CooServerPacketManager.replyInternal(sender, packet, correlationId)
    }
}
