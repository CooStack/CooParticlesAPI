package cn.coostack.cooparticlesapi.entities.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.entities.TestRenderEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.lighting.LightEngine
import kotlin.math.sin

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
        poseStack.pushPose()
        val buffer = bufferSource.getBuffer(RenderType.LINES)
        val pose = poseStack.last()
        buffer.addVertex(
            pose, 0f,
            sin(Math.toRadians((entity.level().gameTime.toDouble() + partialTick))).toFloat(),
            0f
        )
            .setNormal(pose, 0f, 1f, 0f)
            .setColor(255, 255, 0, 255)
            .setLight(LightEngine.MAX_LEVEL)
        buffer.addVertex(pose, 1f, 1f, 1f)
            .setNormal(pose, 0f, 1f, 0f)
            .setColor(0, 255, 255, 255)

        buffer.addVertex(pose, 1f, 0f, 1f)
            .setNormal(pose, 0f, 1f, 0f)
            .setColor(0, 255, 255, 255)

        buffer.addVertex(pose, 2f, 2f, 2f)
            .setNormal(1f, 1f, 1f)
            .setColor(0, 255, 255, 255)
            .setWhiteAlpha(110)


        poseStack.popPose()

    }
}