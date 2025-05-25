package cn.coostack.cooparticlesapi.utils.builder

import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.SequencedParticleStyle
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.particles.control.group.SequencedParticleGroup
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.MathPresets
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.SortedMap

class PointsBuilder {
    companion object {
        @JvmStatic
        fun of(axis: RelativeLocation): PointsBuilder {
            return PointsBuilder().also { it.axis = axis }
        }

        /**
         * 默认对称轴为Y轴
         */
        @JvmStatic
        fun of(points: Collection<RelativeLocation>): PointsBuilder {
            return PointsBuilder().also { it.addPoints(points) }
        }

        @JvmStatic
        fun of(axis: RelativeLocation, points: Collection<RelativeLocation>): PointsBuilder {
            return PointsBuilder().also { it.axis = axis; it.addPoints(points) }
        }

    }

    var axis = RelativeLocation.yAxis()
        private set
    private val points = ArrayList<RelativeLocation>()

    fun axis(axis: RelativeLocation): PointsBuilder {
        this.axis = axis
        return this
    }


    /**
     * 循环对每一个已经加入到builder的点进行同一个操作
     */
    fun pointsOnEach(handler: (RelativeLocation) -> Unit): PointsBuilder {
        points.onEach { handler.invoke(it) }
        return this
    }

    fun withPreset(handler: MathPresets.() -> Collection<RelativeLocation>): PointsBuilder = addPoints(
        handler(MathPresets)
    )

    fun addPoints(enter: Collection<RelativeLocation>): PointsBuilder {
        points.addAll(enter)
        return this
    }

    fun addWith(handler: Math3DUtil.() -> Collection<RelativeLocation>): PointsBuilder = addPoints(
        handler(Math3DUtil)
    )

    fun addPoint(point: RelativeLocation): PointsBuilder {
        points.add(point)
        return this
    }

    fun addBezierCurve(
        target: RelativeLocation,
        startHandle: RelativeLocation,
        endHandle: RelativeLocation,
        count: Int
    ): PointsBuilder = addWith {
        generateBezierCurve(target, startHandle, endHandle, count)
    }

    fun withBuilder(builder: PointsBuilder): PointsBuilder {
        addPoints(builder.create())
        return this
    }

    fun withBuilder(handler: (PointsBuilder) -> Unit): PointsBuilder {
        val builder = PointsBuilder()
        handler(builder)
        addPoints(builder.create())
        return this
    }

    fun withBuilderAxis(axis: RelativeLocation, handler: (PointsBuilder) -> Unit): PointsBuilder {
        val builder = PointsBuilder.of(axis)
        handler(builder)
        addPoints(builder.create())
        return this
    }

    fun addDiscreteCircleXZ(r: Double, count: Int, discrete: Double): PointsBuilder = addWith {
        getDiscreteCircleXZ(r, count, discrete)
    }

    fun addCircle(r: Double, count: Int): PointsBuilder = addPoints(
        Math3DUtil.getCircleXZ(r, count)
    )

    fun addHalfCircle(r: Double, count: Int): PointsBuilder = addWith {
        getHalfCircleXZ(r, count)
    }

    fun addHalfCircle(r: Double, count: Int, rotate: Double): PointsBuilder = addWith {
        getHalfCircleXZ(r, count, rotate)
    }

    fun addBall(r: Double, countPow: Int): PointsBuilder = addPoints(
        Math3DUtil.getBallLocations(r, countPow)
    )

    fun addCycloidGraphic(
        r1: Double,
        r2: Double,
        w1: Int,
        w2: Int,
        count: Int,
        scale: Double
    ): PointsBuilder = addPoints(
        Math3DUtil.getCycloidGraphic(
            r1, r2, w1, w2, count, scale
        )
    )

    fun addBuilder(origin: RelativeLocation, builder: PointsBuilder): PointsBuilder {
        points.addAll(builder.create().onEach { it.add(origin) })
        return this
    }

    fun addPolygonInCircle(n: Int, edgeCount: Int, r: Double): PointsBuilder = addPoints(
        Math3DUtil.getPolygonInCircleLocations(n, edgeCount, r)
    )

    fun addPolygonInCircleVertices(n: Int, r: Double): PointsBuilder = addPoints(
        Math3DUtil.getPolygonInCircleVertices(n, r)
    )


    fun addRoundShape(r: Double, step: Double, preCircleCount: Int): PointsBuilder = addPoints(
        Math3DUtil.getRoundScapeLocations(r, step, preCircleCount)
    )


    fun addRoundShape(r: Double, step: Double, minCircleCount: Int, maxCircleCount: Int): PointsBuilder =
        addWith {
            getRoundScapeLocations(r, step, minCircleCount, maxCircleCount)
        }


    fun addLine(
        start: RelativeLocation, end: RelativeLocation, count: Int
    ): PointsBuilder = addPoints(
        Math3DUtil.getLineLocations(start, end, count)
    )


    fun addLine(
        start: Vec3d, end: Vec3d, count: Int
    ): PointsBuilder = addPoints(
        Math3DUtil.getLineLocations(start, end, count)
    )


    fun addLine(
        direction: RelativeLocation, step: Double, count: Int
    ): PointsBuilder = addPoints(
        Math3DUtil.getLineLocations(Vec3d.ZERO, direction.toVector(), step, count)
    )


