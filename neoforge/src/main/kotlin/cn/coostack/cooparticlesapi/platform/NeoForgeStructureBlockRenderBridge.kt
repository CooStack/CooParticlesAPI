package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.entities.structure.client.StructureBlockRenderBridge
import cn.coostack.cooparticlesapi.entities.structure.client.StructureFluidVertices
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.BlockModelRenderState
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.block.FluidRenderer
import net.minecraft.client.renderer.block.model.BlockDisplayContext
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState

/** NeoForge 使用原版模型解析器构建结构局部方块的多层帧提交。 */
class NeoForgeStructureBlockRenderBridge : StructureBlockRenderBridge {

    override fun submit(
        view: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        matrices: PoseStack,
        collector: SubmitNodeCollector,
        random: RandomSource,
        overlay: Int
    ) {
        val renderState = BlockModelRenderState()
        Minecraft.getInstance().blockModelResolver.update(renderState, state, BlockDisplayContext.create())
        renderState.submitMultiLayer(matrices, collector, LightCoordsUtil.getLightCoords(view, pos), overlay, 0)
    }

    override fun submitFluid(
        view: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        fluidState: FluidState,
        matrices: PoseStack,
        collector: SubmitNodeCollector,
        overlay: Int
    ) {
        val models = Minecraft.getInstance().modelManager.fluidStateModelSet
        val renderer = FluidRenderer(models)
        val verticesByLayer = linkedMapOf<ChunkSectionLayer, NeoStructureFluidVertices>()
        val output = FluidRenderer.Output { layer ->
            verticesByLayer.getOrPut(layer) { NeoStructureFluidVertices() }
        }
        val custom = models.get(fluidState).customRenderer()
        if (custom == null || !custom.renderFluid(renderer, fluidState, view, pos, output, state)) {
            renderer.tesselate(view, pos, output, state, fluidState)
        }
        val capturedPos = pos.immutable()
        val light = LightCoordsUtil.getLightCoords(view, pos)
        verticesByLayer.forEach { (layer, vertices) ->
            val renderType = when (layer) {
                ChunkSectionLayer.SOLID -> RenderTypes.solidMovingBlock()
                ChunkSectionLayer.CUTOUT -> RenderTypes.cutoutMovingBlock()
                ChunkSectionLayer.TRANSLUCENT -> RenderTypes.translucentMovingBlock()
            }
            collector.submitCustomGeometry(matrices, renderType) { pose, target ->
                vertices.replay(StructureFluidVertices(target, pose, capturedPos, overlay, light))
            }
        }
    }

}
