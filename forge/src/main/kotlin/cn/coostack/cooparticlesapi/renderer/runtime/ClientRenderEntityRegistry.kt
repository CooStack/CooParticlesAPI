package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation

object ClientRenderEntityRegistry {
    private val types = LinkedHashMap<ResourceLocation, ClientRenderEntityType>()
    private val automaticEntityClasses = LinkedHashMap<ResourceLocation, Class<out RenderEntity>>()
    private val renderers = LinkedHashMap<ResourceLocation, RenderEntityRenderer<out RenderEntity>>()

    @Synchronized
    fun register(id: ResourceLocation, type: ClientRenderEntityType) {
        if (types.containsKey(id)) {
            throw IllegalArgumentException(id.toString())
        }
        types[id] = type
    }

    fun register(
        id: ResourceLocation,
        codec: ForgeStreamCodec<PacketByteBuf, RenderEntity>,
        rendererFactory: (() -> RenderEntityRenderer<out RenderEntity>)? = null
    ) {
        register(id, ClientRenderEntityType(codec, rendererFactory))
    }

    @Synchronized
    fun registerRenderer(id: ResourceLocation, rendererFactory: () -> RenderEntityRenderer<out RenderEntity>) {
        val existing = types[id]
            ?: throw IllegalStateException("RenderEntity codec not registered: $id")
        types[id] = existing.copy(rendererFactory = rendererFactory)
        renderers.remove(id)
    }

    @Synchronized
    internal fun applyRegistrations(registrations: Map<ResourceLocation, AutomaticClientRenderEntityType>) {
        registrations.forEach { (id, registration) ->
            val existing = types[id]
            val existingEntityClass = automaticEntityClasses[id]
            check(existing == null || existingEntityClass == registration.entityClass) {
                "RenderEntity id ownership changed during automatic registration: $id"
            }
        }
        registrations.forEach { (id, registration) ->
            types[id] = registration.type
            automaticEntityClasses[id] = registration.entityClass
            renderers.remove(id)
        }
    }

    @Synchronized
    internal fun getAutomaticEntityClass(id: ResourceLocation): Class<out RenderEntity>? {
        return automaticEntityClasses[id]
    }

    @Synchronized
    fun get(id: ResourceLocation): ClientRenderEntityType? {
        return types[id]
    }

    @Synchronized
    fun resolveRenderer(id: ResourceLocation): RenderEntityRenderer<out RenderEntity>? {
        renderers[id]?.let { return it }
        val factory = types[id]?.rendererFactory ?: return null
        return factory().also { renderer ->
            renderers[id] = renderer
        }
    }

    @Synchronized
    fun clear() {
        types.clear()
        automaticEntityClasses.clear()
        renderers.clear()
    }
}

internal data class AutomaticClientRenderEntityType(
    val type: ClientRenderEntityType,
    val entityClass: Class<out RenderEntity>
)
