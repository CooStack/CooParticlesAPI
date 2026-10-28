package cn.coostack.cooparticlesapi.display

import cn.coostack.cooparticlesapi.platform.CooClientServices
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier

object CooParticlesRenderTypes {
    private val provider: CooRenderTypesProvider
        get() = CooClientServices.RENDER_TYPES_PROVIDER

    @JvmStatic
    fun glow(): RenderType {
        return provider.glow()
    }

    @JvmStatic
    fun entityCutoutEmissive(texture: Identifier): RenderType {
        return provider.entityCutoutEmissive(texture)
    }

    @JvmStatic
    fun entityCutoutEmissive(texture: Identifier, brightness: Float): RenderType {
        return provider.entityCutoutEmissive(texture, brightness)
    }

    @JvmStatic
    fun create(descriptor: CooRenderTypeDescriptor): RenderType {
        return provider.create(descriptor)
    }

    @JvmStatic
    fun named(id: Identifier): RenderType? {
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
    fun layered(id: Identifier): CooLayeredRenderType? {
        return provider.layered(id)
    }
}
