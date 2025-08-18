package cn.coostack.cooparticlesapi.renderer.client

import com.mojang.blaze3d.vertex.VertexConsumer

class AlphasVertexConsumers(var alpha: Int, val consumer: VertexConsumer) : VertexConsumer {
    override fun addVertex(
        x: Float,
        y: Float,
        z: Float
    ): VertexConsumer {
        return consumer.addVertex(x, y, z)
    }

    override fun setColor(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ): VertexConsumer {
        consumer.setColor(red, green, blue, this.alpha)
        return this
    }

    override fun setUv(u: Float, v: Float): VertexConsumer {
        consumer.setUv(u, v)
        return this
    }

    override fun setUv1(u: Int, v: Int): VertexConsumer {
        consumer.setUv1(u, v)
        return this
    }

    override fun setUv2(u: Int, v: Int): VertexConsumer {
        consumer.setUv2(u, v)
        return this
    }

    override fun setNormal(
        normalX: Float,
        normalY: Float,
        normalZ: Float
    ): VertexConsumer {
        consumer.setNormal(normalX, normalY, normalZ)
        return this
    }

}