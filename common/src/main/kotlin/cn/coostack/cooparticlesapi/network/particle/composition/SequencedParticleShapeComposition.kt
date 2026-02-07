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
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID

/**
 * - 只用于客户端嵌套（不允许单独发包生成）
 * - 继承 SequencedParticleComposition：提供 SortedMap 序列数据（按 CompositionData.order 排序）
 */
class SequencedParticleShapeComposition(uuid: UUID) : SequencedParticleComposition(Vec3.ZERO, null) {
    init {
        this.controlUUID = uuid
    }

    private val points = ArrayList<Pair<PointsBuilder, (RelativeLocation, Int) -> CompositionData>>()

    private val invokes = ArrayList<SequencedParticleShapeComposition.() -> Unit>()
    private val beforeInvokes =
        ArrayList<SequencedParticleShapeComposition.(map: SortedMap<CompositionData, RelativeLocation>) -> Unit>()

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

    fun loadScaleHelper(min: Double, max: Double, scalingTick: Int): SequencedParticleShapeComposition {
        scaleHelper = CompositionScaleHelper(min, max, scalingTick).apply {
            loadControler(this@SequencedParticleShapeComposition)
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
    ): SequencedParticleShapeComposition {
        scaleHelper = CompositionBezierScaleHelper(scaleTick, minScale, maxScale, c1, c2)
        scalePreTick = true
        scaleHelper!!.loadControler(this)
        return this
    }

    /**
     * 改用apply是因为 要防止和addPreTickAction冲突
     */
    fun applyDisplayAction(action: SequencedParticleShapeComposition.() -> Unit): SequencedParticleShapeComposition {
        invokes.add(action)
        return this
    }

    fun applyBeforeDisplayAction(
        action: SequencedParticleShapeComposition.(SortedMap<CompositionData, RelativeLocation>) -> Unit
    ): SequencedParticleShapeComposition {
        beforeInvokes.add(action)
        return this
    }

    fun applyPoint(
        point: RelativeLocation,
        dataSupplier: (RelativeLocation, Int) -> CompositionData
    ): SequencedParticleShapeComposition {
        points.add(PointsBuilder().addPoint(point) to dataSupplier)
        return this
    }

    fun applyPointRel(
        point: RelativeLocation,
        dataSupplier: (RelativeLocation) -> CompositionData
    ): SequencedParticleShapeComposition {
        points.add(PointsBuilder().addPoint(point) to { rel, o -> dataSupplier(rel) })
        return this
    }

    fun applyPointI(
        point: RelativeLocation,
        dataSupplier: (Int) -> CompositionData
    ): SequencedParticleShapeComposition {
        points.add(PointsBuilder().addPoint(point) to { rel, o -> dataSupplier(o) })
        return this
    }


    fun applyBuilder(
        builder: PointsBuilder,
        dataSupplier: (RelativeLocation, Int) -> CompositionData
    ): SequencedParticleShapeComposition {
        points.add(builder to dataSupplier)
        return this
    }

    fun applyBuilderI(
        builder: PointsBuilder,
        dataSupplier: (Int) -> CompositionData
    ): SequencedParticleShapeComposition {
        points.add(builder to { rel, o -> dataSupplier(o) })
        return this
    }

    fun applyBuilderRel(
        builder: PointsBuilder,
        dataSupplier: (RelativeLocation) -> CompositionData
    ): SequencedParticleShapeComposition {
        points.add(builder to { rel, o -> dataSupplier(rel) })
        return this
    }

    fun setReversedScaleOnDisableStatus(status: StatusHelper): SequencedParticleShapeComposition {
        addPreTickAction {
            if (status.displayStatus == 2) {
                scaleReversed = true
            }
        }
        return this
    }

    fun setReversedScaleOnCompositionStatus(composition: ParticleComposition): SequencedParticleShapeComposition {
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

    /**
     * SequencedParticleComposition 需要返回 SortedMap
     * TreeMap 会按 CompositionData.compareTo -> order 排序
     */
    override fun getParticleSequenced(): SortedMap<CompositionData, RelativeLocation> {
        val res: SortedMap<CompositionData, RelativeLocation> = TreeMap()
        var order = 0
        points.forEach { (builder, supplier) ->
            res.putAll(
                builder.createWithCompositionData { rel ->
                    supplier(rel, order++)
                }
            )
        }
        return res
    }


    override fun beforeDisplaySequenced(map: SortedMap<CompositionData, RelativeLocation>) {
        super.beforeDisplaySequenced(map)
        beforeInvokes.forEach {
            it(map)
        }
    }

    override fun onDisplay() {
        animate.clientOnly()
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
