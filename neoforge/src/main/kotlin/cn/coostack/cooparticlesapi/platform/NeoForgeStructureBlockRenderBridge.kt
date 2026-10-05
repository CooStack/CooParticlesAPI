package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.entities.structure.client.StructureBlockRenderBridge
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.client.RenderTypeHelper
import net.neoforged.neoforge.client.model.data.ModelData

/** NeoForge 始终即时绘制，快照方块实体的 ModelData 和动态层不能作为静态网格缓存。 */
class NeoForgeStructureBlockRenderBridge : StructureBlockRenderBridge {
    override fun canCache(model: BakedModel): Boolean = false

    override fun render(view: BlockAndTintGetter, pos: BlockPos, state: BlockState, display: BlockEntity?,
                        matrices: PoseStack, buffers: MultiBufferSource, random: RandomSource, overlay: Int) {
        val blocks = Minecraft.getInstance().blockRenderer
        val model = blocks.getBlockModel(state)
        val data = model.getModelData(view, pos, state, display?.modelData ?: ModelData.EMPTY)
        val seed = state.getSeed(pos)
        random.setSeed(seed)
        for (layer in model.getRenderTypes(state, random, data)) {
            val target = if (overlay == OverlayTexture.NO_OVERLAY) RenderTypeHelper.getMovingBlockRenderType(layer)
                else RenderTypeHelper.getEntityRenderType(layer, false)
            matrices.pushPose()
            try {
                blocks.modelRenderer.tesselateBlock(view, model, state, pos, matrices, buffers.getBuffer(target),
                    true, random, seed, overlay, data, layer)
            } finally {
                matrices.popPose()
            }
        }
    }
}
