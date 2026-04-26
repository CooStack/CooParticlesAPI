package cn.coostack.cooparticlesapi.display

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType

data class CooLayeredRenderTypeDescriptor(
    val name: String,
    val layers: List<CooRenderTypeDescriptor>
)

class CooLayeredRenderType(
    val name: String,
    val layers: List<RenderType>
) {
    fun consumer(bufferSource: MultiBufferSource): VertexConsumer {
        return LayeredVertexConsumer(layers.map(bufferSource::getBuffer))
    }
}

class LayeredVertexConsumer(
    private val delegates: List<VertexConsumer>
) : VertexConsumer {
    init {
        require(delegates.isNotEmpty()) { "layered vertex consumer requires at least one delegate" }
    }

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
        delegates.forEach { it.addVertex(x, y, z) }
        return this
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
        delegates.forEach { it.setColor(red, green, blue, alpha) }
        return this
    }

    override fun setUv(u: Float, v: Float): VertexConsumer {
        delegates.forEach { it.setUv(u, v) }
        return this
    }

    override fun setUv1(u: Int, v: Int): VertexConsumer {
        delegates.forEach { it.setUv1(u, v) }
        return this
    }

    override fun setUv2(u: Int, v: Int): VertexConsumer {
        delegates.forEach { it.setUv2(u, v) }
        return this
    }

    override fun setNormal(normalX: Float, normalY: Float, normalZ: Float): VertexConsumer {
        delegates.forEach { it.setNormal(normalX, normalY, normalZ) }
        return this
    }
}
