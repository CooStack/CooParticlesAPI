package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelExecutors
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelLayer
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelVertex
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object DemoWorldRenderModelSupport {

    fun buildModel(
        geometry: (
            RenderEntityModelBuilder,
            RenderEntityModelLayer
        ) -> Unit
    ): RenderEntityModel {
        val model = RenderEntityModelBuilder()
        val baseLayer = model.layer("world_model")
        geometry(model, baseLayer)
        return model.build()
    }

    fun renderModel(input: RenderInput<*>, model: RenderEntityModel) {
        RenderEntityModelExecutors.active().draw(model, input)
    }

    fun circle(
        model: RenderEntityModelBuilder,
        layer: RenderEntityModelLayer,
        radius: Float,
        color: Vector4f,
        y: Float
    ) {
        val step = (2.0 * PI / DEFAULT_SEGMENTS).toFloat()
        for (index in 0 until DEFAULT_SEGMENTS) {
            val a = index * step
            val b = (index + 1) * step
            line(
                model,
                layer,
                Vector3f(cos(a) * radius, y, sin(a) * radius),
                Vector3f(cos(b) * radius, y, sin(b) * radius),
                color
            )
        }
    }

    fun disc(
        model: RenderEntityModelBuilder,
        layer: RenderEntityModelLayer,
        radius: Float,
        color: Vector4f,
        y: Float = 0F
    ) {
        val center = vertex(Vector3f(0F, y, 0F), color, Vector2f(0.5F, 0.5F))
        val step = (2.0 * PI / DEFAULT_SEGMENTS).toFloat()
        for (index in 0 until DEFAULT_SEGMENTS) {
            val a = index * step
            val b = (index + 1) * step
            model.addTriangle(
                layer,
                center,
                vertex(Vector3f(cos(a) * radius, y, sin(a) * radius), color),
                vertex(Vector3f(cos(b) * radius, y, sin(b) * radius), color)
            )
        }
    }

    fun annulus(
        model: RenderEntityModelBuilder,
        layer: RenderEntityModelLayer,
        innerRadius: Float,
        outerRadius: Float,
        color: Vector4f,
        y: Float = 0F
    ) {
        val step = (2.0 * PI / DEFAULT_SEGMENTS).toFloat()
        for (index in 0 until DEFAULT_SEGMENTS) {
            val a = index * step
            val b = (index + 1) * step
            model.addQuad(
                layer,
                vertex(Vector3f(cos(a) * innerRadius, y, sin(a) * innerRadius), color),
                vertex(Vector3f(cos(a) * outerRadius, y, sin(a) * outerRadius), color),
                vertex(Vector3f(cos(b) * outerRadius, y, sin(b) * outerRadius), color),
                vertex(Vector3f(cos(b) * innerRadius, y, sin(b) * innerRadius), color)
            )
        }
    }

    fun sphereShell(
        model: RenderEntityModelBuilder,
        layer: RenderEntityModelLayer,
        radius: Float,
        color: Vector4f,
        latSegments: Int = 8,
        lonSegments: Int = 36,
        yScale: Float = 1F
    ) {
        for (lat in 0 until latSegments) {
            val theta0 = (-PI / 2.0 + PI * lat / latSegments).toFloat()
            val theta1 = (-PI / 2.0 + PI * (lat + 1) / latSegments).toFloat()
            val y0 = sin(theta0) * radius * yScale
            val y1 = sin(theta1) * radius * yScale
            val r0 = cos(theta0) * radius
            val r1 = cos(theta1) * radius
            for (lon in 0 until lonSegments) {
                val phi0 = (2.0 * PI * lon / lonSegments).toFloat()
                val phi1 = (2.0 * PI * (lon + 1) / lonSegments).toFloat()
                model.addQuad(
                    layer,
                    vertex(Vector3f(cos(phi0) * r0, y0, sin(phi0) * r0), color),
                    vertex(Vector3f(cos(phi0) * r1, y1, sin(phi0) * r1), color),
                    vertex(Vector3f(cos(phi1) * r1, y1, sin(phi1) * r1), color),
                    vertex(Vector3f(cos(phi1) * r0, y0, sin(phi1) * r0), color)
                )
            }
        }
    }

    fun verticalBeam(
        model: RenderEntityModelBuilder,
        layer: RenderEntityModelLayer,
        halfWidth: Float,
        halfHeight: Float,
        color: Vector4f,
        planes: Int = 4
    ) {
        for (index in 0 until planes) {
            val angle = (PI * index / planes).toFloat()
            val dx = cos(angle) * halfWidth
            val dz = sin(angle) * halfWidth
            model.addQuad(
                layer,
                vertex(Vector3f(-dx, -halfHeight, -dz), color, Vector2f(0F, 0F)),
                vertex(Vector3f(dx, -halfHeight, dz), color, Vector2f(1F, 0F)),
                vertex(Vector3f(dx, halfHeight, dz), color, Vector2f(1F, 1F)),
                vertex(Vector3f(-dx, halfHeight, -dz), color, Vector2f(0F, 1F))
            )
        }
    }

    fun spiral(
        model: RenderEntityModelBuilder,
        layer: RenderEntityModelLayer,
        innerRadius: Float,
        outerRadius: Float,
        turns: Float,
        color: Vector4f,
        y: Float = 0F
    ) {
        val segments = 96
        for (index in 0 until segments) {
            val t0 = index.toFloat() / segments.toFloat()
            val t1 = (index + 1).toFloat() / segments.toFloat()
            val r0 = innerRadius + (outerRadius - innerRadius) * t0
            val r1 = innerRadius + (outerRadius - innerRadius) * t1
            val a0 = turns * 2.0F * PI.toFloat() * t0
            val a1 = turns * 2.0F * PI.toFloat() * t1
            line(
                model,
                layer,
                Vector3f(cos(a0) * r0, y + sin(t0 * PI.toFloat()) * outerRadius * 0.08F, sin(a0) * r0),
                Vector3f(cos(a1) * r1, y + sin(t1 * PI.toFloat()) * outerRadius * 0.08F, sin(a1) * r1),
                color
            )
        }
    }

    fun sphereGuideRings(
        model: RenderEntityModelBuilder,
        layer: RenderEntityModelLayer,
        radius: Float,
        color: Vector4f
    ) {
        circle(model, layer, radius, color, y = 0F)
        verticalCircleX(model, layer, radius, color)
        verticalCircleZ(model, layer, radius, color)
    }

    fun line(
        model: RenderEntityModelBuilder,
        layer: RenderEntityModelLayer,
        from: Vector3f,
        to: Vector3f,
        color: Vector4f
    ) {
        model.addVertex(layer, from, color)
        model.addVertex(layer, to, color)
    }

    fun alpha(color: Vector4f, scale: Float): Vector4f {
        return Vector4f(color.x, color.y, color.z, color.w * scale)
    }

    fun boosted(color: Vector4f, rgbScale: Float, alphaScale: Float = 1F): Vector4f {
        return Vector4f(color.x * rgbScale, color.y * rgbScale, color.z * rgbScale, color.w * alphaScale)
    }

    private fun verticalCircleX(
        model: RenderEntityModelBuilder,
        layer: RenderEntityModelLayer,
        radius: Float,
        color: Vector4f
    ) {
        val step = (2.0 * PI / DEFAULT_SEGMENTS).toFloat()
        for (index in 0 until DEFAULT_SEGMENTS) {
            val a = index * step
            val b = (index + 1) * step
            line(
                model,
                layer,
                Vector3f(0F, cos(a) * radius, sin(a) * radius),
                Vector3f(0F, cos(b) * radius, sin(b) * radius),
                color
            )
        }
    }

    private fun verticalCircleZ(
        model: RenderEntityModelBuilder,
        layer: RenderEntityModelLayer,
        radius: Float,
        color: Vector4f
    ) {
        val step = (2.0 * PI / DEFAULT_SEGMENTS).toFloat()
        for (index in 0 until DEFAULT_SEGMENTS) {
            val a = index * step
            val b = (index + 1) * step
            line(
                model,
                layer,
                Vector3f(cos(a) * radius, sin(a) * radius, 0F),
                Vector3f(cos(b) * radius, sin(b) * radius, 0F),
                color
            )
        }
    }

    private fun vertex(
        position: Vector3f,
        color: Vector4f,
        uv: Vector2f = Vector2f(0F, 0F)
    ): RenderEntityModelVertex {
        return RenderEntityModelVertex(
            position = position,
            color = color,
            uv = uv
        )
    }

    private const val DEFAULT_SEGMENTS = 48
}
