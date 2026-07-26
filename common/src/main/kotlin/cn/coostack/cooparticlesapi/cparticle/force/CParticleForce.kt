package cn.coostack.cooparticlesapi.cparticle.force

import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleAttractionCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleDragCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleFlowFieldCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleNoiseCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleRotationForceCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleVortexCommand
import net.minecraft.world.phys.Vec3
import java.util.function.Supplier
import kotlin.math.exp

/**
 * # GPU 粒子力场
 *
 * [ParticleCommand] 的 GPU 可执行等价物: 每个力场是纯数据(POD),
 * 每 tick 被打包成 `16 float` 的结构上传 (compute shader uniform 数组),
 * CPU 回退模拟器读取**同一份打包数据**执行相同数学 —— 两端行为一致.
 *
 * 动态中心点使用 [Supplier] (对应 command 的 `Supplier<Vec3>`), 在每 tick 打包时求值一次.
 *
 * 打包布局 (每个力场 4 x vec4):
 * ```
 * [ 0.. 3] type, a, b, c
 * [ 4.. 7] P.xyz (位置类参数, 系统原点相对坐标), d
 * [ 8..11] Q.xyz (轴/风向), e
 * [12..15] f, g, h, i
 * ```
 */
sealed class CParticleForce {
    companion object {
        /** 单系统最大力场数 (与 GLSL 中的 MAX_FORCES 一致) */
        const val MAX_FORCES = 16

        /** 每个力场占用的 float 数 */
        const val STRIDE = 16

        const val TYPE_GRAVITY = 1
        const val TYPE_ENV_DRAG = 2
        const val TYPE_EXP_DRAG = 3
        const val TYPE_WIND = 4
        const val TYPE_VORTEX = 5
        const val TYPE_ATTRACT = 6
        const val TYPE_ROTATION = 7
        const val TYPE_NOISE = 8
        const val TYPE_FLOW_FIELD = 9

        /** 0.5 * ρ * Cd * A * 0.05 — 与 ClassParticleEmitters.updatePhysics 完全一致的阻力系数 */
        @JvmStatic
        fun envDragK(airDensity: Double): Double =
            0.5 * airDensity * PhysicConstant.DRAG_COEFFICIENT * PhysicConstant.CROSS_SECTIONAL_AREA * 0.05

        /**
         * 把内置 [ParticleCommand] 转换为等价力场 (无法转换的返回 null).
         * 支持: Drag / Vortex / Attraction / RotationForce / FlowField / Noise
         */
        @JvmStatic
        fun fromCommand(command: ParticleCommand): CParticleForce? = when (command) {
            is ParticleDragCommand -> ExpDrag(command.damping, command.minSpeed, command.linear)
            is ParticleVortexCommand -> Vortex(
                { command.center.get() }, command.axis,
                command.swirlStrength, command.radialPull, command.axialLift,
                command.range, command.falloffPower, command.minDistance
            )

            is ParticleAttractionCommand -> Attraction(
                { command.target.get() },
                command.strength, command.range, command.falloffPower, command.minDistance
            )

            is ParticleRotationForceCommand -> RotationForce(
                { command.center.get() }, command.axis,
                command.strength, command.range, command.falloffPower
            )

            is ParticleFlowFieldCommand -> FlowField(
                command.amplitude, command.frequency, command.timeScale,
                command.phaseOffset, command.worldOffset
            )

            // ParticleNoiseCommand 的参数全部 private, 只能反射提取 (转换时执行一次)
            is ParticleNoiseCommand -> runCatching {
                fun readDouble(name: String): Double =
                    ParticleNoiseCommand::class.java.getDeclaredField(name)
                        .apply { isAccessible = true }.getDouble(command)

                val useLife = ParticleNoiseCommand::class.java.getDeclaredField("useLifeCurve")
                    .apply { isAccessible = true }.getBoolean(command)
                Noise(
                    readDouble("strength"), readDouble("frequency"), readDouble("speed"),
                    readDouble("clampSpeed"), readDouble("affectY"), useLife
                )
            }.getOrNull()

            else -> null
        }
    }

