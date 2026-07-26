package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * # GPU 粒子发射器测试 (cparticle 压力测试用例)
 *
 * 演示 "Emitter = 特定的 Data": 发射器只负责**产出 [ControlableParticleData]**,
 * 打开 [useCParticleSystem] 后这些数据不再变成逐个 `ControlableParticle` 对象,
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

    /** 打开 GPU 粒子路径 */
    override val useCParticleSystem: Boolean
        get() = true

    /** 池容量要 ≥ 稳态粒子数，否则溢出粒子会回退 CPU，影响压测结果 */
    override val cparticleCapacity: Int
        get() = 262144

    /** 压测粒子不需要逐粒子外观更新，避免保留十万个源对象。 */
    override fun cparticleUpdateMode(data: ControlableParticleData): CParticleUpdateMode =
        CParticleUpdateMode.STATIC

    /** 粒子外观模板 (贴图/透明度/限速等一次性属性) */
    @CodecField
    var template = ControlableParticleData().apply {
        setTextureSheet(CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT)
        size = 0.10f
        alpha = 0.85f
        maxAge = 170
        light = 15
        speedLimit = 2.0
        visibleRange = 192f
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

    /** 渐变起点颜色 */
    @CodecField
    var colorStart = Vector3f(0.20f, 0.72f, 1.00f)

    /** 渐变终点颜色 */
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
        val count = spawnPerTick.coerceIn(0, MAX_SPAWN_PER_TICK)
        if (count <= 0) return emptyList()

        val result = ArrayList<Pair<ControlableParticleData, RelativeLocation>>(count)
        for (index in 0 until count) {
            val progress = if (count <= 1) 0f else index.toFloat() / (count - 1).toFloat()
            val angle = random.nextDouble(0.0, TAU)
            // sqrt 采样保证圆盘内均匀分布 (否则会向圆心聚集)
            val radius = emitRadius * sqrt(random.nextDouble())
            val cosA = cos(angle)
            val sinA = sin(angle)

            val data = template.clone().apply {
                color = gradientColor(progress)
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

    private fun gradientColor(progress: Float): Vector3f {
        val t = progress.coerceIn(0f, 1f)
        return Vector3f(
            colorStart.x + (colorEnd.x - colorStart.x) * t,
            colorStart.y + (colorEnd.y - colorStart.y) * t,
            colorStart.z + (colorEnd.z - colorStart.z) * t
        )
    }

    companion object {
        /** 单 tick 生成上限, 防止参数误填导致瞬间打满粒子池 */
        const val MAX_SPAWN_PER_TICK = 4096

        private const val TAU = Math.PI * 2.0
    }
}
