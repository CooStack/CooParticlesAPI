package cn.coostack.cooparticlesapi.entities.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.entities.TestRenderEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation

class TestRenderEntityRenderer(context: EntityRendererProvider.Context) : EntityRenderer<TestRenderEntity>(context) {
    override fun getTextureLocation(location: TestRenderEntity): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "NONE")
    }

    override fun render(
        entity: TestRenderEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {



    }

}