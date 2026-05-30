package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.display.CooParticlesRenderTypes
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityVisualProfile
import cn.coostack.cooparticlesapi.renderer.runtime.IrisRenderTypeProxyRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderTypeRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.WorldPassRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPipe
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPrimitive
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPrimitiveMode
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelVertex
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector2i
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class DemoIrisStraightLaserRenderEntityRenderer :
    WorldPassRenderEntityRenderer<DemoIrisStraightLaserRenderEntity>,
    IrisRenderTypeProxyRenderer<DemoIrisStraightLaserRenderEntity> {
    override fun initialize(instance: RenderEntityInstance<DemoIrisStraightLaserRenderEntity>) {
        initStatic()
    }

    override fun describeFeatures(entity: DemoIrisStraightLaserRenderEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.WORLD_PASS),
            localRendererEnabled = true,
            effectGraphEnabled = false
        )
    }

    override fun createVisualProfile(entity: DemoIrisStraightLaserRenderEntity): RenderEntityVisualProfile {
        return RenderEntityVisualProfile(renderPriority = 190)
    }

    override fun irisProxyRenderType(
        input: RenderTypeRenderInput<DemoIrisStraightLaserRenderEntity>,
        primitive: RenderEntityModelPrimitive
    ): RenderType? {
        if (primitive.primitiveMode != RenderEntityModelPrimitiveMode.QUADS) {
            return null
        }
        return CooParticlesRenderTypes.entityCutoutEmissive(
            LASER_IMPACT_NOISE_TEXTURE,
            input.instance.entity.brightness
        )
    }

    override fun buildIrisProxyModel(entity: DemoIrisStraightLaserRenderEntity, tickDelta: Float): RenderEntityModel {
        val start = entity.renderStart(tickDelta)
        val end = entity.renderEnd(tickDelta)
        val length = entity.beamLength(start, end)
        if (length <= DemoIrisStraightLaserRenderEntity.MIN_BEAM_LENGTH) {
            return RenderEntityModelBuilder().build()
        }
        val alpha = entity.currentAlpha(tickDelta) * entity.irisMaskAlpha.coerceIn(0f, 1f)
        if (alpha <= MIN_VISIBLE_ALPHA) {
            return RenderEntityModelBuilder().build()
        }
        val builder = RenderEntityModelBuilder()
        val maskPipe = builder.pipe(IRIS_MASK_PIPE)
        appendIrisProxyBeam(
            builder = builder,
            pipe = maskPipe,
            start = start,
            end = end,
            anchor = entity.pos,
            radius = entity.currentRadius(tickDelta) * 1.24f,
            color = Vector4f(entity.color.x, entity.color.y, entity.color.z, alpha),
            coneEndRatio = coneRatio(length),
            radialSegments = 24,
            axialSegments = 48,
            textureRepeat = MASK_TEXTURE_REPEAT
        )
        return builder.build()
    }

    override fun renderLocal(input: LocalRenderInput<DemoIrisStraightLaserRenderEntity>) {
        initStatic()
        val entity = input.instance.entity
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
        val pulse = 0.5f + 0.5f * sin((entity.age + input.tickDelta) * 0.42f)
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
        try {
            RenderSystem.blendFunc(770, 771)
            drawPass(
                entity = entity,
                modelMatrix = modelMatrix,
                viewMatrix = viewMatrix,
                projMatrix = projMatrix,
                beamLength = beamLength,
                beamRadius = radius * (1.18f + pulse * 0.10f),
                passColor = entity.color,
                passAlpha = (passAlpha * 0.34f).coerceAtMost(0.48f),
                brightness = 0.64f,
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
                beamRadius = radius * 1.18f,
                passColor = mixColor(entity.color, Vector3f(1.0f, 0.97f, 0.90f), 0.28f),
                passAlpha = (passAlpha * 0.10f).coerceAtMost(0.18f),
                brightness = 0.74f,
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
                beamRadius = radius * 0.30f,
                passColor = mixColor(entity.color, Vector3f(1.0f, 0.98f, 0.92f), 0.62f),
                passAlpha = (passAlpha * 0.12f).coerceAtMost(0.22f),
                brightness = 1.16f,
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
                beamRadius = radius * 2.80f,
                passColor = entity.color,
                passAlpha = (passAlpha * 0.13f).coerceAtMost(0.24f),
                brightness = 1.95f,
                phaseProgress = phaseProgress,
                collapse = collapse,
                time = time,
                layerMode = LAYER_OUTER_BLOOM
            )
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
            RenderSystem.enableCull()
        }
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
            val brightnessScale = entity.brightness.coerceAtLeast(0f)
            RenderSystem.setShaderTexture(0, LASER_IMPACT_NOISE_TEXTURE)
            setInt("impactNoise", 0)
            setMatrix4("modelMatrix", modelMatrix)
            setMatrix4("viewMatrix", viewMatrix)
            setMatrix4("projMatrix", projMatrix)
            setFloat("beamRadius", beamRadius.coerceAtLeast(DemoIrisStraightLaserRenderEntity.MIN_RADIUS))
            setFloat("beamLength", beamLength.coerceAtLeast(DemoIrisStraightLaserRenderEntity.MIN_BEAM_LENGTH))
            setFloat3("color", passColor)
            setFloat("alpha", (passAlpha * alphaMultiplierFromBrightness(brightnessScale)).coerceAtMost(1f))
            setFloat("brightness", brightness * brightnessScale * BRIGHTNESS_MULTIPLIER)
            setFloat("phaseProgress", phaseProgress)
            setFloat("collapse", collapse)
            setFloat("time", time)
            setInt("layerMode", layerMode)
            beamVertexBuffer.draw()
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
                0f,
                1f,
                0f,
                direction.x.toFloat(),
                direction.y.toFloat(),
                direction.z.toFloat()
            )
        )
    }

    private fun appendIrisProxyBeam(
        builder: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        start: Vec3,
        end: Vec3,
        anchor: Vec3,
        radius: Float,
        color: Vector4f,
        coneEndRatio: Float,
        radialSegments: Int,
        axialSegments: Int,
        textureRepeat: Float
    ) {
        val length = start.distanceTo(end).toFloat()
        if (length <= DemoIrisStraightLaserRenderEntity.MIN_BEAM_LENGTH) {
            return
        }
        val direction = end.subtract(start).normalize()
        val rotation = Quaternionf().rotationTo(
            0f,
            1f,
            0f,
            direction.x.toFloat(),
            direction.y.toFloat(),
            direction.z.toFloat()
        )
        val offset = Vector3f(
            (start.x - anchor.x).toFloat(),
            (start.y - anchor.y).toFloat(),
            (start.z - anchor.z).toFloat()
        )
        val stops = buildProxyAxialStops(axialSegments, coneEndRatio)
        for (segment in 0 until radialSegments) {
            val angle0 = (PI.toFloat() * 2f * segment) / radialSegments.toFloat()
            val angle1 = (PI.toFloat() * 2f * (segment + 1)) / radialSegments.toFloat()
            for (axial in 0 until stops.size - 1) {
                val y0 = stops[axial]
                val y1 = stops[axial + 1]
                val u0 = segment.toFloat() / radialSegments.toFloat()
                val u1 = (segment + 1).toFloat() / radialSegments.toFloat()
                val a = proxyVertex(angle0, y0, u0, radius, length, coneEndRatio, color, rotation, offset, textureRepeat)
                val b = proxyVertex(angle1, y0, u1, radius, length, coneEndRatio, color, rotation, offset, textureRepeat)
                val c = proxyVertex(angle1, y1, u1, radius, length, coneEndRatio, color, rotation, offset, textureRepeat)
                val d = proxyVertex(angle0, y1, u0, radius, length, coneEndRatio, color, rotation, offset, textureRepeat)
                builder.addRenderTypeQuad(pipe, a, b, c, d)
            }
        }
    }

    private fun proxyVertex(
        angle: Float,
        y: Float,
        u: Float,
        radius: Float,
        length: Float,
        coneEndRatio: Float,
        color: Vector4f,
        rotation: Quaternionf,
        offset: Vector3f,
        textureRepeat: Float
    ): RenderEntityModelVertex {
        val radiusScale = proxyConeRadiusScale(y, coneEndRatio)
        val normal = Vector3f(cos(angle), 0f, sin(angle)).normalize()
        val local = Vector3f(normal.x * radius * radiusScale, y * length, normal.z * radius * radiusScale)
        rotation.transform(local)
        rotation.transform(normal)
        local.add(offset)
        return RenderEntityModelVertex(
            position = local,
            color = color,
            uv = Vector2f(u, y * textureRepeat),
            uv1 = Vector2i(0, 10),
            uv2 = Vector2i(0xF000F0 and 0xFFFF, 0xF000F0 ushr 16),
            normal = normal
        )
    }

    companion object {
        private const val MIN_VISIBLE_ALPHA = 0.001f
        private const val CONE_LENGTH_FRACTION = 0.10f
        private const val MAX_CONE_LENGTH = 10.0f
        private const val BRIGHTNESS_MULTIPLIER = 0.82f
        private const val MASK_TEXTURE_REPEAT = 3.2f
        private const val IRIS_MASK_PIPE = "iris_straight_laser_entity_mask"
        private const val LAYER_OUTER_TEXTURE = 0
        private const val LAYER_INNER_GLOW = 1
        private const val LAYER_OUTER_BLOOM = 2

        private val LASER_IMPACT_NOISE_TEXTURE: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID,
                "textures/effect/straight_laser_impact_noise.png"
            )

        private lateinit var beamVertexBuffer: SimpleVertexBuffer
        private lateinit var beamShader: CooShaderProgram
        private var initialized = false
        private var beamGeometryConeRatio = -1f

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
                .coerceIn(0.001f, 1.0f)
            if (abs(beamGeometryConeRatio - coneRatio) <= 0.0001f) {
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
                val angle0 = (PI.toFloat() * 2f * segment) / segments.toFloat()
                val angle1 = (PI.toFloat() * 2f * (segment + 1)) / segments.toFloat()
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
                if (stops.none { abs(it - y) <= 0.0001f }) {
                    stops += y
                }
            }
            if (stops.none { abs(it - coneEndRatio) <= 0.0001f }) {
                stops += coneEndRatio
            }
            stops.sort()
            return stops
        }

        private fun buildProxyAxialStops(axialSegments: Int, coneEndRatio: Float): List<Float> {
            val stops = ArrayList<Float>(axialSegments + 2)
            for (index in 0..axialSegments) {
                val y = index.toFloat() / axialSegments.toFloat()
                if (stops.none { abs(it - y) <= 0.0001f }) {
                    stops += y
                }
            }
            if (stops.none { abs(it - coneEndRatio) <= 0.0001f }) {
                stops += coneEndRatio
            }
            stops.sort()
            return stops
        }

        private fun coneRatio(length: Float): Float {
            return (MAX_CONE_LENGTH / length.coerceAtLeast(DemoIrisStraightLaserRenderEntity.MIN_BEAM_LENGTH))
                .coerceAtMost(CONE_LENGTH_FRACTION)
                .coerceIn(0.001f, 1.0f)
        }

        private fun coneRadiusScale(y: Float, coneEndRatio: Float): Float {
            if (y >= coneEndRatio) {
                return 1.0f
            }
            return DemoIrisStraightLaserRenderEntity.smoothstep(0.0f, coneEndRatio, y)
        }

        private fun proxyConeRadiusScale(y: Float, coneEndRatio: Float): Float {
            if (y >= coneEndRatio) {
                return 1.0f
            }
            return DemoIrisStraightLaserRenderEntity.smoothstep(0.0f, coneEndRatio, y)
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
            val t = alpha.coerceIn(0f, 1f)
            return Vector3f(
                DemoIrisStraightLaserRenderEntity.mix(from.x, to.x, t),
                DemoIrisStraightLaserRenderEntity.mix(from.y, to.y, t),
                DemoIrisStraightLaserRenderEntity.mix(from.z, to.z, t)
            )
        }

        private fun alphaMultiplierFromBrightness(brightness: Float): Float {
            if (brightness <= 1f) {
                return brightness.coerceIn(0.05f, 1f)
            }
            return (1f + (brightness - 1f) * 0.42f).coerceAtMost(3.5f)
        }
    }
}
