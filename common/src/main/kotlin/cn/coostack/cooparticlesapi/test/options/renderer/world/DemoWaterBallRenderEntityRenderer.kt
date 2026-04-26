package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.runtime.CompositeMode
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityVisualProfile
import cn.coostack.cooparticlesapi.renderer.runtime.WorldPassRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.vertex.DynamicVertexBuffer
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class DemoWaterBallRenderEntityRenderer :
    WorldPassRenderEntityRenderer<DemoWaterBallRenderEntity> {
    override fun initialize(instance: RenderEntityInstance<DemoWaterBallRenderEntity>) {
        ensureProgram()
        ensureBuffer()
        ensureTextures()
    }

    override fun describeFeatures(entity: DemoWaterBallRenderEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.WORLD_PASS),
            requestedSceneTargets = emptySet(),
            effectTypes = emptySet(),
            localRendererEnabled = true,
            effectGraphEnabled = false
        )
    }

    override fun createVisualProfile(entity: DemoWaterBallRenderEntity): RenderEntityVisualProfile {
        return RenderEntityVisualProfile(
            compositeMode = CompositeMode.ALPHA,
            needsSceneColorCopy = false,
            needsSceneDepth = false,
            renderPriority = 190
        )
    }

    override fun renderLocal(input: LocalRenderInput<DemoWaterBallRenderEntity>) {
        drawWaterBall(
            entity = input.instance.entity,
            tickDelta = input.tickDelta,
            viewMatrix = input.viewMatrix,
            projMatrix = input.projMatrix,
            modelMatrix = Matrix4f(input.modelMatrix)
        )
    }

    private fun drawWaterBall(
        entity: DemoWaterBallRenderEntity,
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        modelMatrix: Matrix4f
    ) {
        val shader = ensureProgram()
        val vertexBuffer = ensureBuffer()
        val textures = ensureTextures()
        val vertices = buildSphereVertices(entity)
        shader.useOnContext {
            setMatrix4("projMat", projMatrix)
            setMatrix4("viewMat", viewMatrix)
            setMatrix4("transMat", modelMatrix)
            setFloat("time", entity.getTime(tickDelta))
            setFloat("radius", entity.radius)
            setFloat("intensity", entity.intensity)
            setFloat4("tint", entity.effectColor)
            setInt("noiseTex", 0)
            vertexBuffer.setVertexes(vertices, CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT)
            textures.drawWith(
                Runnable {
                    vertexBuffer.draw()
                }
            )
        }
    }

    private fun buildSphereVertices(entity: DemoWaterBallRenderEntity): List<VertexData> {
        val vertices = ArrayList<VertexData>(LAT_SEGMENTS * LON_SEGMENTS * 6)
        val color = Vector4f(entity.effectColor)
        for (lat in 0 until LAT_SEGMENTS) {
            val v0 = lat.toFloat() / LAT_SEGMENTS.toFloat()
            val v1 = (lat + 1).toFloat() / LAT_SEGMENTS.toFloat()
            val theta0 = (-PI / 2.0 + PI * v0).toFloat()
            val theta1 = (-PI / 2.0 + PI * v1).toFloat()
            for (lon in 0 until LON_SEGMENTS) {
                val u0 = lon.toFloat() / LON_SEGMENTS.toFloat()
                val u1 = (lon + 1).toFloat() / LON_SEGMENTS.toFloat()
                val phi0 = (2.0 * PI * u0).toFloat()
                val phi1 = (2.0 * PI * u1).toFloat()
                val a = sphereVertex(entity.radius, theta0, phi0, u0, v0, color)
                val b = sphereVertex(entity.radius, theta1, phi0, u0, v1, color)
                val c = sphereVertex(entity.radius, theta1, phi1, u1, v1, color)
                val d = sphereVertex(entity.radius, theta0, phi1, u1, v0, color)
                vertices += a
                vertices += b
                vertices += c
                vertices += a
                vertices += c
                vertices += d
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
    ): VertexData {
        val ringRadius = cos(theta) * radius
        return VertexData(
            Vector3f(cos(phi) * ringRadius, sin(theta) * radius, sin(phi) * ringRadius),
            Vector4f(color),
            Vector2f(u, v)
        )
    }

    companion object {
        private const val LAT_SEGMENTS = 24
        private const val LON_SEGMENTS = 64

        private var program: CooShaderProgram? = null
        private var buffer: DynamicVertexBuffer? = null
        private var textures: SimpleTextures? = null

        private fun ensureProgram(): CooShaderProgram {
            val current = program
            if (current != null) {
                if (current.program == 0) current.init()
                return current
            }
            return AdvancedShaderProgramBuilder()
                .vertex("core/vertex/render_entity_water_ball.vsh")
                .fragment("core/fragment/render_entity_water_ball.fsh")
                .managedId("render_entity/water_ball")
                .build()
                .also {
                    it.init()
                    program = it
                }
        }

        private fun ensureBuffer(): DynamicVertexBuffer {
            return buffer ?: DynamicVertexBuffer().also {
                it.init()
                buffer = it
            }
        }

        private fun ensureTextures(): SimpleTextures {
            val current = textures
            if (current != null) {
                return current
            }
            return SimpleTextures().also {
                it.addTexture(
                    IdentifierTexture(
                        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "noise.png")
                    )
                )
                it.init()
                textures = it
            }
        }
    }
}
