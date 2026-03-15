package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

object ClientRenderEntityRegistry {
    private val types = LinkedHashMap<ResourceLocation, ClientRenderEntityType>()

    fun register(id: ResourceLocation, type: ClientRenderEntityType) {
        if (types.containsKey(id)) {
            throw IllegalArgumentException(id.toString())
        }
        types[id] = type
    }

    fun register(
        id: ResourceLocation,
        codec: StreamCodec<FriendlyByteBuf, RenderEntity>,
        rendererFactory: (() -> RenderEntityRenderer<out RenderEntity>)? = null
    ) {
        register(id, ClientRenderEntityType(codec, rendererFactory))
    }

    fun registerRenderer(id: ResourceLocation, rendererFactory: () -> RenderEntityRenderer<out RenderEntity>) {
        val existing = types[id]
            ?: throw IllegalStateException("RenderEntity codec not registered: $id")
        types[id] = existing.copy(rendererFactory = rendererFactory)
    }

    fun get(id: ResourceLocation): ClientRenderEntityType? {
        return types[id]
    }

    fun clear() {
        types.clear()
    }
}
