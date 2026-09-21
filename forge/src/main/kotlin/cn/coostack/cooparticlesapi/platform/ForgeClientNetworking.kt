package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.CooPacketEnvelopeC2S

class ForgeClientNetworking : ClientNetworking {
    override fun send(packet: CooPacket) {
        CooClientPacketManager.sendTo(packet)
    }
}