    fun addLine(
        direction: Vec3d, step: Double, count: Int
    ): PointsBuilder = addPoints(
        Math3DUtil.getLineLocations(Vec3d.ZERO, direction, step, count)
    )


    fun addLine(
        origin: RelativeLocation, direction: RelativeLocation, step: Double, count: Int
    ): PointsBuilder = addPoints(
        Math3DUtil.getLineLocations(origin.toVector(), direction.toVector(), step, count)
    )


    fun addLightningNodes(end: RelativeLocation, count: Int): PointsBuilder = addWith {
        getLightningEffectNodes(RelativeLocation(), end, count)
    }

    fun addLightningNodes(start: RelativeLocation, end: RelativeLocation, count: Int): PointsBuilder = addWith {
        getLightningEffectNodes(start, end, count)
    }

    fun addLightningNodes(end: RelativeLocation, count: Int, offsetRange: Double): PointsBuilder = addWith {
        getLightningEffectNodes(RelativeLocation(), end, count, offsetRange)
    }

    fun addLightningNodes(
        start: RelativeLocation,
        end: RelativeLocation,
        count: Int,
        offsetRange: Double
    ): PointsBuilder = addWith {
        getLightningEffectNodes(start, end, count, offsetRange)
    }

    fun addLightningPoints(end: RelativeLocation, count: Int, preLineCount: Int, offsetRange: Double): PointsBuilder =
        addWith {
            getLightningEffectPoints(end, count, preLineCount, offsetRange)
        }

    fun addLightningPoints(
        start: RelativeLocation,
        end: RelativeLocation,
        count: Int,
        preLineCount: Int,
        offsetRange: Double
    ): PointsBuilder = addWith {
        getLightningEffectPoints(end, count, preLineCount, offsetRange).onEach { it.add(start) }
    }

    fun addLightningPoints(end: RelativeLocation, count: Int, preLineCount: Int): PointsBuilder = addWith {
        getLightningEffectPoints(end, count, preLineCount)
    }

    fun addLightningPoints(
        start: RelativeLocation,
        end: RelativeLocation,
        count: Int,
        preLineCount: Int
    ): PointsBuilder = addWith {
        getLightningEffectPoints(end, count, preLineCount).onEach { it.add(start) }
    }

    fun addLine(
        origin: Vec3d, direction: Vec3d, step: Double, count: Int
    ): PointsBuilder = addPoints(
        Math3DUtil.getLineLocations(origin, direction, step, count)
    )


    fun rotateAsAxis(radius: Double): PointsBuilder {
        Math3DUtil.rotateAsAxis(points, axis, radius)
        return this
    }

    fun rotateAsAxis(radius: Double, axis: RelativeLocation): PointsBuilder {
        Math3DUtil.rotateAsAxis(points, axis, radius)
        return this
    }

    fun rotateTo(to: RelativeLocation): PointsBuilder {
        Math3DUtil.rotatePointsToPoint(points, to, axis)
        return this
    }

    fun rotateTo(to: Vec3d): PointsBuilder {
        Math3DUtil.rotatePointsToPoint(points, RelativeLocation.Companion.of(to), axis)
        return this
    }

    fun rotateTo(origin: RelativeLocation, end: RelativeLocation): PointsBuilder {
        Math3DUtil.rotatePointsToPoint(points, origin.toVector(), end.toVector(), axis)
        return this
    }

    fun rotateTo(origin: Vec3d, end: Vec3d): PointsBuilder {
        Math3DUtil.rotatePointsToPoint(points, origin, end, axis)
        return this
    }

    fun clear(): PointsBuilder {
        points.clear()
        return this
    }

    fun create(): List<RelativeLocation> = points.asSequence().map { it.clone() }.toList()

    fun createWithParticleEffects(
        dataBuilder: (relative: RelativeLocation) -> ControlableParticleGroup.ParticleRelativeData
    ): Map<ControlableParticleGroup.ParticleRelativeData, RelativeLocation> {
        return mapOf(
            *create().map {
                dataBuilder(it) to it
            }.toTypedArray()
        )
    }

    fun createWithSequencedStyleData(
        dataBuilder: (relative: RelativeLocation, order: Int) -> SequencedParticleStyle.SortedStyleData
    ): SortedMap<SequencedParticleStyle.SortedStyleData, RelativeLocation> {
        var order = 0
        return sortedMapOf(
            *create().map {
                dataBuilder(it, order++) to it
            }.toTypedArray()
        )
    }

    fun createWithSequencedParticleEffects(
        dataBuilder: (relative: RelativeLocation) -> SequencedParticleGroup.SequencedParticleRelativeData
    ): Map<SequencedParticleGroup.SequencedParticleRelativeData, RelativeLocation> {
        return mapOf(
            *create().map {
                dataBuilder(it) to it
            }.toTypedArray()
        )
    }

    fun createWithStyleData(
        dataBuilder: (relative: RelativeLocation) -> ParticleGroupStyle.StyleData
    ): Map<ParticleGroupStyle.StyleData, RelativeLocation> {
        return mapOf(
            *create().map {
                dataBuilder(it) to it
            }.toTypedArray()
        )
    }

    fun createAsBlockPos(): Set<BlockPos> = points.asSequence().map {
        BlockPos.ofFloored(it.toVector())
    }.toMutableSet()

    fun cloneBuilder(): PointsBuilder {
        return of(axis, create())
    }
}