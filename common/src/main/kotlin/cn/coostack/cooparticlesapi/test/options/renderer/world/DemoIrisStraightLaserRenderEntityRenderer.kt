package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Iris 直线激光演示实体的本地渲染与代理模型 renderer。 */
@CooAutoRegisterRenderer
class DemoIrisStraightLaserRenderEntityRenderer : RenderEntityRenderer<DemoIrisStraightLaserRenderEntity> {
    override val pipeline = CooPipelines.DEFAULT

    override fun render(input: RenderInput<DemoIrisStraightLaserRenderEntity>) {
        initStatic()
        val entity = input.entity
        val start = entity.renderStart(input.tickDelta)
        val end = entity.renderEnd(input.tickDelta)
        val length = entity.beamLength(start, end)
        if (length <= DemoIrisStraightLaserRenderEntity.MIN_BEAM_LENGTH) {
            return
        }
        val alpha = entity.currentAlpha(input.tickDelta)
        if (alpha <= MIN_VISIBLE_ALPHA) {
            return
        }
        val pulse = 0.5F + 0.5F * sin((entity.age + input.tickDelta) * 0.42F)
        renderPasses(
            entity = entity,
            modelMatrix = orientedModelMatrix(input.modelMatrix, start, end, entity.pos),
            viewMatrix = input.viewMatrix,
            projMatrix = input.projMatrix,
            beamLength = length,
            radius = entity.currentRadius(input.tickDelta),
            passAlpha = alpha,
            pulse = pulse,
            phaseProgress = entity.currentPhaseProgress(input.tickDelta),
            collapse = entity.currentCollapse(input.tickDelta),
            time = entity.getTime(input.tickDelta)
        )
    }

    private fun renderPasses(
        entity: DemoIrisStraightLaserRenderEntity,
        modelMatrix: Matrix4f,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        beamLength: Float,
        radius: Float,
        passAlpha: Float,
        pulse: Float,
        phaseProgress: Float,
        collapse: Float,
        time: Float
    ) {
        if (passAlpha <= MIN_VISIBLE_ALPHA) {
            return
        }
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.depthMask(false)
        RenderSystem.blendFunc(770, 771)
        drawPass(
            entity = entity,
            modelMatrix = modelMatrix,
            viewMatrix = viewMatrix,
            projMatrix = projMatrix,
            beamLength = beamLength,
            beamRadius = radius * (1.18F + pulse * 0.10F),
            passColor = entity.color,
            passAlpha = (passAlpha * 0.34F).coerceAtMost(0.48F),
            brightness = 0.64F,
            phaseProgress = phaseProgress,
            collapse = collapse,
            time = time,
            layerMode = LAYER_OUTER_TEXTURE
        )
        drawPass(
            entity = entity,
            modelMatrix = modelMatrix,
            viewMatrix = viewMatrix,
            projMatrix = projMatrix,
            beamLength = beamLength,
            beamRadius = radius * 1.18F,
            passColor = mixColor(entity.color, Vector3f(1.0F, 0.97F, 0.90F), 0.28F),
            passAlpha = (passAlpha * 0.10F).coerceAtMost(0.18F),
            brightness = 0.74F,
            phaseProgress = phaseProgress,
            collapse = collapse,
            time = time,
            layerMode = LAYER_OUTER_TEXTURE
        )
        RenderSystem.blendFunc(770, 1)
        drawPass(
            entity = entity,
            modelMatrix = modelMatrix,
            viewMatrix = viewMatrix,
            projMatrix = projMatrix,
            beamLength = beamLength,
            beamRadius = radius * 0.30F,
            passColor = mixColor(entity.color, Vector3f(1.0F, 0.98F, 0.92F), 0.62F),
            passAlpha = (passAlpha * 0.12F).coerceAtMost(0.22F),
            brightness = 1.16F,
            phaseProgress = phaseProgress,
            collapse = collapse,
            time = time,
            layerMode = LAYER_INNER_GLOW
        )
        drawPass(
            entity = entity,
            modelMatrix = modelMatrix,
            viewMatrix = viewMatrix,
            projMatrix = projMatrix,
            beamLength = beamLength,
            beamRadius = radius * 2.80F,
            passColor = entity.color,
            passAlpha = (passAlpha * 0.13F).coerceAtMost(0.24F),
            brightness = 1.95F,
            phaseProgress = phaseProgress,
            collapse = collapse,
            time = time,
            layerMode = LAYER_OUTER_BLOOM
        )
    }

