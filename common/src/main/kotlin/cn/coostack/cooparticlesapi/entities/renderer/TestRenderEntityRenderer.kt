package cn.coostack.cooparticlesapi.entities.renderer

import cn.coostack.cooparticlesapi.entities.TestRenderEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.util.LightCoordsUtil
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import kotlin.math.PI
import kotlin.math.sin

/** 测试实体的状态提取器与线框提交器。 */
class TestRenderEntityRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<TestRenderEntity, TestRenderEntityRenderState>(context) {
    override fun createRenderState(): TestRenderEntityRenderState = TestRenderEntityRenderState()

    override fun extractRenderState(
        entity: TestRenderEntity,
        state: TestRenderEntityRenderState,
        partialTick: Float,
    ) {
        super.extractRenderState(entity, state, partialTick)
        state.wave = sin((entity.level().gameTime.toDouble() + partialTick) * PI / 180.0).toFloat()
    }

    override fun submit(
        state: TestRenderEntityRenderState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        cameraState: CameraRenderState,
    ) {
        collector.submitCustomGeometry(poseStack, RenderTypes.LINES) { pose, vertices ->
            vertices.addVertex(pose, 0F, state.wave, 0F)
                .setColor(255, 255, 0, 255)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
            vertices.addVertex(pose, 1F, 1F, 1F)
                .setColor(0, 255, 255, 255)
            vertices.addVertex(pose, 1F, 0F, 1F)
                .setColor(0, 255, 255, 255)
            vertices.addVertex(pose, 2F, 2F, 2F)
                .setNormal(pose, 1F, 1F, 1F)
                .setColor(0, 255, 255, 110)
        }
    }
}

/**
 * 每帧从测试实体提取的波动顶点数据。
 *
 * @property wave 当前帧首顶点的纵向偏移
 */
class TestRenderEntityRenderState : EntityRenderState() {
    var wave = 0F
}