    /**
     * 打包进 [out] 的 [base] 偏移处 (16 floats).
     * @param origin 系统原点 (粒子位置为原点相对坐标, 位置类参数需要减去它)
     */
    abstract fun pack(out: FloatArray, base: Int, origin: Vec3)

    protected fun FloatArray.p(base: Int, i: Int, v: Double) {
        this[base + i] = v.toFloat()
    }

    protected fun FloatArray.type(base: Int, t: Int) {
        this[base] = t.toFloat()
    }

    // ------------------------------------------------------------------

    /** 恒定加速度 (重力 = Gravity(Vec3(0, -g, 0))) */
    class Gravity(var accel: Vec3) : CParticleForce() {
        constructor(gravity: Double) : this(Vec3(0.0, -gravity, 0.0))

        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, TYPE_GRAVITY)
            out.p(base, 4, accel.x); out.p(base, 5, accel.y); out.p(base, 6, accel.z)
        }
    }

    /**
     * 环境空气阻力 — 与 `ClassParticleEmitters.updatePhysics` 的 airResistanceForce 相同:
     * `dv = -k * speed² * v̂` (speed > 0.01 才生效)
     */
    class EnvDrag(var airDensity: Double) : CParticleForce() {
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, TYPE_ENV_DRAG)
            out.p(base, 1, envDragK(airDensity))
        }
    }

    /**
     * 指数阻尼 — 与 [ParticleDragCommand] 相同:
     * `v *= exp(-damping)`; 可选线性项与最小速度归零
     */
    class ExpDrag(
        var damping: Double = 0.15,
        var minSpeed: Double = 0.0,
        var linear: Double = 0.0,
    ) : CParticleForce() {
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, TYPE_EXP_DRAG)
            out.p(base, 1, if (damping <= 0.0) 1.0 else exp(-damping))
            out.p(base, 2, if (linear > 0.0) (1.0 - linear).coerceIn(0.0, 1.0) else 1.0)
            out.p(base, 3, minSpeed)
        }
    }

    /**
     * 风力 — 与 `WindDirections.handleWindForce` 相同:
     * `dv = k * |w - v| * (w - v)`
     *
     * @param rangeMode 0=全局 1=球形范围 2=盒形范围 (对应 Global/Ball/BoxWindDirection)
     */
    class Wind(
        var wind: Supplier<Vec3>,
        var airDensity: Double = PhysicConstant.SEA_AIR_DENSITY,
        var rangeMode: Int = 0,
        var rangeCenter: Supplier<Vec3> = Supplier { Vec3.ZERO },
        /** 球: x=半径; 盒: xyz=半边长 */
        var rangeSize: Vec3 = Vec3.ZERO,
    ) : CParticleForce() {
        constructor(wind: Vec3, airDensity: Double = PhysicConstant.SEA_AIR_DENSITY) :
                this(Supplier { wind }, airDensity)

        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, TYPE_WIND)
            out.p(base, 1, envDragK(airDensity))
            out.p(base, 2, rangeMode.toDouble())
            val c = rangeCenter.get()
            out.p(base, 4, c.x - origin.x); out.p(base, 5, c.y - origin.y); out.p(base, 6, c.z - origin.z)
            val w = wind.get()
            out.p(base, 8, w.x); out.p(base, 9, w.y); out.p(base, 10, w.z)
            out.p(base, 12, rangeSize.x); out.p(base, 13, rangeSize.y); out.p(base, 14, rangeSize.z)
        }
    }

    /** 漩涡 — 数学与 [ParticleVortexCommand] 完全一致 */
    class Vortex(
        var center: () -> Vec3 = { Vec3.ZERO },
        var axis: Vec3 = Vec3(0.0, 1.0, 0.0),
        var swirlStrength: Double = 0.8,
        var radialPull: Double = 0.35,
        var axialLift: Double = 0.0,
        var range: Double = 10.0,
        var falloffPower: Double = 2.0,
        var minDistance: Double = 0.2,
    ) : CParticleForce() {
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, TYPE_VORTEX)
            out.p(base, 1, swirlStrength); out.p(base, 2, radialPull); out.p(base, 3, axialLift)
            val c = center()
            out.p(base, 4, c.x - origin.x); out.p(base, 5, c.y - origin.y); out.p(base, 6, c.z - origin.z)
            out.p(base, 7, range)
            val a = axis.normalize()
            out.p(base, 8, a.x); out.p(base, 9, a.y); out.p(base, 10, a.z)
            out.p(base, 11, falloffPower)
            out.p(base, 12, minDistance)
        }
    }

    /** 吸引/排斥 — 数学与 [ParticleAttractionCommand] 完全一致 (strength<0 = 排斥) */
    class Attraction(
        var target: () -> Vec3 = { Vec3.ZERO },
        var strength: Double = 0.8,
        var range: Double = 8.0,
        var falloffPower: Double = 2.0,
        var minDistance: Double = 0.25,
    ) : CParticleForce() {
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, TYPE_ATTRACT)
            out.p(base, 1, strength); out.p(base, 2, range); out.p(base, 3, falloffPower)
            val c = target()
            out.p(base, 4, c.x - origin.x); out.p(base, 5, c.y - origin.y); out.p(base, 6, c.z - origin.z)
            out.p(base, 7, minDistance)
        }
    }

    /** 切向旋转力 — 数学与 [ParticleRotationForceCommand] 完全一致 */
    class RotationForce(
        var center: () -> Vec3 = { Vec3.ZERO },
        var axis: Vec3 = Vec3(0.0, 1.0, 0.0),
        var strength: Double = 0.35,
        var range: Double = 8.0,
        var falloffPower: Double = 2.0,
    ) : CParticleForce() {
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, TYPE_ROTATION)
            out.p(base, 1, strength); out.p(base, 2, range); out.p(base, 3, falloffPower)
            val c = center()
            out.p(base, 4, c.x - origin.x); out.p(base, 5, c.y - origin.y); out.p(base, 6, c.z - origin.z)
            val a = axis.normalize()
            out.p(base, 8, a.x); out.p(base, 9, a.y); out.p(base, 10, a.z)
        }
    }

    /**
     * 值噪声扰动 — 数学与 [ParticleNoiseCommand] 一致 (hash3 + fade 三线性),
     * per-particle 稳定 seed 由槽位派生.
     */
    class Noise(
        var strength: Double = 0.1,
        var frequency: Double = 0.35,
        var speed: Double = 0.02,
        var clampSpeed: Double = 2.0,
        var affectY: Double = 1.0,
        var useLifeCurve: Boolean = false,
        var seedOffset: Int = 0,
    ) : CParticleForce() {
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, TYPE_NOISE)
            out.p(base, 1, strength); out.p(base, 2, frequency); out.p(base, 3, speed)
            out.p(base, 7, clampSpeed)
            out.p(base, 11, affectY)
            out.p(base, 12, if (useLifeCurve) 1.0 else 0.0)
            out.p(base, 13, seedOffset.toDouble())
        }
    }

    /** 解析流场 — 数学与 [ParticleFlowFieldCommand] 完全一致 (sin/cos 卷曲场) */
    class FlowField(
        var amplitude: Double = 0.15,
        var frequency: Double = 0.25,
        var timeScale: Double = 0.06,
        var phaseOffset: Double = 0.0,
        var worldOffset: Vec3 = Vec3.ZERO,
    ) : CParticleForce() {
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, TYPE_FLOW_FIELD)
            out.p(base, 1, amplitude); out.p(base, 2, frequency); out.p(base, 3, timeScale)
            out.p(base, 4, worldOffset.x); out.p(base, 5, worldOffset.y); out.p(base, 6, worldOffset.z)
            out.p(base, 7, phaseOffset)
        }
    }
}
