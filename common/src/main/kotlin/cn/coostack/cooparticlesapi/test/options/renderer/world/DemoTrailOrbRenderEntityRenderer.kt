package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput
import cn.coostack.cooparticlesapi.renderer.utils.TrailModelBuilder
import cn.coostack.cooparticlesapi.renderer.utils.TrailPointTracker
import cn.coostack.cooparticlesapi.renderer.utils.TrailRibbonStyle
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
class DemoTrailOrbRenderEntityRenderer : RenderEntityRenderer<DemoTrailOrbRenderEntity> {

    private val tracker = TrailPointTracker(
        maxPoints = 96,
        maxAgeTicks = 16F,
        minPointDistance = 0.01
    )

    override val pipeline = CooPipelines.MASK_BLOOM
        .blurSigma(6F)
        .blurRange(6F)
        .intensity { entity: DemoTrailOrbRenderEntity -> entity.intensity }

    override fun render(input: RenderInput<DemoTrailOrbRenderEntity>) {
        DemoWorldRenderModelSupport.renderModel(input, buildModel(input.entity, input.tickDelta))
    }

    private fun buildModel(entity: DemoTrailOrbRenderEntity, tickDelta: Float): RenderEntityModel {
        val orbitLocal = orbitOffset(entity, tickDelta)
        val origin = entity.lastRenderPos.lerp(entity.pos, tickDelta.toDouble())
        // 同一帧内 world pass 与 glow mask 会各构建一次模型，tracker 会自动忽略重复时间戳
        tracker.record(
            origin.add(orbitLocal.x.toDouble(), orbitLocal.y.toDouble(), orbitLocal.z.toDouble()),
            entity.age + tickDelta
        )
        // 拖尾头部是光球本身而不是实体中心，所以不插入局部原点
        val sample = tracker.sample(entity, tickDelta, includeHead = false)
        return DemoWorldRenderModelSupport.buildModel { model, baseLayer ->
            TrailModelBuilder.buildRibbon(
                model,
                baseLayer,
                sample,
                TrailRibbonStyle(
                    headWidth = 0.3F,
                    tailWidth = 0.02F,
                    headColor = DemoWorldRenderModelSupport.boosted(entity.effectColor, 1.35F, 1.0F),
                    tailColor = DemoWorldRenderModelSupport.alpha(entity.effectColor, 0F),
                    widthEase = 1.2F,
                    fadeEase = 1.4F
                )
            )
            TrailModelBuilder.buildCameraFacingQuad(
                model,
                baseLayer,
                orbitLocal,
                0.5F,
                DemoWorldRenderModelSupport.boosted(entity.effectColor, 1.5F, 0.95F),
                TrailModelBuilder.cameraLocalPos(sample)
            )
        }
    }

    private fun orbitOffset(entity: DemoTrailOrbRenderEntity, tickDelta: Float): Vector3f {
        // 每 2.4 秒绕行一圈。
        val orbitSpeed = (PI * 2.0 / 2.4).toFloat()
        val angle = entity.getTime(tickDelta) * orbitSpeed
        val radius = entity.radius
        return Vector3f(
            cos(angle) * radius,
            sin(angle * 2F) * radius * 0.25F + radius * 0.45F,
            sin(angle) * radius
        )
    }
}
