package cn.coostack.cooparticlesapi.test.options.particle.composition

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.compat.CParticleCompositions
import cn.coostack.cooparticlesapi.cparticle.compat.CParticleDisplayer
import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.ImagePointBuilder
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * # GPU 粒子 Composition 测试 (多层旋转法阵)
 *
 * 演示 "Composition = 特定的 display": composition 依旧负责**形状与层级控制**
 * (每个槽位一个 [CompositionData] + 相对坐标), 只是把槽位的 displayer 换成
 * [CParticleCompositions.data] — 该槽位便渲染为 GPU 粒子.
 *
 * 关键点: composition 的控制语义**完整保留**.
 * [onDisplay] 里挂的 `rotateAsAxis` 每 tick 旋转整个法阵, 旋转经
 * `toggleRelative()` 写入每个槽位句柄 ([cn.coostack.cooparticlesapi.cparticle.compat.CParticleControlable])
 * 直接落到 SoA 存储 — 没有逐粒子 tick 对象, 渲染仍是每层一次 instanced draw.
 *
 * 默认 [ringCount] × [pointsPerRing] = 4 × 320 = 1280 个粒子槽位.
 */
@CooAutoRegister
class TestCParticleComposition(position: Vec3, world: Level? = null) : AutoParticleComposition(position, world) {

    /** 圆环层数 */
    @CodecField
    var ringCount = 4

    /** 每层圆环上的粒子数 */
    @CodecField
    var pointsPerRing = 320

    /** 最外层半径 */
    @CodecField
    var radius = 2.6

    /** 层间垂直间距 */
    @CodecField
    var ringSpacing = 0.35

    /** 单个粒子边长 */
    @CodecField
    var particleSize = 0.16f

    /** 内层颜色 */
    @CodecField
    var colorInner = Vector3f(0.35f, 0.85f, 1.00f)

    /** 外层颜色 */
    @CodecField
    var colorOuter = Vector3f(0.85f, 0.30f, 1.00f)

    /** 每 tick 自转弧度 (0 = 不旋转) */
    @CodecField
    var rotateSpeed = PI / 90.0

    private var dis = false

    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        return PointsBuilder()
            .addImage(
                ImagePointBuilder(ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test/test2.png"))
                    .scale(1.0)
                    .step(0.1)
            )
            .createWithCompositionData {
                CompositionData().setDisplayerSupplier {
                    ParticleDisplayer.withCParticle(it)
                }
                    .addCParticleInstanceInit {
                        effect= ControlableEndRodEffect(UUID.randomUUID())
                    }
            }

    }

    override fun onDisplay() {
        playCParticleVisualTransition(
            10f, alphaCurve =
                CParticleCurve.linear(0f, 1f)
        )
        if (rotateSpeed == 0.0) return
        addPreTickAction {
            rotateAsAxis(rotateSpeed)
            if (status.isDisable()) {
                if (dis) return@addPreTickAction
                dis = true
                playCParticleVisualTransition(
                    status.closedInternal.toFloat(),
                    alphaCurve = CParticleCurve.linear(1f, 0f)
                )
            }
        }
    }

    override fun remove() {
        if (status.isDisable()) {
            super.remove()
        } else {
            status.disable()
        }
    }

    private fun gradientColor(progress: Float): Vector3f {
        val t = progress.coerceIn(0f, 1f)
        return Vector3f(
            colorInner.x + (colorOuter.x - colorInner.x) * t,
            colorInner.y + (colorOuter.y - colorInner.y) * t,
            colorInner.z + (colorOuter.z - colorInner.z) * t
        )
    }
}
