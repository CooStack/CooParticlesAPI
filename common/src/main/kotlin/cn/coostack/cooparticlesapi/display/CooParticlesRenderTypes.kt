package cn.coostack.cooparticlesapi.display

import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

object CooParticlesRenderTypes {
    private val provider: CooRenderTypesProvider
        get() = CooParticlesServices.PLATFORM.getRenderTypesProvider()

    @JvmStatic
    fun glow(): RenderType {
        return provider.glow()
    }

    @JvmStatic
    fun entityCutoutEmissive(texture: ResourceLocation): RenderType {
        return provider.entityCutoutEmissive(texture)
    }

    @JvmStatic
    fun entityCutoutEmissive(texture: ResourceLocation, brightness: Float): RenderType {
        return provider.entityCutoutEmissive(texture, brightness)
    }

    @JvmStatic
    fun create(descriptor: CooRenderTypeDescriptor): RenderType {
        return provider.create(descriptor)
    }

    @JvmStatic
    fun named(id: ResourceLocation): RenderType? {
        return provider.named(id)
    }

    @JvmStatic
    fun layered(name: String, vararg layers: RenderType): CooLayeredRenderType {
        return provider.layered(name, *layers)
    }

    @JvmStatic
    fun layered(descriptor: CooLayeredRenderTypeDescriptor): CooLayeredRenderType {
        return provider.layered(descriptor)
    }

    @JvmStatic
    fun layered(id: ResourceLocation): CooLayeredRenderType? {
        return provider.layered(id)
    }
}
