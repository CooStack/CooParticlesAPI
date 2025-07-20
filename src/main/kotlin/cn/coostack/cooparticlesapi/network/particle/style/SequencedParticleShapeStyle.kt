package cn.coostack.cooparticlesapi.network.particle.style

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import cn.coostack.cooparticlesapi.utils.helper.ScaleHelper
import cn.coostack.cooparticlesapi.utils.helper.impl.StyleBezierValueScaleHelper
import cn.coostack.cooparticlesapi.utils.helper.impl.StyleScaleHelper
import cn.coostack.cooparticlesapi.utils.helper.impl.StyleStatusHelper
import net.minecraft.client.particle.ParticleTextureSheet
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Vec3d
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID
import java.util.function.Predicate

/**
 * 为了方便制作子图形作用于父图形 (进行不同方向的旋转, rotate)
 * 专门创建此类
 *
 * 无法单独生成
 * 继承此类的目的是为这个类创建一个专属形状的子类
 * client属性始终为true
 */
class SequencedParticleShapeStyle(uuid: UUID) :
    SequencedParticleStyle(64.0, uuid) {
    var scaleHelper: ScaleHelper? = null
    private var onDisplayInvoke: SequencedParticleShapeStyle.() -> Unit = {}
    private var beforeDisplayInvoke: SequencedParticleShapeStyle.(SortedMap<SortedStyleData, RelativeLocation>) -> Unit = {}
    private val pointBuilders = LinkedHashMap<PointsBuilder, (RelativeLocation) -> SortedStyleData>()

    /**
     * 用于控制粒子的播放顺序
     * 如果满足 animationConditions[animationIndex].first的条件
     * 则会执行 addMultiple(second (second > 0))
     * 或者 removeMultiple(second (second < 0)
     * 符合条件后 animationIndex 会递增
     * 直到到达边界
     */
    private val animationConditions = ArrayList<Pair<Predicate<SequencedParticleShapeStyle>, Int>>()
    var animationIndex = 0
        private set

    /**
     * 通过判断(外层) 提供的状态工具来决定是否反转缩放
     * 在toggleDisplay执行这个方法
     */
    val reverseFunctionFromStatus: (SequencedParticleShapeStyle, StyleStatusHelper) -> Unit =
        function@{ it, displayStatus ->
            // 必须设置缩放工具
            if (scaleHelper == null) return@function
            it.addPreTickAction {
                if (it.scaleReversed && it.scaleHelper!!.isZero()) {
                    it.remove()
                    return@addPreTickAction
                }
                if (displayStatus.displayStatus != 2) {
                    return@addPreTickAction
                }
                if (!it.scaleReversed) {
                    it.scaleReversed(false)
                }
            }
        }
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

    fun appendBuilder(
        pointsBuilder: PointsBuilder,
        dataBuilder: (RelativeLocation) -> SortedStyleData
    ): SequencedParticleShapeStyle {
        pointBuilders[pointsBuilder] = dataBuilder
        return this
    }

    fun appendPoint(
        point: RelativeLocation,
        dataBuilder: (RelativeLocation) -> SortedStyleData
    ): SequencedParticleShapeStyle {
        pointBuilders[
            PointsBuilder().also { it.addPoint(point) }
        ] = dataBuilder
        return this
    }

    /**
     * @param predicate 设置添加/删除动画的要求
     * @param add 向下的个数
     */
    fun appendAnimateCondition(
        predicate: Predicate<SequencedParticleShapeStyle>,
        add: Int
    ): SequencedParticleShapeStyle {
        animationConditions.add(predicate to add)
        return this
    }

    /**
     * @param max 直接让scale从最大开始
     */
    fun scaleReversed(max: Boolean): SequencedParticleShapeStyle {
        scaleHelper ?: return this
        scaleReversed = true
        if (max) {
            scaleHelper!!.toggleScale(scaleHelper!!.maxScale)
        }
        return this
    }

    fun loadScaleHelper(minScale: Double, maxScale: Double, scaleTick: Int): SequencedParticleShapeStyle {
        scaleHelper = StyleScaleHelper(minScale, maxScale, scaleTick)
        scalePreTick = true
        scaleHelper!!.loadControler(this)
        return this
    }

    fun loadScaleHelperBezierValue(
        minScale: Double,
        maxScale: Double,
        scaleTick: Int,
        c1: RelativeLocation,
        c2: RelativeLocation
    ): SequencedParticleShapeStyle {
        scaleHelper = StyleBezierValueScaleHelper(scaleTick, minScale, maxScale, c1, c2)
        scalePreTick = true
        scaleHelper!!.loadControler(this)
        return this
    }

    /**
     * 在display之前执行
     */
    fun toggleOnDisplay(toggleMethod: SequencedParticleShapeStyle.() -> Unit): SequencedParticleShapeStyle {
        onDisplayInvoke = toggleMethod
        return this
    }

    /**
     * 在生成粒子之前执行
     */
    fun toggleBeforeDisplay(toggleMethod: SequencedParticleShapeStyle.(SortedMap<SortedStyleData, RelativeLocation>) -> Unit): SequencedParticleShapeStyle {
        beforeDisplayInvoke = toggleMethod
        return this
    }


    override fun beforeDisplay(styles: SortedMap<SortedStyleData, RelativeLocation>) {
        beforeDisplayInvoke(styles)
    }


    override fun onDisplay() {
        onDisplayInvoke()
        addPreTickAction {
            if (animationIndex >= animationConditions.size) {
                return@addPreTickAction
            }
            val (predicate, add) = animationConditions[animationIndex]
            if (predicate.test(this@SequencedParticleShapeStyle)) {
                if (add > 0) {
                    addMultiple(add)
                } else {
                    removeMultiple(add)
                }
                animationIndex++
            }
        }
        addPreTickAction {
            spawnAge++
            if (!scalePreTick || scaleHelper == null) {
                return@addPreTickAction
            }

            if (scaleReversed) {
                scaleHelper!!.doScaleReversed()
            } else {
                scaleHelper!!.doScale()
            }
        }
    }

    private var count = -1
    override fun getParticlesCount(): Int {
        // 调用此方法比初始化count早
        if (count == -1 || count == 0) {
            count = getCurrentFramesSequenced().size
        }
        return count
    }

    override fun getCurrentFramesSequenced(): SortedMap<SortedStyleData, RelativeLocation> {
        val res = TreeMap<SortedStyleData, RelativeLocation>()
        pointBuilders.forEach { entry ->
            res.putAll(entry.key.createWithSequencedStyleData { it, order ->
                entry.value(it)
            })
        }
        return res
    }

    override fun writePacketArgsSequenced(): Map<String, ParticleControlerDataBuffer<*>> {
        return mapOf()
    }

    override fun readPacketArgsSequenced(args: Map<String, ParticleControlerDataBuffer<*>>) {
    }


    fun fastRotateToPlayerView(player: PlayerEntity) {
        rotateParticlesToPoint(RelativeLocation.of(player.rotationVector))
    }

    fun fastStyleData(order: Int, color: Vec3d, displayer: (UUID) -> ParticleDisplayer): SortedStyleData {
        return SortedStyleData(displayer, order).withParticleHandler {
            this.colorOfRGB(color.x.toInt(), color.y.toInt(), color.z.toInt())
        } as SortedStyleData
    }

    fun fastStyleData(
        order: Int,
        color: Vec3d,
        sheet: ParticleTextureSheet,
        displayer: (UUID) -> ParticleDisplayer
    ): SortedStyleData {
        return SortedStyleData(displayer, order).withParticleHandler {
            this.colorOfRGB(color.x.toInt(), color.y.toInt(), color.z.toInt())
            this.textureSheet = sheet
        } as SortedStyleData
    }

    fun fastStyleData(
        order: Int,
        color: Vec3d,
        sheet: ParticleTextureSheet,
        size: Float,
        displayer: (UUID) -> ParticleDisplayer
    ): SortedStyleData {
        return SortedStyleData(displayer, order).withParticleHandler {
            this.colorOfRGB(color.x.toInt(), color.y.toInt(), color.z.toInt())
            this.textureSheet = sheet
            this.size = size
        } as SortedStyleData
    }

    fun fastStyleData(
        order: Int,
        color: Vec3d,
        sheet: ParticleTextureSheet,
        size: Float,
        alpha: Float,
        displayer: (UUID) -> ParticleDisplayer
    ): SortedStyleData {
        return SortedStyleData(displayer, order).withParticleHandler {
            this.colorOfRGB(color.x.toInt(), color.y.toInt(), color.z.toInt())
            this.textureSheet = sheet
            this.size = size
            this.particleAlpha = alpha
        } as SortedStyleData
    }

    fun fastStyleData(
        order: Int,
        sheet: ParticleTextureSheet,
        displayer: (UUID) -> ParticleDisplayer
    ): SortedStyleData {
        return SortedStyleData(displayer, order).withParticleHandler {
            this.textureSheet = sheet
        } as SortedStyleData
    }
}