package cn.coostack.cooparticlesapi.platform

interface ClientNetworking {
    fun send(packet: cn.coostack.cooparticlesapi.network.packet.api.CooPacket)
}