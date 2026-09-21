package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.network.PacketByteBuf

data class ClientRenderEntityType(
    val codec: ForgeStreamCodec<PacketByteBuf, RenderEntity>,
    val rendererFactory: (() -> RenderEntityRenderer<out RenderEntity>)? = null
)
