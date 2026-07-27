package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.cparticle.CParticleColorCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.textureOfBlock
import cn.coostack.cooparticlesapi.cparticle.textureOfItem
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * # GPU 粒子发射器测试 (cparticle 压力测试用例)
 *
 * 发射器只负责产出彼此独立的 [ControlableParticleData]。这个压测使用同一个模板，
 * 普通 emitter 也可以在一次 `genParticles()` 中按 [ControlableParticleData.sign] 混合不同 data。
 * [ControlableCParticleData] 不再变成逐个 `ControlableParticle` 对象,
 * 而是直接写入 GPU 粒子系统的 SoA 缓冲 — 运动由 [cparticleForces] 声明的力场
 * 在 compute shader (或 CPU 并行回退) 中统一驱动, 渲染坍缩为单次 instanced draw.
 *
 * 默认参数下稳态粒子数 ≈ [spawnPerTick] × [particleMaxAge] = 600 × 170 ≈ **10.2 万**,
 * 用于验证 10 万粒子 60FPS 指标. 调小 `spawn_per_tick` 可降低负载.
 *
 * ## GPU 模式下的语义差异 (与普通 emitter 相比)
 * - 不执行 [singleParticleAction] 的逐粒子 tick 回调
 * - 不做方块/实体碰撞, 不派发 ParticleEvent
 * - 不执行 `singleParticleDeathAction` 粒子重生
 *
 * 发射器自身的 `gravity` / `airDensity` / 全局风会被桥接层自动映射为 GPU 力场,
 * 无需在 [cparticleForces] 里重复声明.
 */
@CooAutoRegister
class TestCParticleEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {

    /** 粒子外观模板 (贴图/透明度/限速等一次性属性) */
    @CodecField
    var template = ControlableCParticleData().apply {
        setTextureSheet(CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT)
        size = 0.10f
        alpha = 0.85f
        maxAge = 170
        light = 15
        speedLimit = 2.0
        visibleRange = 192f
        updateMode = CParticleUpdateMode.STATIC
        alphaCurve = LIFETIME_ALPHA
        sizeCurve = LIFETIME_SIZE
    }

    /** 每 tick 生成的粒子数 (稳态数量 = 本值 × [particleMaxAge]) */
    @CodecField
    var spawnPerTick = 600

    /** 粒子存活 tick 数 */
    @CodecField
    var particleMaxAge = 170

    /** 单个粒子边长 */
    @CodecField
    var particleSize = 0.10f

    /** 生成圆盘半径 (粒子在以发射器为心的水平圆盘上均匀生成) */
    @CodecField
    var emitRadius = 2.4

    /** 初速度大小 (向上为主, 略微向外; 其余交给力场) */
    @CodecField
    var spreadSpeed = 0.10

    /** 粒子出生时的颜色 */
    @CodecField
    var colorStart = Vector3f(0.20f, 0.72f, 1.00f)

    /** 粒子死亡前的颜色 */
    @CodecField
    var colorEnd = Vector3f(1.00f, 0.36f, 0.12f)

    /** 漩涡切向强度 (龙卷风打旋力度) */
    @CodecField
    var vortexSwirl = 0.55

    /** 漩涡径向吸入强度 (收拢成漏斗) */
    @CodecField
    var vortexRadialPull = 0.16

    /** 漩涡轴向升力 */
    @CodecField
    var vortexLift = 0.05

    /** 漩涡作用范围尺度 */
    @CodecField
    var vortexRange = 9.0

    /** 噪声扰动强度 (0 = 关闭) */
    @CodecField
    var noiseStrength = 0.030

    /** 指数阻尼 (0 = 关闭; 防止速度被力场持续累加至发散) */
    @CodecField
    var dragDamping = 0.045

    private val random = Random(System.nanoTime())
    private val cachedColorStart = Vector3f(colorStart)
    private val cachedColorEnd = Vector3f(colorEnd)
    private var cachedColorCurve = CParticleColorCurve.linear(colorStart, colorEnd)

    private fun lifetimeColorCurve(): CParticleColorCurve {
        if (cachedColorStart != colorStart || cachedColorEnd != colorEnd) {
            cachedColorStart.set(colorStart)
            cachedColorEnd.set(colorEnd)
            cachedColorCurve = CParticleColorCurve.linear(cachedColorStart, cachedColorEnd)
        }
        return cachedColorCurve
    }

    override fun doTick() {
    }

    /**
     * GPU 力场声明 — 每 tick 与发射器状态同步一次 (打包进 SSBO/uniform, 不逐粒子分配).
     * 中心点用 lambda 取 [pos], 因此力场会跟随发射器移动.
     */
    override fun cparticleForces(): List<CParticleForce> {
        val forces = ArrayList<CParticleForce>(3)
        if (vortexSwirl != 0.0 || vortexRadialPull != 0.0 || vortexLift != 0.0) {
            forces += CParticleForce.Vortex(
                center = { pos },
                axis = Vec3(0.0, 1.0, 0.0),
                swirlStrength = vortexSwirl,
                radialPull = vortexRadialPull,
                axialLift = vortexLift,
                range = vortexRange,
                falloffPower = 2.0,
                minDistance = 0.25,
            )
        }
        if (noiseStrength > 0.0) {
            forces += CParticleForce.Noise(
                strength = noiseStrength,
                frequency = 0.40,
                speed = 0.03,
                clampSpeed = 1.5,
            )
        }
        if (dragDamping > 0.0) {
            forces += CParticleForce.ExpDrag(damping = dragDamping)
        }
        return forces
    }

    /**
     * 在水平圆盘上均匀撒点.
     *
     * 注意: 这里每个粒子仍要 clone 一份 data (走的是现有 emitter API 的通用契约,
     * CPU 回退路径需要独立实例). GPU 侧的模拟与渲染本身是零分配的 —
     * 这些 clone 是接入旧 API 的固定开销, 与粒子存活数量无关.
     */
    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        val count = spawnPerTick
        if (count <= 0) return emptyList()

        val result = ArrayList<Pair<ControlableParticleData, RelativeLocation>>(count)
        val colorCurve = lifetimeColorCurve()
        repeat(count) {
            val angle = random.nextDouble(0.0, TAU)
            // sqrt 采样保证圆盘内均匀分布 (否则会向圆心聚集)
            val radius = emitRadius * sqrt(random.nextDouble())
            val cosA = cos(angle)
            val sinA = sin(angle)

            val data = template.clone().apply {
                color = Vector3f(1f)
                this.colorCurve = colorCurve
                size = particleSize
                maxAge = particleMaxAge
                velocity = Vec3(
                    cosA * spreadSpeed * 0.35,
                    spreadSpeed,
                    sinA * spreadSpeed * 0.35
                )
            }
            result += data to RelativeLocation(cosA * radius, 0.0, sinA * radius)
        }
        return result
    }

    /** GPU 入池成功时不会被调用；选择性回退或满池回退时仍走这里 */
    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float
    ) {
    }

    companion object {
        private val LIFETIME_ALPHA = CParticleCurve.fadeInOut(fadeIn = 0.08f, fadeOut = 1f)
        private val LIFETIME_SIZE = CParticleCurve.of(
            0f to 0.35f,
            0.12f to 1f,
            0.82f to 1f,
            1f to 0.2f,
        )
        private const val TAU = Math.PI * 2.0
    }
}
