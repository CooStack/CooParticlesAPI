package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.AutoRegisteredRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.client.RenderUtil
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomConfig
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomDistanceCompensation
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomMode
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelExecutors
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.FramePostRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityVisualProfile
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import cn.coostack.cooparticlesapi.renderer.utils.TrailModelBuilder
import cn.coostack.cooparticlesapi.renderer.utils.TrailPointTracker
import cn.coostack.cooparticlesapi.renderer.utils.TrailRibbonStyle
import org.joml.Matrix4fStack
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 拖尾演示 renderer。
 *
 * 每帧把盘旋光球的世界位置写入 [TrailPointTracker]，
 * 再用 [TrailModelBuilder] 重建拖尾条带 —— 这就是“点在不断变化的动态模型”。
 * glow mask 复用同一套模型，并演示 mask bloom 的三个可选增强项。
 */
@CooAutoRegisterRenderer
class DemoTrailOrbRenderEntityRenderer :
    RenderEntityModelRenderer<DemoTrailOrbRenderEntity>,
    FramePostRenderEntityRenderer<DemoTrailOrbRenderEntity>,
    AutoRegisteredRenderEntityRenderer<DemoTrailOrbRenderEntity> {

    private val tracker = TrailPointTracker(
        maxPoints = 96,
        maxAgeTicks = 16f,
        minPointDistance = 0.01
    )

    override fun initialize(instance: RenderEntityInstance<DemoTrailOrbRenderEntity>) {
    }

    override fun describeFeatures(entity: DemoTrailOrbRenderEntity): RenderEntityFeatureSet {
        return DemoWorldRenderModelSupport.describeFeatures()
    }

    override fun createVisualProfile(entity: DemoTrailOrbRenderEntity): RenderEntityVisualProfile {
        return DemoWorldRenderModelSupport.createVisualProfile()
    }

    override fun buildModel(entity: DemoTrailOrbRenderEntity, tickDelta: Float): RenderEntityModel {
        val orbitLocal = orbitOffset(entity, tickDelta)
        val origin = entity.lastRenderPos.lerp(entity.pos, tickDelta.toDouble())
        // 同一帧内 world pass 与 glow mask 会各构建一次模型，tracker 会自动忽略重复时间戳
        tracker.record(
            origin.add(orbitLocal.x.toDouble(), orbitLocal.y.toDouble(), orbitLocal.z.toDouble()),
            entity.age + tickDelta
        )
        // 拖尾头部是光球本身而不是实体中心，所以不插入局部原点
        val sample = tracker.sample(entity, tickDelta, includeHead = false)
        return DemoWorldRenderModelSupport.buildModel(entity) { model, basePipe ->
            TrailModelBuilder.buildRibbon(
                model,
                basePipe,
                sample,
                TrailRibbonStyle(
                    headWidth = 0.3f,
                    tailWidth = 0.02f,
                    headColor = DemoWorldRenderModelSupport.boosted(entity.effectColor, 1.35f, 1.0f),
                    tailColor = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0f),
                    widthEase = 1.2f,
                    fadeEase = 1.4f
                )
            )
            TrailModelBuilder.buildCameraFacingQuad(
                model,
                basePipe,
                orbitLocal,
                0.5f,
                DemoWorldRenderModelSupport.boosted(entity.effectColor, 1.5f, 0.95f),
                TrailModelBuilder.cameraLocalPos(sample)
            )
        }
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<DemoTrailOrbRenderEntity>,
        collector: RenderContributionCollector
    ) {
        val entity = input.instance.entity
        val model = buildModel(entity, input.frameContext.tickDelta)
        collector.submit(
            BuiltinRenderEffectDescriptors.sharedModelMaskBloom(
                effectId = "${entity.uuid}:trail_orb_mask_bloom",
                sourceInstanceId = entity.uuid.toString(),
                frameContext = input.frameContext,
                sourceEntity = entity,
                config = MaskBloomConfig(
                    blurSigma = 6.0f,
                    blurRange = 6.0f,
                    intensity = entity.intensity,
                    baseMaskIntensity = 0.0f,
                    threshold = 0.0f,
                    thresholdSoftness = 0.01f,
                    tint = Vector3f(entity.effectColor.x, entity.effectColor.y, entity.effectColor.z),
                    // 以下三项为新的可选泛光增强：强泛光模式 + 半档曝光补偿 + 距离聚光补偿
                    bloomMode = MaskBloomMode.STRONG,
                    exposureCompensation = 0.5f,
                    distanceCompensation = MaskBloomDistanceCompensation(
                        startDistance = 12f,
                        fullDistance = 48f,
                        maxBoost = 2.2f
                    )
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

    private fun orbitOffset(entity: DemoTrailOrbRenderEntity, tickDelta: Float): Vector3f {
        val angle = entity.getTime(tickDelta) * ORBIT_SPEED
        val radius = entity.radius
        return Vector3f(
            cos(angle) * radius,
            sin(angle * 2f) * radius * 0.25f + radius * 0.45f,
            sin(angle) * radius
        )
    }

    companion object {
        /** 每 2.4 秒绕行一圈。 */
        private const val ORBIT_SPEED = (PI * 2.0 / 2.4).toFloat()
    }
}
