package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.runtime.ClientRenderEntityRegistry
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import net.minecraft.resources.ResourceLocation

object DemoWorldRenderEffectClientRegistry {
    fun register() {
        register(DemoIrisStraightLaserRenderEntity.ID) {
            DemoIrisStraightLaserRenderEntityRenderer()
        }
        register(DemoBlackHoleRenderEntity.ID) {
            DemoBlackHoleRenderEntityRenderer()
        }
        register(DemoShieldRenderEntity.ID) {
            DemoShieldRenderEntityRenderer()
        }
        register(DemoLightBeamRenderEntity.ID) {
            DemoLightBeamRenderEntityRenderer()
        }
        register(DemoLightOrbRenderEntity.ID) {
            DemoLightOrbRenderEntityRenderer()
        }
        register(DemoWaterBallRenderEntity.ID) {
            DemoWaterBallRenderEntityRenderer()
        }
    }

    private fun register(
        id: ResourceLocation,
        rendererFactory: () -> RenderEntityRenderer<out RenderEntity>
    ) {
        val type = ClientRenderEntityRegistry.get(id) ?: return
        if (type.rendererFactory != null) return
        ClientRenderEntityRegistry.registerRenderer(id) {
            rendererFactory()
        }
    }
}
