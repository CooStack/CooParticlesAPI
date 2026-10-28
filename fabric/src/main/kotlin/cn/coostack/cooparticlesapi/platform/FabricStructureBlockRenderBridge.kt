package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.entities.structure.client.StructureBlockRenderBridge
import cn.coostack.cooparticlesapi.entities.structure.client.StructureFluidVertices
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.renderer.v1.Renderer
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricOrderedSubmitNodeCollector
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.block.FluidRenderer
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.core.BlockPos
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState

/** 将结构中的 Fabric 方块模型转换为 26.3 主渲染队列提交。 */
class FabricStructureBlockRenderBridge : StructureBlockRenderBridge {
    override fun submit(
        view: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        matrices: PoseStack,
        collector: SubmitNodeCollector,
        random: RandomSource,
        overlay: Int
    ) {
        val client = Minecraft.getInstance()
        val model = client.modelManager.blockStateModelSet.get(state)
        val modelRandom = random.fork().also { it.setSeed(state.getSeed(pos)) }
        val parts = ArrayList<BlockStateModelPart>()
        model.collectParts(modelRandom, parts)
        if (parts.isEmpty()) return

        val bakedRenderer = Renderer.get().altModelBlockRenderer(true, true, client.blockColors)
        val mutableMesh = Renderer.get().mutableMesh()
        bakedRenderer.tesselateBlock(
            mutableMesh.emitter(),
            0F,
            0F,
            0F,
            view,
            pos,
            state,
            model,
            state.getSeed(pos)
        )

        val tintSources = client.blockColors.getTintSources(state)
        val tintLayers = IntArray(tintSources.size) { index ->
            tintSources[index].colorInWorld(state, view, pos)
        }
        val translucent = model.hasMaterialFlag(
            view,
            pos,
            state,
            random.fork().also { it.setSeed(state.getSeed(pos)) },
            BakedQuad.FLAG_TRANSLUCENT
        )
        (collector as FabricOrderedSubmitNodeCollector).submitBlockModel(
            matrices,
            { layer: ChunkSectionLayer -> ChunkSectionLayerHelper.getMovingBlockRenderType(layer) },
            translucent,
            parts,
            mutableMesh.immutableCopy(),
            tintLayers,
            LightCoordsUtil.getLightCoords(view, pos),
            overlay,
            0
        )
    }

    override fun submitFluid(
        view: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        fluidState: FluidState,
        matrices: PoseStack,
        collector: SubmitNodeCollector,
        overlay: Int,
    ) {
        val client = Minecraft.getInstance()
        val models = client.modelManager.fluidStateModelSet
        val renderer = FluidRenderer(models)
        val layer = models.get(fluidState).layer()
        val light = LightCoordsUtil.getLightCoords(view, pos)
        collector.submitCustomGeometry(matrices, ChunkSectionLayerHelper.getMovingBlockRenderType(layer)) { pose, consumer ->
            val transformed = StructureFluidVertices(consumer, pose, pos, overlay, light)
            renderer.tesselate(view, pos, { transformed }, state, fluidState)
        }
    }
}
