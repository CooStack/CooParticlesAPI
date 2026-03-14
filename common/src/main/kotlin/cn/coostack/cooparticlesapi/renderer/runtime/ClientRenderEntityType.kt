package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

data class ClientRenderEntityType(
    val codec: StreamCodec<FriendlyByteBuf, RenderEntity>,
    val rendererFactory: () -> RenderEntityRenderer<out RenderEntity>
)
