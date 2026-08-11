package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL33
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** 复现 UsefulMagic 直线激光材质和 MASK_BLOOM 双 attachment 绘制。 */
@CooAutoRegisterRenderer
class DemoMaskBloomStraightLaserRenderEntityRenderer :
    RenderEntityRenderer<DemoMaskBloomStraightLaserRenderEntity> {
    override val pipeline = CooPipelines.MASK_BLOOM
        .bloomSoftKnee(0.02F)
        .intensity { entity: DemoMaskBloomStraightLaserRenderEntity -> entity.brightness * 19.2F }

    override fun render(input: RenderInput<DemoMaskBloomStraightLaserRenderEntity>) {
        initStatic()
        val entity = input.entity
        val renderStart = entity.renderStart(input.tickDelta)
        val renderEnd = entity.renderEnd(input.tickDelta)
        val beamLength = entity.beamLength(renderStart, renderEnd)
        if (beamLength <= DemoMaskBloomStraightLaserRenderEntity.MIN_BEAM_LENGTH) {
            return
        }
        val bodyAlpha = entity.currentBodyAlpha(input.tickDelta)
        if (bodyAlpha <= MIN_VISIBLE_ALPHA) {
            return
        }
        val radius = entity.currentRadius(input.tickDelta)
        val projectedRadiusPx = projectedRadiusPx(
            radius = radius,
            start = renderStart,
            end = renderEnd,
            cameraWorldPos = currentCameraWorldPos(),
            screenSize = currentScreenSize()
        )
        val directWeight = DemoMaskBloomStraightLaserRenderEntity.smoothstep(5F, 18F, projectedRadiusPx)
        val directAlpha = bodyAlpha * DemoMaskBloomStraightLaserRenderEntity.mix(0.80F, 1F, directWeight)
        if (directAlpha <= MIN_VISIBLE_ALPHA) {
            return
        }
        val bloomIntensityGain = entity.brightness.coerceAtLeast(0F) *
            DemoMaskBloomStraightLaserRenderEntity.mix(
                0.82F,
                1.34F,
                bloomVisibleBrightness(entity, input.tickDelta)
            )
        val bloomAlpha = entity.currentBloomAlpha(input.tickDelta) *
            DemoMaskBloomStraightLaserRenderEntity.mix(0.28F, 0.62F, 1F - directWeight) *
            max(0.62F, farBloomIntensityScale(projectedRadiusPx, 18F, 2F)) * bloomIntensityGain
        renderPasses(
            entity = entity,
            modelMatrix = orientedModelMatrix(input.modelMatrix, renderStart, renderEnd, renderStart),
            viewMatrix = input.viewMatrix,
            projMatrix = input.projMatrix,
            beamLength = beamLength,
            radius = radius,
            passAlpha = directAlpha,
            bloomAlpha = bloomAlpha,
            phaseProgress = entity.currentPhaseProgress(input.tickDelta),
            collapse = entity.currentCollapse(input.tickDelta),
            time = entity.getTime(input.tickDelta)
        )
    }

    private fun bloomVisibleBrightness(
        entity: DemoMaskBloomStraightLaserRenderEntity,
        tickDelta: Float
    ): Float {
        val sourceLuminance = max(entity.color.x, max(entity.color.y, entity.color.z)).coerceIn(0.08F, 1F)
        return (sourceLuminance * entity.currentBloomAlpha(tickDelta) * entity.brightness.coerceAtLeast(0F))
            .coerceIn(0F, 1F)
    }

    private fun renderPasses(
        entity: DemoMaskBloomStraightLaserRenderEntity,
        modelMatrix: Matrix4f,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        beamLength: Float,
        radius: Float,
        passAlpha: Float,
        bloomAlpha: Float,
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
            beamShader.useOnContext {
                beamTextures.drawWith {
                    RenderSystem.blendFunc(GL33.GL_SRC_ALPHA, GL33.GL_ONE_MINUS_SRC_ALPHA)
                    drawPass(
                        entity,
                        modelMatrix,
                        viewMatrix,
                        projMatrix,
                        beamLength,
                        radius,
                        entity.color,
                        (passAlpha * 0.34F).coerceAtMost(0.48F),
                        0.64F,
                        0F,
                        0F,
                        phaseProgress,
                        collapse,
                        time,
                        0
                    )
                    drawPass(
                        entity,
                        modelMatrix,
                        viewMatrix,
                        projMatrix,
                        beamLength,
                        radius * 1.18F,
                        mixColor(entity.color, Vector3f(1F, 0.97F, 0.90F), 0.28F),
                        (passAlpha * 0.10F).coerceAtMost(0.18F),
                        0.74F,
                        0F,
                        0F,
                        phaseProgress,
                        collapse,
                        time,
                        0
                    )
                    RenderSystem.blendFunc(GL33.GL_SRC_ALPHA, GL33.GL_ONE)
                    drawPass(
                        entity,
                        modelMatrix,
                        viewMatrix,
                        projMatrix,
                        beamLength,
                        radius * 0.30F,
                        mixColor(entity.color, Vector3f(1F, 0.98F, 0.92F), 0.62F),
                        (passAlpha * 0.12F).coerceAtMost(0.22F),
                        1.16F,
                        0F,
                        0F,
                        phaseProgress,
                        collapse,
                        time,
                        1
                    )
                    drawPass(
                        entity,
                        modelMatrix,
                        viewMatrix,
                        projMatrix,
                        beamLength,
                        radius,
                        entity.color,
                        0F,
                        0F,
                        (bloomAlpha * 0.72F).coerceAtMost(0.96F),
                        2.25F,
                        phaseProgress,
                        collapse,
                        time,
                        0
                    )
                    drawPass(
                        entity,
                        modelMatrix,
                        viewMatrix,
                        projMatrix,
                        beamLength,
                        radius * 1.18F,
                        mixColor(entity.color, Vector3f(1F, 0.97F, 0.90F), 0.24F),
                        0F,
                        0F,
                        (bloomAlpha * 0.38F).coerceAtMost(0.62F),
                        1.55F,
                        phaseProgress,
                        collapse,
                        time,
                        0
                    )
                    drawPass(
                        entity,
                        modelMatrix,
                        viewMatrix,
                        projMatrix,
                        beamLength,
                        radius * 0.30F,
                        mixColor(entity.color, Vector3f(1F, 0.99F, 0.94F), 0.58F),
                        0F,
                        0F,
                        (bloomAlpha * 0.20F).coerceAtMost(0.32F),
                        1.92F,
                        phaseProgress,
                        collapse,
                        time,
                        1
                    )
                }
            }
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
            RenderSystem.enableCull()
        }
    }

    private fun drawPass(
        entity: DemoMaskBloomStraightLaserRenderEntity,
        modelMatrix: Matrix4f,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        beamLength: Float,
        beamRadius: Float,
        passColor: Vector3f,
        passAlpha: Float,
        brightness: Float,
        maskAlpha: Float,
        maskBrightness: Float,
        phaseProgress: Float,
        collapse: Float,
        time: Float,
        layerMode: Int
    ) {
        if (passAlpha <= MIN_VISIBLE_ALPHA && maskAlpha <= MIN_VISIBLE_ALPHA) {
            return
        }
        beamShader.apply {
            setInt("impactNoise", 0)
            setMatrix4("modelMatrix", modelMatrix)
            setMatrix4("viewMatrix", viewMatrix)
            setMatrix4("projMatrix", projMatrix)
            setFloat("beamRadius", beamRadius.coerceAtLeast(DemoMaskBloomStraightLaserRenderEntity.MIN_RADIUS))
            setFloat("beamLength", beamLength.coerceAtLeast(DemoMaskBloomStraightLaserRenderEntity.MIN_BEAM_LENGTH))
            setFloat("coneEndRatio", coneRatio(beamLength))
            setFloat3("color", passColor)
            setFloat("alpha", passAlpha)
            setFloat("brightness", brightness * entity.brightness * 0.82F)
            setFloat("maskAlpha", maskAlpha)
            setFloat("maskBrightness", maskBrightness * entity.brightness * 0.82F)
            setFloat("phaseProgress", phaseProgress)
            setFloat("collapse", collapse)
            setFloat("time", time)
            setInt("layerMode", layerMode)
            beamVertexBuffer.draw()
        }
    }

    private fun projectedRadiusPx(
        radius: Float,
        start: Vec3,
        end: Vec3,
        cameraWorldPos: Vector3f,
        screenSize: Vector2f
    ): Float {
        val midpoint = (start + end) * 0.5
        val midpointPosition = Vector3f(midpoint.x.toFloat(), midpoint.y.toFloat(), midpoint.z.toFloat())
        val distance = (midpointPosition - cameraWorldPos).length().coerceAtLeast(0.125F)
        return max(radius * 8F, 3.6F) / distance * screenSize.y.coerceAtLeast(1F) * 0.75F
    }

    private fun farBloomIntensityScale(
        projectedRadiusPx: Float,
        compensationFadeStartPx: Float,
        maxCompensationPx: Float
    ): Float {
        val farWeight = 1F - DemoMaskBloomStraightLaserRenderEntity.smoothstep(
            4F,
            compensationFadeStartPx,
            projectedRadiusPx
        )
        return 1F + farWeight * maxCompensationPx * 0.18F
    }

    private fun orientedModelMatrix(
        baseMatrix: Matrix4f,
        start: Vec3,
        end: Vec3,
        anchor: Vec3
    ): Matrix4f {
        val delta = end - start
        val direction = if (delta.lengthSqr() <= MIN_DIRECTION_LENGTH_SQUARED) {
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

    private fun currentCameraWorldPos(): Vector3f {
        val cameraPos = Minecraft.getInstance().gameRenderer.mainCamera.position
        return Vector3f(cameraPos.x.toFloat(), cameraPos.y.toFloat(), cameraPos.z.toFloat())
    }

    private fun currentScreenSize(): Vector2f {
        val renderTarget = Minecraft.getInstance().mainRenderTarget
        val width = ClientRenderPipelineManager.currentRenderWidth().takeIf { it > 0 }
            ?: renderTarget.width.takeIf { it > 0 }
            ?: 0
        val height = ClientRenderPipelineManager.currentRenderHeight().takeIf { it > 0 }
            ?: renderTarget.height.takeIf { it > 0 }
            ?: 0
        return Vector2f(width.toFloat(), height.toFloat())
    }

    private fun mixColor(from: Vector3f, to: Vector3f, alpha: Float): Vector3f {
        val value = alpha.coerceIn(0F, 1F)
        return Vector3f(
            DemoMaskBloomStraightLaserRenderEntity.mix(from.x, to.x, value),
            DemoMaskBloomStraightLaserRenderEntity.mix(from.y, to.y, value),
            DemoMaskBloomStraightLaserRenderEntity.mix(from.z, to.z, value)
        )
    }

    companion object {
        private const val MIN_VISIBLE_ALPHA = 0.001F
        private const val MIN_DIRECTION_LENGTH_SQUARED = 0.000001

        private lateinit var beamVertexBuffer: SimpleVertexBuffer
        private lateinit var beamShader: CooShaderProgram
        private lateinit var beamTextures: SimpleTextures
        private var initialized = false

        private fun initStatic() {
            if (!initialized) {
                beamVertexBuffer = SimpleVertexBuffer().apply {
                    init()
                    setVertexes(buildCylinderVertices(), CooVertexFormat.POINT_FORMAT)
                }
                beamShader = ShaderProgramBuilder()
                    .vertex(
                        IdentifierShader(
                            ResourceLocation.fromNamespaceAndPath(
                                CooParticlesConstants.MOD_ID,
                                "core/vertex/mask_bloom_straight_laser.vsh"
                            ),
                            GlShaderType.VERTEX
                        )
                    )
                    .fragment(
                        IdentifierShader(
                            ResourceLocation.fromNamespaceAndPath(
                                CooParticlesConstants.MOD_ID,
                                "core/fragment/mask_bloom_straight_laser.fsh"
                            ),
                            GlShaderType.FRAGMENT
                        )
                    )
                    .build()
                beamTextures = SimpleTextures().apply {
                    addTexture(
                        IdentifierTexture(
                            ResourceLocation.fromNamespaceAndPath(
                                CooParticlesConstants.MOD_ID,
                                "effect/straight_laser_impact_noise.png"
                            )
                        )
                    )
                }
                initialized = true
            }
            if (beamShader.program == 0) {
                beamShader.init()
                beamTextures.init()
            }
        }

        private fun coneRatio(beamLength: Float): Float {
            return (10F / beamLength.coerceAtLeast(DemoMaskBloomStraightLaserRenderEntity.MIN_BEAM_LENGTH))
                .coerceAtMost(0.10F)
                .coerceIn(0.001F, 1F)
        }

        private fun buildCylinderVertices(): List<VertexData> {
            val segments = 24
            val yStops = floatArrayOf(0F, 0.001F, 0.0025F, 0.005F, 0.01F, 0.025F, 0.05F, 0.075F, 0.10F, 1F)
            val vertices = ArrayList<VertexData>(segments * (yStops.size - 1) * 6)
            for (segment in 0 until segments) {
                val angle0 = (PI.toFloat() * 2F * segment) / segments.toFloat()
                val angle1 = (PI.toFloat() * 2F * (segment + 1)) / segments.toFloat()
                val x0 = cos(angle0)
                val z0 = sin(angle0)
                val x1 = cos(angle1)
                val z1 = sin(angle1)
                for (axialSegment in 0 until yStops.lastIndex) {
                    val y0 = yStops[axialSegment]
                    val y1 = yStops[axialSegment + 1]
                    appendQuad(
                        vertices,
                        Vector3f(x0, y0, z0),
                        Vector3f(x1, y0, z1),
                        Vector3f(x1, y1, z1),
                        Vector3f(x0, y1, z0)
                    )
                }
            }
            return vertices
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
    }
}