    private fun drawPass(
        entity: DemoIrisStraightLaserRenderEntity,
        modelMatrix: Matrix4f,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        beamLength: Float,
        beamRadius: Float,
        passColor: Vector3f,
        passAlpha: Float,
        brightness: Float,
        phaseProgress: Float,
        collapse: Float,
        time: Float,
        layerMode: Int
    ) {
        if (passAlpha <= MIN_VISIBLE_ALPHA) {
            return
        }
        ensureBeamGeometry(beamLength)
        beamShader.useOnContext {
            val brightnessScale = entity.brightness.coerceAtLeast(0F)
            val impactNoiseTexture = ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID,
                "textures/effect/straight_laser_impact_noise.png"
            )
            val previousTexture = RenderSystem.getShaderTexture(0)
            try {
                RenderSystem.setShaderTexture(0, impactNoiseTexture)
                setInt("impactNoise", 0)
                setMatrix4("modelMatrix", modelMatrix)
                setMatrix4("viewMatrix", viewMatrix)
                setMatrix4("projMatrix", projMatrix)
                setFloat("beamRadius", beamRadius.coerceAtLeast(DemoIrisStraightLaserRenderEntity.MIN_RADIUS))
                setFloat("beamLength", beamLength.coerceAtLeast(DemoIrisStraightLaserRenderEntity.MIN_BEAM_LENGTH))
                setFloat3("color", passColor)
                setFloat("alpha", (passAlpha * alphaMultiplierFromBrightness(brightnessScale)).coerceAtMost(1F))
                setFloat("brightness", brightness * brightnessScale * 0.82F)
                setFloat("phaseProgress", phaseProgress)
                setFloat("collapse", collapse)
                setFloat("time", time)
                setInt("layerMode", layerMode)
                beamVertexBuffer.draw()
            } finally {
                RenderSystem.setShaderTexture(0, previousTexture)
            }
        }
    }

    private fun orientedModelMatrix(
        baseMatrix: Matrix4f,
        start: Vec3,
        end: Vec3,
        anchor: Vec3
    ): Matrix4f {
        val delta = end.subtract(start)
        val direction = if (delta.lengthSqr() <= DemoIrisStraightLaserRenderEntity.MIN_DIRECTION_LENGTH_SQR) {
            Vec3(0.0, 1.0, 0.0)
        } else {
            delta.normalize()
        }
        return Matrix4f(baseMatrix).translate(
            (start.x - anchor.x).toFloat(),
            (start.y - anchor.y).toFloat(),
            (start.z - anchor.z).toFloat()
        ).rotate(
            Quaternionf().rotationTo(
                0F,
                1F,
                0F,
                direction.x.toFloat(),
                direction.y.toFloat(),
                direction.z.toFloat()
            )
        )
    }

    companion object {
        private const val MIN_VISIBLE_ALPHA = 0.001F
        private const val CONE_LENGTH_FRACTION = 0.10F
        private const val MAX_CONE_LENGTH = 10.0F
        private const val LAYER_OUTER_TEXTURE = 0
        private const val LAYER_INNER_GLOW = 1
        private const val LAYER_OUTER_BLOOM = 2

        private lateinit var beamVertexBuffer: SimpleVertexBuffer
        private lateinit var beamShader: CooShaderProgram
        private var initialized = false
        private var beamGeometryConeRatio = -1F

        private fun initStatic() {
            if (initialized) {
                return
            }
            beamVertexBuffer = SimpleVertexBuffer().apply {
                init()
                setVertexes(buildCylinderVertices(CONE_LENGTH_FRACTION), CooVertexFormat.POINT_FORMAT)
            }
            beamShader = ShaderProgramBuilder()
                .vertex("core/vertex/straight_laser_beam.vsh")
                .fragment("core/fragment/straight_laser_beam.fsh")
                .build()
            beamShader.init()
            initialized = true
        }

        private fun ensureBeamGeometry(beamLength: Float) {
            val coneRatio = (MAX_CONE_LENGTH / beamLength.coerceAtLeast(DemoIrisStraightLaserRenderEntity.MIN_BEAM_LENGTH))
                .coerceAtMost(CONE_LENGTH_FRACTION)
                .coerceIn(0.001F, 1.0F)
            if (abs(beamGeometryConeRatio - coneRatio) <= 0.0001F) {
                return
            }
            beamVertexBuffer.setVertexes(buildCylinderVertices(coneRatio), CooVertexFormat.POINT_FORMAT)
            beamGeometryConeRatio = coneRatio
        }

        private fun buildCylinderVertices(coneEndRatio: Float): List<VertexData> {
            val segments = 24
            val axialSegments = 96
            val yStops = buildAxialStops(axialSegments, coneEndRatio)
            val vertices = ArrayList<VertexData>(segments * (yStops.size - 1) * 6)
            for (segment in 0 until segments) {
                val angle0 = (PI.toFloat() * 2F * segment) / segments.toFloat()
                val angle1 = (PI.toFloat() * 2F * (segment + 1)) / segments.toFloat()
                val x0 = cos(angle0)
                val z0 = sin(angle0)
                val x1 = cos(angle1)
                val z1 = sin(angle1)
                for (axialSegment in 0 until yStops.size - 1) {
                    val y0 = yStops[axialSegment]
                    val y1 = yStops[axialSegment + 1]
                    val scale0 = coneRadiusScale(y0, coneEndRatio)
                    val scale1 = coneRadiusScale(y1, coneEndRatio)
                    val a = Vector3f(x0 * scale0, y0, z0 * scale0)
                    val b = Vector3f(x1 * scale0, y0, z1 * scale0)
                    val c = Vector3f(x1 * scale1, y1, z1 * scale1)
                    val d = Vector3f(x0 * scale1, y1, z0 * scale1)
                    appendQuad(vertices, a, b, c, d)
                }
            }
            return vertices
        }

        private fun buildAxialStops(axialSegments: Int, coneEndRatio: Float): List<Float> {
            val stops = ArrayList<Float>(axialSegments + 2)
            for (index in 0..axialSegments) {
                val y = index.toFloat() / axialSegments.toFloat()
                if (stops.none { abs(it - y) <= 0.0001F }) {
                    stops += y
                }
            }
            if (stops.none { abs(it - coneEndRatio) <= 0.0001F }) {
                stops += coneEndRatio
            }
            stops.sort()
            return stops
        }

        private fun coneRatio(length: Float): Float {
            return (MAX_CONE_LENGTH / length.coerceAtLeast(DemoIrisStraightLaserRenderEntity.MIN_BEAM_LENGTH))
                .coerceAtMost(CONE_LENGTH_FRACTION)
                .coerceIn(0.001F, 1.0F)
        }

        private fun coneRadiusScale(y: Float, coneEndRatio: Float): Float {
            if (y >= coneEndRatio) {
                return 1.0F
            }
            return DemoIrisStraightLaserRenderEntity.smoothstep(0.0F, coneEndRatio, y)
        }

        private fun appendQuad(
            output: MutableList<VertexData>,
            a: Vector3f,
            b: Vector3f,
            c: Vector3f,
            d: Vector3f
        ) {
            appendTriangle(output, a, b, c)
            appendTriangle(output, a, c, d)
        }

        private fun appendTriangle(
            output: MutableList<VertexData>,
            a: Vector3f,
            b: Vector3f,
            c: Vector3f
        ) {
            output += VertexData(a, Vector4f(), Vector2f())
            output += VertexData(b, Vector4f(), Vector2f())
            output += VertexData(c, Vector4f(), Vector2f())
        }

        private fun mixColor(from: Vector3f, to: Vector3f, alpha: Float): Vector3f {
            val t = alpha.coerceIn(0F, 1F)
            return Vector3f(
                DemoIrisStraightLaserRenderEntity.mix(from.x, to.x, t),
                DemoIrisStraightLaserRenderEntity.mix(from.y, to.y, t),
                DemoIrisStraightLaserRenderEntity.mix(from.z, to.z, t)
            )
        }

        private fun alphaMultiplierFromBrightness(brightness: Float): Float {
            if (brightness <= 1F) {
                return brightness.coerceIn(0.05F, 1F)
            }
            return (1F + (brightness - 1F) * 0.42F).coerceAtMost(3.5F)
        }
    }
}
