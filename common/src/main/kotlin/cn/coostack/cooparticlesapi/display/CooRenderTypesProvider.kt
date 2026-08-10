package cn.coostack.cooparticlesapi.display

import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline

interface CooRenderTypesProvider {
    fun glow(): RenderType

    fun entityCutoutEmissive(texture: ResourceLocation): RenderType {
        return entityCutoutEmissive(texture, 1F)
    }

    fun entityCutoutEmissive(texture: ResourceLocation, brightness: Float): RenderType

    fun entityCutoutEmissive(texture: ResourceLocation, brightness: Float, alpha: Float): RenderType {
        return entityCutoutEmissive(texture, brightness)
    }

    fun create(descriptor: CooRenderTypeDescriptor): RenderType

    /** 为区块批处理创建使用扩展方块顶点格式的 terrain layer。 */
    fun terrain(pipeline: CooRenderPipeline<BlockState>, baseLayer: RenderType): RenderType

    fun named(id: ResourceLocation): RenderType? {
        return CooRenderTypeResourceRegistry.get(id)?.let(::create)
    }

    fun layered(name: String, vararg layers: RenderType): CooLayeredRenderType {
        return CooLayeredRenderType(name, layers.toList())
    }

    fun layered(descriptor: CooLayeredRenderTypeDescriptor): CooLayeredRenderType {
        return CooLayeredRenderType(
            descriptor.name,
            descriptor.layers.map(::create)
        )
    }

    fun layered(id: ResourceLocation): CooLayeredRenderType? {
        return CooRenderTypeResourceRegistry.getLayered(id)?.let(::layered)
    }
}
