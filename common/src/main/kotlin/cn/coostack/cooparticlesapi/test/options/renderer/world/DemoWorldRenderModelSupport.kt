package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.client.RenderUtil
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectTypes
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomConfig
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelExecutors
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPipe
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelVertex
import cn.coostack.cooparticlesapi.renderer.runtime.CompositeMode
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityVisualProfile
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import net.minecraft.resources.ResourceLocation
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.joml.Matrix4fStack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object DemoWorldRenderModelSupport {
    fun describeFeatures(): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.WORLD_PASS, RenderFrameStage.FRAME_POST),
            requestedSceneTargets = setOf(
                RenderSceneTargets.POST,
                RenderSceneTargets.SCENE_COLOR,
                RenderSceneTargets.SCENE_DEPTH
            ),
            effectTypes = setOf(BuiltinRenderEffectTypes.MASK_BLOOM),
            localRendererEnabled = true,
            effectGraphEnabled = true
        )
    }

    fun createVisualProfile(): RenderEntityVisualProfile {
        return RenderEntityVisualProfile(
            compositeMode = CompositeMode.ADDITIVE,
            needsSceneColorCopy = true,
            needsSceneDepth = true,
            renderPriority = 180
        )
    }

    fun buildModel(
        entity: DemoWorldRenderEffectSpec,
        geometry: (
            RenderEntityModelBuilder,
            RenderEntityModelPipe
        ) -> Unit
    ): RenderEntityModel {
        val model = RenderEntityModelBuilder()
        val basePipe = model.pipe("world_model") {
            shader(shader("render_entity/world_model"))
            param("radius", entity.radius)
            param("intensity", entity.intensity)
        }
        geometry(model, basePipe)
        return model.build()
    }

    fun <T> collectModelMaskBloom(
        input: RenderContributionInput<T>,
        collector: RenderContributionCollector,
        model: RenderEntityModel
    ) where T : RenderEntity, T : DemoWorldRenderEffectSpec {
        val entity = input.instance.entity
        collector.submit(
            BuiltinRenderEffectDescriptors.sharedModelMaskBloom(
                effectId = "${entity.uuid}:model_mask_bloom",
                sourceInstanceId = entity.uuid.toString(),
                frameContext = input.frameContext,
                sourceEntity = entity,
                config = MaskBloomConfig(
                    blurSigma = 5.5f,
                    blurRange = 5.5f,
                    intensity = entity.intensity,
                    baseMaskIntensity = 0.0f,
                    threshold = 0.0f,
                    thresholdSoftness = 0.01f,
                    tint = Vector3f(entity.effectColor.x, entity.effectColor.y, entity.effectColor.z)
                ),
                priority = 220,
                requiredCapabilities = setOf(
                    RenderBackendCapability.FINAL_FRAME_POST,
                    RenderBackendCapability.SAFE_WORLD_COMPOSITE
                )
            ) {
                val stack = Matrix4fStack(16)
                stack.set(RenderUtil.buildModelMatrix(entity, input.frameContext.tickDelta))
                RenderEntityModelExecutors.active().draw(
                    model,
                    LocalRenderInput(
                        instance = input.instance,
                        tickDelta = input.frameContext.tickDelta,
                        viewMatrix = input.frameContext.viewMatrix,
                        projMatrix = input.frameContext.projMatrix,
                        modelMatrix = stack,
                        renderState = RenderStateGuard.MutableRenderState()
                    )
                )
            }
        )
    }

    fun circle(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
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
                pipe,
                Vector3f(cos(a) * radius, y, sin(a) * radius),
                Vector3f(cos(b) * radius, y, sin(b) * radius),
                color
            )
        }
    }

    fun disc(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        radius: Float,
        color: Vector4f,
        y: Float = 0f
    ) {
        val center = vertex(Vector3f(0f, y, 0f), color, Vector2f(0.5f, 0.5f))
        val step = (2.0 * PI / DEFAULT_SEGMENTS).toFloat()
        for (index in 0 until DEFAULT_SEGMENTS) {
            val a = index * step
            val b = (index + 1) * step
            model.addTriangle(
                pipe,
                center,
                vertex(Vector3f(cos(a) * radius, y, sin(a) * radius), color),
                vertex(Vector3f(cos(b) * radius, y, sin(b) * radius), color)
            )
        }
    }

    fun annulus(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        innerRadius: Float,
        outerRadius: Float,
        color: Vector4f,
        y: Float = 0f
    ) {
        val step = (2.0 * PI / DEFAULT_SEGMENTS).toFloat()
        for (index in 0 until DEFAULT_SEGMENTS) {
            val a = index * step
            val b = (index + 1) * step
            model.addQuad(
                pipe,
                vertex(Vector3f(cos(a) * innerRadius, y, sin(a) * innerRadius), color),
                vertex(Vector3f(cos(a) * outerRadius, y, sin(a) * outerRadius), color),
                vertex(Vector3f(cos(b) * outerRadius, y, sin(b) * outerRadius), color),
                vertex(Vector3f(cos(b) * innerRadius, y, sin(b) * innerRadius), color)
            )
        }
    }

    fun sphereShell(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        radius: Float,
        color: Vector4f,
        latSegments: Int = 8,
        lonSegments: Int = 36,
        yScale: Float = 1f
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
                    pipe,
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
        pipe: RenderEntityModelPipe,
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
                pipe,
                vertex(Vector3f(-dx, -halfHeight, -dz), color, Vector2f(0f, 0f)),
                vertex(Vector3f(dx, -halfHeight, dz), color, Vector2f(1f, 0f)),
                vertex(Vector3f(dx, halfHeight, dz), color, Vector2f(1f, 1f)),
                vertex(Vector3f(-dx, halfHeight, -dz), color, Vector2f(0f, 1f))
            )
        }
    }

    fun spiral(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        innerRadius: Float,
        outerRadius: Float,
        turns: Float,
        color: Vector4f,
        y: Float = 0f
    ) {
        val segments = 96
        for (index in 0 until segments) {
            val t0 = index.toFloat() / segments.toFloat()
            val t1 = (index + 1).toFloat() / segments.toFloat()
            val r0 = innerRadius + (outerRadius - innerRadius) * t0
            val r1 = innerRadius + (outerRadius - innerRadius) * t1
            val a0 = turns * 2.0f * PI.toFloat() * t0
            val a1 = turns * 2.0f * PI.toFloat() * t1
            line(
                model,
                pipe,
                Vector3f(cos(a0) * r0, y + sin(t0 * PI.toFloat()) * outerRadius * 0.08f, sin(a0) * r0),
                Vector3f(cos(a1) * r1, y + sin(t1 * PI.toFloat()) * outerRadius * 0.08f, sin(a1) * r1),
                color
            )
        }
    }

    fun sphereGuideRings(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        radius: Float,
        color: Vector4f
    ) {
        circle(model, pipe, radius, color, y = 0f)
        verticalCircleX(model, pipe, radius, color)
        verticalCircleZ(model, pipe, radius, color)
    }

    fun line(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        from: Vector3f,
        to: Vector3f,
        color: Vector4f
    ) {
        model.addVertex(pipe, from, color)
        model.addVertex(pipe, to, color)
    }

    fun alpha(color: Vector4f, scale: Float): Vector4f {
        return Vector4f(color.x, color.y, color.z, color.w * scale)
    }

    fun boosted(color: Vector4f, rgbScale: Float, alphaScale: Float = 1f): Vector4f {
        return Vector4f(color.x * rgbScale, color.y * rgbScale, color.z * rgbScale, color.w * alphaScale)
    }

    private fun verticalCircleX(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        radius: Float,
        color: Vector4f
    ) {
        val step = (2.0 * PI / DEFAULT_SEGMENTS).toFloat()
        for (index in 0 until DEFAULT_SEGMENTS) {
            val a = index * step
            val b = (index + 1) * step
            line(
                model,
                pipe,
                Vector3f(0f, cos(a) * radius, sin(a) * radius),
                Vector3f(0f, cos(b) * radius, sin(b) * radius),
                color
            )
        }
    }

    private fun verticalCircleZ(
        model: RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        radius: Float,
        color: Vector4f
    ) {
        val step = (2.0 * PI / DEFAULT_SEGMENTS).toFloat()
        for (index in 0 until DEFAULT_SEGMENTS) {
            val a = index * step
            val b = (index + 1) * step
            line(
                model,
                pipe,
                Vector3f(cos(a) * radius, sin(a) * radius, 0f),
                Vector3f(cos(b) * radius, sin(b) * radius, 0f),
                color
            )
        }
    }

    private fun vertex(
        position: Vector3f,
        color: Vector4f,
        uv: Vector2f = Vector2f(0f, 0f)
    ): RenderEntityModelVertex {
        return RenderEntityModelVertex(
            position = position,
            color = color,
            uv = uv
        )
    }

    private fun shader(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }

    private const val DEFAULT_SEGMENTS = 48
}
