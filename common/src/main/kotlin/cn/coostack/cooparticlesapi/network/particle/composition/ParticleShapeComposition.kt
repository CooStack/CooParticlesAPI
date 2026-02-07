package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import cn.coostack.cooparticlesapi.utils.helper.ScaleHelper
import cn.coostack.cooparticlesapi.utils.helper.StatusHelper
import cn.coostack.cooparticlesapi.utils.helper.impl.composition.CompositionBezierScaleHelper
import cn.coostack.cooparticlesapi.utils.helper.impl.composition.CompositionScaleHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import java.util.UUID

class ParticleShapeComposition(uuid: UUID) : ParticleComposition(Vec3.ZERO, null) {
    init {
        this.controlUUID = uuid
    }

    private val points = ArrayList<Pair<PointsBuilder, (RelativeLocation) -> CompositionData>>()
    private val invokes = ArrayList<ParticleShapeComposition.() -> Unit>()
    private val beforeInvokes =
        ArrayList<ParticleShapeComposition.(map: Map<CompositionData, RelativeLocation>) -> Unit>()

    private var scaleHelper: ScaleHelper? = null
    var spawnAge = 0

    /**
     * 设置为true时 会利用scaleHelper 每tick增长一点
     */
    var scalePreTick = false
        private set

    /**
     * 设置为true时 利用scaleHelper 每tick减弱一点
     */
    var scaleReversed = false
        private set

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

    /**
     * 改用apply是因为 要防止和addPreTickAction冲突
     *
     * @return
     */
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


    override fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleComposition> {
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
            }
        }
    }
}