package cn.coostack.cooparticlesapi.renderer.model

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.runtime.RenderTypeRenderInput
import net.minecraft.client.renderer.RenderType
import com.mojang.blaze3d.vertex.VertexFormatElement
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
            val format = renderType.format()
            primitive.vertices.forEach { vertex ->
                val entry = consumer.addVertex(
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

                if (format.contains(VertexFormatElement.UV0)) {
                    entry.setUv(vertex.uv.x, vertex.uv.y)
                }
                if (format.contains(VertexFormatElement.UV1)) {
                    entry.setUv1(vertex.uv1.x, vertex.uv1.y)
                }
                if (format.contains(VertexFormatElement.UV2)) {
                    entry.setUv2(vertex.uv2.x, vertex.uv2.y)
                }
                if (format.contains(VertexFormatElement.NORMAL)) {
                    entry.setNormal(
                        pose,
                        vertex.normal.x,
                        vertex.normal.y,
                        vertex.normal.z
                    )
                }
            }
        }
    }

    private fun Float.toColorChannel(): Int {
        return (coerceIn(0f, 1f) * 255f).roundToInt()
    }
}
