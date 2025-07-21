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
import java.util.UUID

/**
 * 为了方便制作子图形作用于父图形 (进行不同方向的旋转, rotate)
 * 专门创建此类
 *
 * 此类没有Provider 因此无法单独生成
 *
 * 继承此类的目的是为这个类创建一个专属形状的子类
 * 这种子类可以拥有Provider
 */
open class ParticleShapeStyle(uuid: UUID) :
    ParticleGroupStyle(64.0, uuid) {
    var scaleHelper: ScaleHelper? = null

    /**
     * 增加可复用性
     */
    private val displayInvokes = mutableListOf<ParticleShapeStyle.() -> Unit>()
    private val beforeDisplayInvokes = mutableListOf<ParticleShapeStyle.(Map<StyleData, RelativeLocation>) -> Unit>()
    private val pointBuilders = LinkedHashMap<PointsBuilder, (RelativeLocation) -> StyleData>()

    /**
     * 通过判断(外层) 提供的状态工具来决定是否反转缩放
     * 在toggleDisplay执行这个方法
     */
    val reverseFunctionFromStatus: (ParticleShapeStyle, StyleStatusHelper) -> Unit = function@{ it, displayStatus ->
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

    fun appendBuilder(pointsBuilder: PointsBuilder, dataBuilder: (RelativeLocation) -> StyleData): ParticleShapeStyle {
        pointBuilders[pointsBuilder] = dataBuilder
        return this
    }

    fun appendPoint(point: RelativeLocation, dataBuilder: (RelativeLocation) -> StyleData): ParticleShapeStyle {
        pointBuilders[
            PointsBuilder().also { it.addPoint(point) }
        ] = dataBuilder
        return this
    }

    /**
     * @param max 直接让scale从最大开始
     */
    fun scaleReversed(max: Boolean): ParticleShapeStyle {
        scaleHelper ?: return this
        scaleReversed = true
        if (max) {
            scaleHelper!!.toggleScale(scaleHelper!!.maxScale)
        }
        return this
    }

    fun loadScaleHelper(minScale: Double, maxScale: Double, scaleTick: Int): ParticleShapeStyle {
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
    ): ParticleShapeStyle {
        scaleHelper = StyleBezierValueScaleHelper(scaleTick, minScale, maxScale, c1, c2)
        scalePreTick = true
        scaleHelper!!.loadControler(this)
        return this
    }

    /**
     * 在display之前执行
     * 可以多次执行此方法, 不会发生方法覆盖
     */
    fun toggleOnDisplay(toggleMethod: ParticleShapeStyle.() -> Unit): ParticleShapeStyle {
        displayInvokes.add(toggleMethod)
        return this
    }

    /**
     * 在生成粒子之前执行
     */
    fun toggleBeforeDisplay(toggleMethod: ParticleShapeStyle.(Map<StyleData, RelativeLocation>) -> Unit): ParticleShapeStyle {
        beforeDisplayInvokes.add(toggleMethod)
        return this
    }

    override fun beforeDisplay(styles: Map<StyleData, RelativeLocation>) {
        beforeDisplayInvokes.forEach { it(styles) }
    }

    override fun getCurrentFrames(): Map<StyleData, RelativeLocation> {
        val res = HashMap<StyleData, RelativeLocation>()
        pointBuilders.forEach { entry ->
            res.putAll(entry.key.createWithStyleData { entry.value(it) })
        }
        return res
    }

    override fun onDisplay() {
        displayInvokes.forEach {
            it()
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

    override fun writePacketArgs(): Map<String, ParticleControlerDataBuffer<*>> {
        return mapOf()
    }

    override fun readPacketArgs(args: Map<String, ParticleControlerDataBuffer<*>>) {
    }

    fun fastRotateToPlayerView(player: PlayerEntity) {
        rotateParticlesToPoint(RelativeLocation.of(player.rotationVector))
    }

    fun fastStyleData(color: Vec3d, displayer: (UUID) -> ParticleDisplayer): StyleData {
        return StyleData(displayer).withParticleHandler {
            this.colorOfRGB(color.x.toInt(), color.y.toInt(), color.z.toInt())
        }
    }

    fun fastStyleData(color: Vec3d, sheet: ParticleTextureSheet, displayer: (UUID) -> ParticleDisplayer): StyleData {
        return StyleData(displayer).withParticleHandler {
            this.colorOfRGB(color.x.toInt(), color.y.toInt(), color.z.toInt())
            this.textureSheet = sheet
        }
    }

    fun fastStyleData(
        color: Vec3d,
        sheet: ParticleTextureSheet,
        size: Float,
        displayer: (UUID) -> ParticleDisplayer
    ): StyleData {
        return StyleData(displayer).withParticleHandler {
            this.colorOfRGB(color.x.toInt(), color.y.toInt(), color.z.toInt())
            this.textureSheet = sheet
            this.size = size
        }
    }

    fun fastStyleData(
        color: Vec3d,
        sheet: ParticleTextureSheet,
        size: Float,
        alpha: Float,
        displayer: (UUID) -> ParticleDisplayer
    ): StyleData {
        return StyleData(displayer).withParticleHandler {
            this.colorOfRGB(color.x.toInt(), color.y.toInt(), color.z.toInt())
            this.textureSheet = sheet
            this.size = size
            this.particleAlpha = alpha
        }
    }

    fun fastStyleData(sheet: ParticleTextureSheet, displayer: (UUID) -> ParticleDisplayer): StyleData {
        return StyleData(displayer).withParticleHandler {
            this.textureSheet = sheet
        }
    }
}