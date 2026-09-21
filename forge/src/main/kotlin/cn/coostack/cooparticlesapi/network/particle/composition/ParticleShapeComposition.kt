package cn.coostack.cooparticlesapi.network.particle.composition

import net.minecraft.network.PacketByteBuf
import net.minecraft.world.phys.Vec3
import java.util.UUID

class ParticleShapeComposition(uuid: UUID) : ParticleComposition(Vec3.ZERO, null) {
    init {
        this.controlUUID = uuid
        visibleRange = Double.MAX_VALUE
    }

    private val points = ArrayList<Pair<PointsBuilder, (RelativeLocation) -> CompositionData>>()
    private val invokes = ArrayList<ParticleShapeComposition.() -> Unit>()
    private val beforeInvokes =
        ArrayList<ParticleShapeComposition.(map: Map<CompositionData, RelativeLocation>) -> Unit>()

    var scaleHelper: ScaleHelper? = null
        private set
    var spawnAge = 0
    var scalePreTick = false
        private set
    var scaleReversed = false
    var reversedClean = true

    fun loadScaleHelper(min: Double, max: Double, scalingTick: Int): ParticleShapeComposition {
        scaleHelper = CompositionScaleHelper(min, max, scalingTick)
            .apply {
                loadControler(this@ParticleShapeComposition)
            }
        scalePreTick = true
        return this
    }

    fun loadScaleHelperBezierValue(
        minScale: Double,
        maxScale: Double,
        scaleTick: Int,
        c1: RelativeLocation,
        c2: RelativeLocation
    ): ParticleShapeComposition {
        scaleHelper = CompositionBezierScaleHelper(scaleTick, minScale, maxScale, c1, c2)
        scalePreTick = true
        scaleHelper!!.loadControler(this)
        return this
    }

    fun applyDisplayAction(action: ParticleShapeComposition.() -> Unit): ParticleShapeComposition {
        invokes.add(action)
        return this
    }

    fun applyBeforeDisplayAction(action: ParticleShapeComposition.(Map<CompositionData, RelativeLocation>) -> Unit): ParticleShapeComposition {
        beforeInvokes.add(action)
        return this
    }

    fun applyPoint(
        point: RelativeLocation,
        dataSupplier: (RelativeLocation) -> CompositionData
    ): ParticleShapeComposition {
        points.add(PointsBuilder().addPoint(point) to dataSupplier)
        return this
    }

    fun applyBuilder(
        builder: PointsBuilder,
        dataSupplier: (RelativeLocation) -> CompositionData
    ): ParticleShapeComposition {
        points.add(builder to dataSupplier)
        return this
    }

    fun setReversedScaleOnDisableStatus(status: StatusHelper): ParticleShapeComposition {
        addPreTickAction {
            if (status.displayStatus == 2) {
                scaleReversed = true
            }
        }
        return this
    }

    fun setReversedScaleOnCompositionStatus(composition: ParticleComposition): ParticleShapeComposition {
        addPreTickAction {
            if (composition.status.getCurrentStatus() == StatusHelper.Status.DISABLE) {
                scaleReversed = true
            }
        }
        return this
    }

    override fun getCodec(): CommonStreamCodec<ParticleComposition> {
        throw NotImplementedError("此类只作为客户端嵌套使用， 不能单独生成！ ")
    }

    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        val res = HashMap<CompositionData, RelativeLocation>()
        points.forEach {
            res.putAll(
                it.first
                    .createWithCompositionData { rel ->
                        it.second(rel)
                    }
            )
        }
        return res
    }

    override fun beforeDisplay(map: Map<CompositionData, RelativeLocation>) {
        super.beforeDisplay(map)
        beforeInvokes.forEach { it(map) }
    }

    override fun onDisplay() {
        invokes.forEach { it() }
        addPreTickAction {
            spawnAge++
            if (scaleHelper == null || !scalePreTick) {
                return@addPreTickAction
            }
            if (!scaleReversed) {
                scaleHelper!!.doScale()
            } else {
                scaleHelper!!.doScaleReversed()
                if (reversedClean && scaleHelper!!.current <= 0) {
                    clear(false)
                }
            }
        }
    }
}
