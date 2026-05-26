package cn.coostack.cooparticlesapi.renderer.model

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.runtime.RenderTypeRenderInput
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import kotlin.math.roundToInt

object RenderTypeRenderEntityModelExecutor {
    fun <T : RenderEntity> draw(
        model: RenderEntityModel,
        input: RenderTypeRenderInput<T>,
        renderTypeResolver: (RenderEntityModelPrimitive) -> RenderType?
    ) {
        val pose = input.poseStack.last()
        model.primitives.forEach { primitive ->
            if (primitive.vertices.isEmpty()) return@forEach
            val renderType = renderTypeResolver(primitive) ?: return@forEach
            val consumer = input.bufferSource.getBuffer(renderType)
            primitive.vertices.forEach { vertex ->
                consumer.addVertex(
                    pose.pose(),
                    vertex.position.x,
                    vertex.position.y,
                    vertex.position.z
                )
                    .setColor(
                        vertex.color.x.toColorChannel(),
                        vertex.color.y.toColorChannel(),
                        vertex.color.z.toColorChannel(),
                        vertex.color.w.toColorChannel()
                    )
                    .setUv(vertex.uv.x, vertex.uv.y)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(
                        pose,
                        vertex.normal.x,
                        vertex.normal.y,
                        vertex.normal.z
                    )
            }
        }
    }

    private fun Float.toColorChannel(): Int {
        return (coerceIn(0f, 1f) * 255f).roundToInt()
    }
}
