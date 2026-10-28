package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import net.minecraft.resources.Identifier
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

@CooAutoRegister
class DemoIrisStraightLaserRenderEntity() : AutoRenderEntity(null, Vec3.ZERO) {
    constructor(world: Level?, start: Vec3, end: Vec3, durationTicks: Int = 90) : this() {
        this.world = world
        this.pos = start
        this.previousStart = start
        this.end = end
        this.previousEnd = end
        this.durationTicks = durationTicks
    }

    @CodecField
    var end: Vec3 = Vec3.ZERO

    @CodecField
    var previousStart: Vec3 = Vec3.ZERO

    @CodecField
    var previousEnd: Vec3 = Vec3.ZERO

    @CodecField
    var durationTicks: Int = 90

    @CodecField
    var phaseTicks: Int = 8

    @CodecField
    var color: Vector3f = Vector3f(0.28F, 0.82F, 1.0F)

    @CodecField
    var alpha: Float = 1.0F

    @CodecField
    var brightness: Float = 1.25F

    @CodecField
    var maxRadius: Float = 0.24F

    @CodecField
    var irisMaskAlpha: Float = 0.22F

    override fun getRenderID(): Identifier = ID

    override fun serverTick() {
        updateLifecycle()
    }

    override fun clientTick() {
        updateLifecycle()
    }

    override fun loadProfileFromEntity(another: cn.coostack.cooparticlesapi.renderer.RenderEntity) {
        super.loadProfileFromEntity(another)
        val typed = another as? DemoIrisStraightLaserRenderEntity ?: return
        end = typed.end
        previousStart = typed.previousStart
        previousEnd = typed.previousEnd
        durationTicks = typed.durationTicks
        phaseTicks = typed.phaseTicks
        color = Vector3f(typed.color)
        alpha = typed.alpha
        brightness = typed.brightness
        maxRadius = typed.maxRadius
        irisMaskAlpha = typed.irisMaskAlpha
    }

    fun renderStart(tickDelta: Float): Vec3 {
        return previousStart.lerp(pos, tickDelta.toDouble().coerceIn(0.0, 1.0))
    }

    fun renderEnd(tickDelta: Float): Vec3 {
        return previousEnd.lerp(end, tickDelta.toDouble().coerceIn(0.0, 1.0))
    }

    fun currentRadius(tickDelta: Float): Float {
        val time = currentTimeline(tickDelta)
        val growDuration = phaseTicks.coerceAtLeast(1).toFloat()
        val holdEnd = growDuration + durationTicks.coerceAtLeast(0).toFloat()
        return when {
            time < growDuration -> mix(MIN_RADIUS, maxRadius, easeOutCubic(smoothstep(0F, growDuration, time)))
            time < holdEnd -> maxRadius
            else -> mix(maxRadius, MIN_RADIUS, currentCollapse(tickDelta))
        }.coerceAtLeast(MIN_RADIUS)
    }

    fun currentAlpha(tickDelta: Float): Float {
        val time = currentTimeline(tickDelta)
        val growDuration = phaseTicks.coerceAtLeast(1).toFloat()
        val holdEnd = growDuration + durationTicks.coerceAtLeast(0).toFloat()
        val baseAlpha = alpha.coerceIn(0F, 1F)
        return when {
            time < growDuration -> mix(0.24F, 1.0F, easeOutCubic(smoothstep(0F, growDuration, time))) * baseAlpha
            time < holdEnd -> baseAlpha
            else -> {
                val fade = 1F - currentCollapse(tickDelta)
                fade * fade * baseAlpha
            }
        }
    }

    fun currentCollapse(tickDelta: Float): Float {
        val time = currentTimeline(tickDelta)
        val growDuration = phaseTicks.coerceAtLeast(1).toFloat()
        val holdEnd = growDuration + durationTicks.coerceAtLeast(0).toFloat()
        return if (time < holdEnd) 0F else smoothstep(0F, growDuration, time - holdEnd)
    }

    fun currentPhaseProgress(tickDelta: Float): Float {
        val time = currentTimeline(tickDelta)
        val growDuration = phaseTicks.coerceAtLeast(1).toFloat()
        val holdEnd = growDuration + durationTicks.coerceAtLeast(0).toFloat()
        return when {
            time < growDuration -> smoothstep(0F, growDuration, time)
            time < holdEnd -> 1F
            else -> 1F - currentCollapse(tickDelta)
        }
    }

    fun beamDirection(start: Vec3, end: Vec3): Vec3 {
        val delta = end.subtract(start)
        return if (delta.lengthSqr() <= MIN_DIRECTION_LENGTH_SQR) Vec3(0.0, 1.0, 0.0) else delta.normalize()
    }

    fun beamLength(start: Vec3, end: Vec3): Float {
        return start.distanceTo(end).toFloat().coerceAtLeast(MIN_BEAM_LENGTH)
    }

    private fun updateLifecycle() {
        if (age > totalDurationTicks() + 1) {
            remove()
            return
        }
        previousStart = pos
        previousEnd = end
    }

    private fun totalDurationTicks(): Int {
        val grow = phaseTicks.coerceAtLeast(1)
        return grow + durationTicks.coerceAtLeast(0) + grow
    }

    private fun currentTimeline(tickDelta: Float): Float {
        return (age - 1F + tickDelta).coerceAtLeast(0F)
    }

    companion object {
        const val MIN_RADIUS = 0.012F
        const val MIN_BEAM_LENGTH = 0.05F
        const val MIN_DIRECTION_LENGTH_SQR = 1.0E-6

        val ID: Identifier = Identifier.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "demo_iris_straight_laser_render_entity"
        )

        fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
            if (edge0 == edge1) return if (value >= edge1) 1F else 0F
            val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0F, 1F)
            return x * x * (3F - 2F * x)
        }

        fun easeOutCubic(value: Float): Float {
            val x = value.coerceIn(0F, 1F)
            val inverse = 1F - x
            return 1F - inverse * inverse * inverse
        }

        fun mix(from: Float, to: Float, alpha: Float): Float {
            return from + (to - from) * alpha.coerceIn(0F, 1F)
        }
    }
}
