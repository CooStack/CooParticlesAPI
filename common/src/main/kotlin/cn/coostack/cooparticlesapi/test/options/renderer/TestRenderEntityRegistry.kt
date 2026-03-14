package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.runtime.ClientRenderEntityRegistry
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

object TestRenderEntityRegistry {
    fun register() {
        registerEntity(TestRendererEntity.ID, TestRendererEntity(), ::TestRendererEntityRenderer)
        registerEntity(TestBillboardSmokeEntity.ID, TestBillboardSmokeEntity(), ::TestBillboardSmokeEntityRenderer)
        registerEntity(TestPersistentGlowSphereEntity.ID, TestPersistentGlowSphereEntity(), ::TestPersistentGlowSphereEntityRenderer)
        registerEntity(TestTexturedBeamEntity.ID, TestTexturedBeamEntity(), ::TestTexturedBeamEntityRenderer)
        registerEntity(TestAccretionDiskEntity.ID, TestAccretionDiskEntity(), ::TestAccretionDiskEntityRenderer)
        registerEntity(TestBlackHoleEntity.ID, TestBlackHoleEntity(), ::TestBlackHoleEntityRenderer)
    }

    private fun registerEntity(
        id: ResourceLocation,
        entity: RenderEntity,
        rendererFactory: () -> RenderEntityRenderer<out RenderEntity>
    ) {
        val existing = ClientRenderEntityRegistry.get(id)
        if (existing == null) {
            ClientRenderEntityRegistry.register(id, entityCodec(entity), rendererFactory)
        } else {
            ClientRenderEntityRegistry.registerRenderer(id, rendererFactory)
        }
    }

    private fun entityCodec(entity: RenderEntity): StreamCodec<FriendlyByteBuf, RenderEntity> {
        return entity.getCodec()
    }
}
