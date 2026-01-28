package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.animation.timeline.AngleAnimator
import cn.coostack.cooparticlesapi.animation.timeline.Eases
import cn.coostack.cooparticlesapi.animation.timeline.Timeline
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleShapeComposition
import cn.coostack.cooparticlesapi.network.particle.composition.SequencedParticleShapeComposition
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.NoiseMode
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.FourierSeriesBuilder
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import cn.coostack.cooparticlesapi.utils.presets.FourierPresets
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * # 此类的作用
 * - 作为复杂生长动画的示例
 * # 讲解
 * ## 使用此类需要注意以下几个事项
 * 1. 必须使用Style / Group / Composition 嵌套的方式
 * 2. 目前只有旋转偏移生长，还没有直线/曲线 位移偏移
 * 3. 需要统一的图案，然后要用到 PointsBuilder 或者手搓圆也可以
 * @constructor
 * TODO
 *
 * @param position
 * @param world
 */
@CooAutoRegister
class TestGlowingAnimationComposition(position: Vec3, world: Level? = null) : AutoParticleComposition(position, world) {

    @CodecField
    var glowingTick = 50

    @CodecField
    var glowedCount = 8

    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        // 环型变换
        val sword = mutableListOf<RelativeLocation>()
        val proportion = 1.5
        val leftDown = RelativeLocation(-0.125 * sqrt(3.0), -0.125, 0.0) * proportion
        val rightDown = RelativeLocation(0.125 * sqrt(3.0), -0.125, 0.0) * proportion
        val leftUp = RelativeLocation(-0.25, 0.5, 0.0) * proportion
        val rightUp = RelativeLocation(0.25, 0.5, 0.0) * proportion
        val down = RelativeLocation(0.0, -0.6, 0.0) * proportion
        val up = RelativeLocation(0.0, 1.0, 0.0) * proportion
        val zero = RelativeLocation()
        val refiner = 20.0
        Math3DUtil.apply {
            sword.addAll(this.fillLine(zero, leftDown, refiner))
            sword.addAll(this.fillLine(zero, rightDown, refiner))
            sword.addAll(this.fillLine(leftUp, rightUp, refiner))
            sword.addAll(this.fillLine(leftUp, rightUp, refiner))
            sword.addAll(this.fillLine(down, up, refiner))
        }
        sword.onEach {
            it.y += 8.0
        }
        val res = mutableMapOf<CompositionData, RelativeLocation>()
        PointsBuilder()
            .addCircle(1.0, glowedCount)
            .createWithoutClone()
            .forEachIndexed { index, finalDir ->
                val finalAngle = index * PI * 2 / glowedCount
                res[
                    CompositionData()
                        .setDisplayerSupplier {
                            ParticleDisplayer.withComposition(
                                ParticleShapeComposition()
                                    .applyBuilder(
                                        PointsBuilder.of(sword).cloneBuilder()
                                            .applyNoiseOffset(0.1, 0.1, 0.1, NoiseMode.SPHERE_UNIFORM)
                                    ) {
                                        CompositionData()
                                    }
                                    .applyDisplayAction {
                                        val animator = AngleAnimator(
                                            glowingTick, finalAngle, Eases
                                                .bezierEase(
                                                    Vec2(0.98f, 0f),
                                                    Vec2(-0.02f, -1.0f)
                                                )
                                        )
                                        animator.reset()
                                        var glowing = true
                                        val timeline = Timeline()
                                            .step {
                                                if (!animator.finished) {
                                                    if (glowing) {
                                                        rotateAsAxis(animator.glowDelta())
                                                    } else {
                                                        rotateAsAxis(animator.fadeDelta())
                                                    }
                                                } else {
                                                    glowing = !glowing
                                                    if (!glowing) {
                                                        rotateAsAxis(animator.fadeDelta())
                                                    } else {
                                                        animator.reset()
                                                        rotateAsAxis(animator.glowDelta())
                                                    }
                                                }
                                                rotateAsAxis(PI / 64)
                                                false
                                            }
                                        axis = RelativeLocation.zAxis()
                                        addPreTickAction {
                                            timeline.doTick()
                                        }
                                    }
                            )
                        }
                ] = RelativeLocation()
            }

        repeat(3) {
            val angle = (it * 2 * PI) / (3 * 3)
            res[
                CompositionData()
                    .setDisplayerSupplier {
                        ParticleDisplayer.withComposition(
                            SequencedParticleShapeComposition()
                                .applyBuilder(
                                    PointsBuilder()
                                        .addFourierSeries(
                                            FourierSeriesBuilder()
                                                .count(720)
                                                .addFourier(1.0, 2.0)
                                                .addFourier(2.0, -1.0)
                                                .scale(3.0)
                                        )
                                ) { rel, o ->
                                    CompositionData()
                                        .apply {
                                            this.order = o
                                        }
                                }
                                .applyDisplayAction {
                                    val animator = AngleAnimator(
                                        glowingTick, angle, Eases
                                            .bezierEase(
                                                Vec2(0.98f, 0f),
                                                Vec2(-0.02f, -1.0f)
                                            )
                                    )
                                    animator.reset()
                                    var glowing = true
                                    val timeline = Timeline()
                                        .step {
                                            addMultiple(count * 2 / glowingTick)
                                            if (!animator.finished) {
                                                if (glowing) {
                                                    rotateToWithAngle(
                                                        RelativeLocation.zAxis(),
                                                        animator.glowDelta()
                                                    )
                                                } else {
                                                    rotateToWithAngle(
                                                        RelativeLocation.zAxis(),
                                                        animator.fadeDelta()
                                                    )
                                                }
                                            } else {
                                                glowing = !glowing
                                                if (!glowing) {
                                                    rotateToWithAngle(
                                                        RelativeLocation.zAxis(),
                                                        animator.fadeDelta()
                                                    )
                                                } else {
                                                    animator.reset()
                                                    rotateToWithAngle(
                                                        RelativeLocation.zAxis(),
                                                        animator.glowDelta()
                                                    )
                                                }
                                            }
                                            rotateToWithAngle(RelativeLocation.zAxis(), -PI / 64)
                                            false
                                        }
                                    addPreTickAction {
                                        timeline.doTick()
                                    }
                                }
                        )
                    }
            ] = RelativeLocation()
        }

        return res
    }

    override fun onDisplay() {
    }
}