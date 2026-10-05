package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.entities.structure.client.StructureBlockRenderBridge
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** Fabric 使用原版批次入口，非静态 FRAPI 模型不进入持久缓存。 */
class FabricStructureBlockRenderBridge : StructureBlockRenderBridge {
    override fun canCache(model: BakedModel): Boolean = (model as FabricBakedModel).isVanillaAdapter

    override fun render(view: BlockAndTintGetter, pos: BlockPos, state: BlockState, display: BlockEntity?,
                        matrices: PoseStack, buffers: MultiBufferSource, random: RandomSource, overlay: Int) {
        val client = Minecraft.getInstance()
        val blocks = client.blockRenderer
        if (overlay == OverlayTexture.NO_OVERLAY) {
            blocks.modelRenderer.tesselateBlock(view, blocks.getBlockModel(state), state, pos, matrices,
                buffers.getBuffer(ItemBlockRenderTypes.getMovingBlockRenderType(state)), true,
                random, state.getSeed(pos), overlay)
        } else {
            val color = client.blockColors.getColor(state, view, pos, 0)
            blocks.modelRenderer.renderModel(matrices.last(),
                buffers.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)), state, blocks.getBlockModel(state),
                (color shr 16 and 255) / 255F, (color shr 8 and 255) / 255F, (color and 255) / 255F,
                LevelRenderer.getLightColor(view, state, pos), overlay)
        }
    }
}
