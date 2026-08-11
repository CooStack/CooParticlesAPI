package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorVec3d
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.sin

/** 保存 MASK_BLOOM 直线激光测试所需的端点、生命周期和外观参数。 */
@CooAutoRegister
class DemoMaskBloomStraightLaserRenderEntity(
    world: Level? = null,
    pos: Vec3 = Vec3.ZERO
) : AutoRenderEntity(world, pos) {
    @CodecField
    var startInterpolator = InterpolatorVec3d(pos)

    @CodecField
    var endInterpolator = InterpolatorVec3d(pos)

    @CodecField
    var phaseTicks: Int = 6

    @CodecField
    var lifetime: Int = 12

    @CodecField
    var color: Vector3f = Vector3f(0.28F, 0.82F, 1.0F)

    @CodecField
    var alpha: Double = 1.0

    @CodecField
    var brightness: Float = 1.0F

    @CodecField
    var maxRadius: Float = DEFAULT_MAX_RADIUS

    override fun getRenderID(): ResourceLocation = ID

    override fun clientTick() {
        updateLifecycle()
    }

    override fun serverTick() {
        updateLifecycle()
    }

    /** 更新固定激光的两个端点，并保留旧端点供客户端帧插值。 */
    fun updateBeam(start: Vec3, end: Vec3): DemoMaskBloomStraightLaserRenderEntity {
        pos = start
        startInterpolator.uploadData(start)
        endInterpolator.uploadData(end)
        markDirty()
        return this
    }

    /** @return 当前渲染帧插值后的激光起点 */
    internal fun renderStart(tickDelta: Float): Vec3 {
        return startInterpolator.getWithInterpolator(tickDelta.toDouble().coerceIn(0.0, 1.0))
    }

    /** @return 当前渲染帧插值后的激光终点 */
    internal fun renderEnd(tickDelta: Float): Vec3 {
        return endInterpolator.getWithInterpolator(tickDelta.toDouble().coerceIn(0.0, 1.0))
    }

    /** @return 不小于最小有效长度的激光长度 */
    internal fun beamLength(start: Vec3, end: Vec3): Float {
        return start.distanceTo(end).toFloat().coerceAtLeast(MIN_BEAM_LENGTH)
    }

    /** @return 当前生命周期阶段的世界半径 */
    internal fun currentRadius(tickDelta: Float): Float {
        val time = currentTimeline(tickDelta)
        val growDuration = phaseTicks.coerceAtLeast(1).toFloat()
        val holdEnd = growDuration + lifetime.coerceAtLeast(0).toFloat()
        return when {
            time < growDuration -> {
                val grow = easeOutCubic(smoothstep(0F, growDuration, time))
                mix(MIN_RADIUS, maxRadius.coerceAtLeast(MIN_RADIUS), grow)
            }

            time < holdEnd -> maxRadius.coerceAtLeast(MIN_RADIUS)
            else -> mix(
                maxRadius.coerceAtLeast(MIN_RADIUS),
                MIN_RADIUS,
                currentCollapse(tickDelta)
            )
        }
    }

    /** @return 当前帧激光主体透明度 */
    internal fun currentBodyAlpha(tickDelta: Float): Float {
        val time = currentTimeline(tickDelta)
        val growDuration = phaseTicks.coerceAtLeast(1).toFloat()
        val holdEnd = growDuration + lifetime.coerceAtLeast(0).toFloat()
        val baseAlpha = alpha.toFloat().coerceIn(0F, 1F)
        return when {
            time < growDuration -> {
                val grow = easeOutCubic(smoothstep(0F, growDuration, time))
                mix(0.24F, 1F, grow) * baseAlpha
            }

            time < holdEnd -> baseAlpha
            else -> {
                val fade = 1F - currentCollapse(tickDelta)
                fade * fade * baseAlpha
            }
        }
    }

    /** @return 当前帧写入泛光遮罩的透明度 */
    internal fun currentBloomAlpha(tickDelta: Float): Float {
        val time = currentTimeline(tickDelta)
        val growDuration = phaseTicks.coerceAtLeast(1).toFloat()
        val holdEnd = growDuration + lifetime.coerceAtLeast(0).toFloat()
        val baseAlpha = alpha.toFloat().coerceIn(0F, 1F)
        return when {
            time < growDuration -> mix(0.34F, 1F, easeOutCubic(smoothstep(0F, growDuration, time))) * baseAlpha
            time < holdEnd -> mix(1F, 1.16F, currentPulse(tickDelta)) * baseAlpha
            else -> {
                val fade = 1F - currentCollapse(tickDelta)
                fade * fade * fade * baseAlpha
            }
        }
    }

    /** @return 扩张时递增、收束时递减的阶段进度 */
    internal fun currentPhaseProgress(tickDelta: Float): Float {
        val time = currentTimeline(tickDelta)
        val growDuration = phaseTicks.coerceAtLeast(1).toFloat()
        val holdEnd = growDuration + lifetime.coerceAtLeast(0).toFloat()
        return when {
            time < growDuration -> smoothstep(0F, growDuration, time)
            time < holdEnd -> 1F
            else -> 1F - currentCollapse(tickDelta)
        }
    }

    /** @return 收束前为零、收束期间位于零到一的进度 */
    internal fun currentCollapse(tickDelta: Float): Float {
        val time = currentTimeline(tickDelta)
        val growDuration = phaseTicks.coerceAtLeast(1).toFloat()
        val holdEnd = growDuration + lifetime.coerceAtLeast(0).toFloat()
        return if (time < holdEnd) {
            0F
        } else {
            smoothstep(0F, growDuration, time - holdEnd)
        }
    }

    private fun updateLifecycle() {
        startInterpolator.flushFrame()
        endInterpolator.flushFrame()
        if (age > totalDurationTicks() + 1) {
            canceled = true
        }
    }

    private fun totalDurationTicks(): Int {
        val grow = phaseTicks.coerceAtLeast(1)
        return grow + lifetime.coerceAtLeast(0) + grow
    }

    private fun currentTimeline(tickDelta: Float): Float {
        return (age - 1F + tickDelta).coerceAtLeast(0F)
    }

    private fun currentPulse(tickDelta: Float): Float {
        return 0.5F + 0.5F * sin(currentTimeline(tickDelta) * 0.84F + 0.6F)
    }

    companion object {
        private const val RENDER_ENTITY_ID = "demo_mask_bloom_straight_laser"

        internal const val DEFAULT_MAX_RADIUS = 0.22F
        internal const val MIN_RADIUS = 0.01F
        internal const val MIN_BEAM_LENGTH = 0.05F

        @JvmField
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            RENDER_ENTITY_ID
        )

        internal fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
            if (edge0 == edge1) {
                return if (value >= edge1) 1F else 0F
            }
            val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0F, 1F)
            return x * x * (3F - 2F * x)
        }

        internal fun mix(from: Float, to: Float, alpha: Float): Float {
            return from + (to - from) * alpha.coerceIn(0F, 1F)
        }

        private fun easeOutCubic(value: Float): Float {
            val inverse = 1F - value.coerceIn(0F, 1F)
            return 1F - inverse * inverse * inverse
        }
    }
}
