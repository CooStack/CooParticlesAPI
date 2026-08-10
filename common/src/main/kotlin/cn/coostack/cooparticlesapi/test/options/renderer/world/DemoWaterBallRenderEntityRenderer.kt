package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelExecutors
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPrimitiveMode
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelVertex
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformProvider
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput
import net.minecraft.resources.ResourceLocation
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 水球演示实体的自定义 world shader renderer。 */
@CooAutoRegisterRenderer
class DemoWaterBallRenderEntityRenderer : RenderEntityRenderer<DemoWaterBallRenderEntity> {
    override val pipeline = CooPipelines.entity<DemoWaterBallRenderEntity>(id("render_entity/water_ball")) {
        world {
            vertex(id("core/vertex/render_entity_water_ball.vsh"))
            fragment(id("core/fragment/render_entity_water_ball.fsh"))
            inputTexture("noiseTex", id("noise.png"))
            uniform("time") { entity: DemoWaterBallRenderEntity -> entity.age.toFloat() }
            uniform("radius") { entity: DemoWaterBallRenderEntity -> entity.radius }
            uniform("intensity") { entity: DemoWaterBallRenderEntity -> entity.intensity }
            uniformValue(
                "tint",
                CooUniformProvider<DemoWaterBallRenderEntity> { entity ->
                    val color = entity.effectColor
                    CooUniformValue.Vec4Value(color.x, color.y, color.z, color.w)
                }
            )
        }
    }

    override fun render(input: RenderInput<DemoWaterBallRenderEntity>) {
        RenderEntityModelExecutors.active().draw(buildModel(input.entity), input)
    }

    private fun buildModel(entity: DemoWaterBallRenderEntity): RenderEntityModel {
        val model = RenderEntityModelBuilder()
        val layer = model.layer("water_ball")
        buildSphereVertices(entity).forEach { vertex ->
            model.addVertex(
                layer = layer,
                position = vertex.position,
                color = vertex.color,
                uv = vertex.uv,
                normal = vertex.normal,
                primitiveMode = RenderEntityModelPrimitiveMode.TRIANGLES
            )
        }
        return model.build()
    }

    private fun buildSphereVertices(entity: DemoWaterBallRenderEntity): List<RenderEntityModelVertex> {
        val latitudeSegments = 24
        val longitudeSegments = 64
        val verticesPerQuad = 6
        val vertices = ArrayList<RenderEntityModelVertex>(
            latitudeSegments * longitudeSegments * verticesPerQuad
        )
        val color = Vector4f(entity.effectColor)
        for (lat in 0 until latitudeSegments) {
            val v0 = lat.toFloat() / latitudeSegments.toFloat()
            val v1 = (lat + 1).toFloat() / latitudeSegments.toFloat()
            val theta0 = (-PI / 2.0 + PI * v0).toFloat()
            val theta1 = (-PI / 2.0 + PI * v1).toFloat()
            for (lon in 0 until longitudeSegments) {
                val u0 = lon.toFloat() / longitudeSegments.toFloat()
                val u1 = (lon + 1).toFloat() / longitudeSegments.toFloat()
                val phi0 = (2.0 * PI * u0).toFloat()
                val phi1 = (2.0 * PI * u1).toFloat()
                val first = sphereVertex(entity.radius, theta0, phi0, u0, v0, color)
                val second = sphereVertex(entity.radius, theta1, phi0, u0, v1, color)
                val third = sphereVertex(entity.radius, theta1, phi1, u1, v1, color)
                val fourth = sphereVertex(entity.radius, theta0, phi1, u1, v0, color)
                vertices += first
                vertices += second
                vertices += third
                vertices += first
                vertices += third
                vertices += fourth
            }
        }
        return vertices
    }

    private fun sphereVertex(
        radius: Float,
        theta: Float,
        phi: Float,
        u: Float,
        v: Float,
        color: Vector4f
    ): RenderEntityModelVertex {
        val ringRadius = cos(theta) * radius
        val normal = Vector3f(cos(phi) * cos(theta), sin(theta), sin(phi) * cos(theta))
        return RenderEntityModelVertex(
            position = Vector3f(cos(phi) * ringRadius, sin(theta) * radius, sin(phi) * ringRadius),
            color = Vector4f(color),
            uv = Vector2f(u, v),
            normal = normal
        )
    }

    companion object {
        private fun id(path: String): ResourceLocation {
            return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
        }
    }
}
