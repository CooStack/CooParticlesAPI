package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation

object PacketClearClientStateS2C {
    private val identifierID =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "clear_client_state")

    val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "clear_client_state")
    val CODEC = ForgeStreamCodec.of({ _, _ -> }, { _ -> PacketClearClientStateS2C })
}
