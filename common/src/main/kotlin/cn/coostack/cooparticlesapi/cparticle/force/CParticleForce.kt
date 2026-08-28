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
 * [ParticleCommand] 的 CParticle 可执行等价物。每个力场保留 `16 float` payload，
 * 再由 [ForceCommand] 加上 selector header 后写入共享 Command SSBO。
 * 调用方显式选择 CPU 模拟时，CPU simulator 读取同一份 Command 打包数据并执行相同数学。
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
    /** 兼容 ABI 中写入 force payload 的类型编号。 */
    abstract val typeId: Int

    companion object {
        /** 每个力场占用的 float 数 */
        const val STRIDE = 16

        /** 旧版 uniform 路径支持的最大力场数；保留该 ABI 上限供兼容快路径使用。 */
        const val MAX_FORCES = 16

        const val TYPE_GRAVITY = 1
        const val TYPE_ENV_DRAG = 2
        const val TYPE_EXP_DRAG = 3
        const val TYPE_WIND = 4
        const val TYPE_VORTEX = 5
        const val TYPE_ATTRACT = 6
        const val TYPE_ROTATION = 7
        const val TYPE_NOISE = 8
        const val TYPE_FLOW_FIELD = 9
        const val TYPE_RADIAL = 10
        const val TYPE_DIRECTIONAL_WIND = 11
        const val TYPE_BLENDER_VORTEX = 12
        const val TYPE_MAGNETIC = 13
        const val TYPE_HARMONIC = 14
        const val TYPE_VELOCITY_DRAG = 15
        const val TYPE_CHARGE = 16
        const val TYPE_LENNARD_JONES = 17
        const val TYPE_TURBULENCE = 18
        const val TYPE_TEXTURE = 19
        const val TYPE_FLUID_FLOW = 20

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
        override val typeId: Int = TYPE_GRAVITY
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
        override val typeId: Int = TYPE_ENV_DRAG
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
        override val typeId: Int = TYPE_EXP_DRAG
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
        override val typeId: Int = TYPE_WIND
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
        override val typeId: Int = TYPE_VORTEX
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
        override val typeId: Int = TYPE_ATTRACT
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
        override val typeId: Int = TYPE_ROTATION
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
        override val typeId: Int = TYPE_NOISE
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
        override val typeId: Int = TYPE_FLOW_FIELD
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, TYPE_FLOW_FIELD)
            out.p(base, 1, amplitude); out.p(base, 2, frequency); out.p(base, 3, timeScale)
            out.p(base, 4, worldOffset.x); out.p(base, 5, worldOffset.y); out.p(base, 6, worldOffset.z)
            out.p(base, 7, phaseOffset)
        }
    }

    /** Blender 风格径向力（新类型 10）。 */
    class Radial(
        var center: Vec3 = Vec3.ZERO,
        var strength: Double = 1.0,
        var falloff: CParticleFalloff = CParticleFalloff(),
        var inverseSquare: Boolean = false,
    ) : CParticleForce() {
        override val typeId: Int = TYPE_RADIAL
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, typeId)
            out.p(base, 1, strength)
            out.p(base, 2, if (inverseSquare) 1.0 else 0.0)
            out.p(base, 4, center.x - origin.x); out.p(base, 5, center.y - origin.y); out.p(base, 6, center.z - origin.z)
            falloff.pack(out, base + 11)
        }
    }

    /** Blender 风格恒定方向风力，不读取粒子相对速度。 */
    class DirectionalWind(
        var axis: Vec3 = Vec3(0.0, 1.0, 0.0),
        var strength: Double = 1.0,
        var falloff: CParticleFalloff = CParticleFalloff(),
        var center: Vec3 = Vec3.ZERO,
    ) : CParticleForce() {
        override val typeId: Int = TYPE_DIRECTIONAL_WIND
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, typeId); out.p(base, 1, strength)
            val a = axis.safeNormalize()
            out.p(base, 4, center.x - origin.x); out.p(base, 5, center.y - origin.y); out.p(base, 6, center.z - origin.z)
            falloff.pack(out, base + 11, a)
        }
    }

    /** 与旧 Vortex 区分的 Blender 风格漩涡，支持切向、径向恢复和速度补偿。 */
    class BlenderVortex(
        var center: Vec3 = Vec3.ZERO,
        var axis: Vec3 = Vec3(0.0, 1.0, 0.0),
        var tangentialStrength: Double = 1.0,
        var radialStrength: Double = 0.0,
        var velocityCompensation: Double = 0.0,
        var falloff: CParticleFalloff = CParticleFalloff(),
    ) : CParticleForce() {
        override val typeId: Int = TYPE_BLENDER_VORTEX
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, typeId); out.p(base, 1, tangentialStrength); out.p(base, 2, radialStrength); out.p(base, 3, velocityCompensation)
            out.p(base, 4, center.x - origin.x); out.p(base, 5, center.y - origin.y); out.p(base, 6, center.z - origin.z)
            val a = axis.safeNormalize(); out.p(base, 8, a.x); out.p(base, 9, a.y); out.p(base, 10, a.z)
            falloff.pack(out, base + 11, a)
        }
    }

    /** 磁场力；粒子速度按 `cross(velocity, field)` 取得洛伦兹式速度增量。 */
    class Magnetic(
        var center: Vec3 = Vec3.ZERO,
        var axis: Vec3 = Vec3(0.0, 1.0, 0.0),
        var strength: Double = 1.0,
        var fieldMode: CParticleMagneticFieldMode = CParticleMagneticFieldMode.LINE,
        var falloff: CParticleFalloff = CParticleFalloff(),
    ) : CParticleForce() {
        override val typeId: Int = TYPE_MAGNETIC
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, typeId); out.p(base, 1, strength); out.p(base, 2, fieldMode.wireValue.toDouble())
            out.p(base, 4, center.x - origin.x); out.p(base, 5, center.y - origin.y); out.p(base, 6, center.z - origin.z)
            val a = axis.safeNormalize(); out.p(base, 8, a.x); out.p(base, 9, a.y); out.p(base, 10, a.z)
            falloff.pack(out, base + 11, a)
        }
    }

    /** Blender 弹簧力；stiffness/damping 使用每 tick 单位。 */
    class Harmonic(
        var center: Vec3 = Vec3.ZERO,
        var stiffness: Double = 1.0,
        var damping: Double = 0.0,
        var restLength: Double = 0.0,
        var falloff: CParticleFalloff = CParticleFalloff(),
    ) : CParticleForce() {
        override val typeId: Int = TYPE_HARMONIC
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, typeId); out.p(base, 1, stiffness); out.p(base, 2, damping); out.p(base, 3, restLength)
            out.p(base, 4, center.x - origin.x); out.p(base, 5, center.y - origin.y); out.p(base, 6, center.z - origin.z)
            falloff.pack(out, base + 11)
        }
    }

    /** 速度阻力，EXACT 对应 Blender 二次+一次阻力，EXP 为稳定指数模式。 */
    class VelocityDrag(
        var strength: Double = 0.0,
        var damping: Double = 0.0,
        var exact: Boolean = true,
        var falloff: CParticleFalloff = CParticleFalloff(),
    ) : CParticleForce() {
        override val typeId: Int = TYPE_VELOCITY_DRAG
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, typeId); out.p(base, 1, strength); out.p(base, 2, damping); out.p(base, 3, if (exact) 1.0 else 0.0)
            falloff.pack(out, base + 11)
        }
    }

    /** 带电粒子的点源力；charge 未设置时由 defaultCharge 提供。 */
    class Charge(
        var center: Vec3 = Vec3.ZERO,
        var strength: Double = 1.0,
        var defaultCharge: Double = 0.0,
        var falloff: CParticleFalloff = CParticleFalloff(),
    ) : CParticleForce() {
        override val typeId: Int = TYPE_CHARGE
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, typeId); out.p(base, 1, strength); out.p(base, 2, defaultCharge)
            out.p(base, 4, center.x - origin.x); out.p(base, 5, center.y - origin.y); out.p(base, 6, center.z - origin.z)
            falloff.pack(out, base + 11)
        }
    }

    /** 单点源 Lennard-Jones 近似；作用半径为 [sourceRadius] 与粒子 `radius` 之和。 */
    class LennardJones(
        var center: Vec3 = Vec3.ZERO,
        var strength: Double = 1.0,
        var sourceRadius: Double = 0.0,
        var falloff: CParticleFalloff = CParticleFalloff(),
    ) : CParticleForce() {
        override val typeId: Int = TYPE_LENNARD_JONES
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, typeId); out.p(base, 1, strength); out.p(base, 2, sourceRadius)
            out.p(base, 4, center.x - origin.x); out.p(base, 5, center.y - origin.y); out.p(base, 6, center.z - origin.z)
            falloff.pack(out, base + 11)
        }
    }

    /** Blender 风格近似湍流；GPU/CPU 使用固定 seed 与 tick 时间。 */
    class Turbulence(
        var strength: Double = 0.1,
        var size: Double = 1.0,
        var seed: Int = 0,
        var timeScale: Double = 0.0,
        var falloff: CParticleFalloff = CParticleFalloff(),
    ) : CParticleForce() {
        override val typeId: Int = TYPE_TURBULENCE
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, typeId); out.p(base, 1, strength); out.p(base, 2, size); out.p(base, 3, timeScale)
            out.p(base, 7, seed.toDouble()); falloff.pack(out, base + 11)
        }
    }

    /** 资源型纹理力。采样器由资源绑定层提供，缺失时由模拟器报告错误。 */
    class Texture(
        var resource: CParticleTextureResource,
        var strength: Double = 1.0,
        var mode: CParticleTextureForceMode = CParticleTextureForceMode.VECTOR,
        var nabla: Double = 0.01,
        var falloff: CParticleFalloff = CParticleFalloff(),
    ) : CParticleForce() {
        override val typeId: Int = TYPE_TEXTURE
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, typeId); out.p(base, 1, strength); out.p(base, 2, mode.ordinal.toDouble())
            out.p(base, 7, nabla); falloff.pack(out, base + 11)
        }
    }

    /** 资源型流体速度力；资源绑定由独立 sampler/SSBO 完成。 */
    class FluidFlow(
        var resource: CParticleFluidResource,
        var strength: Double = 1.0,
        var useDensity: Boolean = false,
        var flowDrag: Double = 0.0,
        var falloff: CParticleFalloff = CParticleFalloff(),
    ) : CParticleForce() {
        override val typeId: Int = TYPE_FLUID_FLOW
        override fun pack(out: FloatArray, base: Int, origin: Vec3) {
            out.type(base, typeId); out.p(base, 1, strength); out.p(base, 3, if (useDensity) 1.0 else 0.0)
            out.p(base, 7, flowDrag); falloff.pack(out, base + 11)
        }
    }
}

/**
 * Texture 力解释纹理内容的方式。
 *
 * [VECTOR] 把 RGB 从 `[0, 1]` 映射到 `[-1, 1]`，直接作为三维速度增量方向；
 * [GRADIENT] 在 simulation space 的 XZ 平面按 [CParticleForce.Texture.nabla] 采样亮度梯度，
 * 用于让粒子沿纹理明暗变化移动。枚举序号会写入 GPU/CPU 共用的 Command，不能调整成员顺序。
 */
enum class CParticleTextureForceMode {
    VECTOR,
    GRADIENT,
}

/**
 * Magnetic Force 的磁场几何模式。
 *
 * [POINT] 让磁场从 [CParticleForce.Magnetic.center] 径向发散；[LINE] 围绕
 * [CParticleForce.Magnetic.axis] 形成环向磁场；[PLANE] 使用恒定轴向磁场。
 * [wireValue] 属于 GPU/CPU 共用的 Command ABI，不能按枚举顺序重新编号。
 */
enum class CParticleMagneticFieldMode(internal val wireValue: Int) {
    POINT(2),
    LINE(0),
    PLANE(1),
}

/**
 * Blender falloff 使用的空间距离形状。
 *
 * [SPHERE] 使用粒子到中心的三维距离；[TUBE] 使用粒子到 [CParticleFalloff.axis] 轴线的
 * 径向距离；[CONE] 使用粒子方向与该轴的夹角。枚举序号属于 Command packing，不能调整成员顺序。
 */
enum class CParticleFalloffShape {
    SPHERE,
    TUBE,
    CONE,
}

/**
 * Blender falloff 的 Z 轴方向限制。
 *
 * [BOTH] 接受轴两侧粒子；[POSITIVE] 只接受正 Z 侧；[NEGATIVE] 只接受负 Z 侧。
 * 枚举序号属于 Command packing，不能调整成员顺序。
 */
enum class CParticleZDirection {
    BOTH,
    POSITIVE,
    NEGATIVE,
}

/**
 * Blender 风格 falloff，支持最小平台、最大截断、幂次、空间形状和 simulation-space 轴。
 *
 * [axis] 只影响 [CParticleFalloffShape.TUBE]、[CParticleFalloffShape.CONE] 和单侧
 * [zDirection]。轴长度为零时，这些依赖方向的约束返回零贡献，避免 CPU/GPU 产生 NaN。
 */
data class CParticleFalloff(
    val minDistance: Double = 0.0,
    val maxDistance: Double = Double.POSITIVE_INFINITY,
    val power: Double = 2.0,
    val shape: CParticleFalloffShape = CParticleFalloffShape.SPHERE,
    val zDirection: CParticleZDirection = CParticleZDirection.BOTH,
    val axis: Vec3 = Vec3(0.0, 0.0, 1.0),
) {
    init {
        require(minDistance.isFinite() && minDistance >= 0.0)
        require(maxDistance.isFinite() || maxDistance == Double.POSITIVE_INFINITY)
        require(maxDistance >= minDistance)
        require(power.isFinite() && power >= 0.0)
    }

    internal fun pack(out: FloatArray, base: Int, axis: Vec3 = this.axis) {
        val normalizedAxis = axis.safeNormalize()
        out[base - 3] = normalizedAxis.x.toFloat()
        out[base - 2] = normalizedAxis.y.toFloat()
        out[base - 1] = normalizedAxis.z.toFloat()
        out[base] = minDistance.toFloat()
        out[base + 1] = if (maxDistance.isFinite()) maxDistance.toFloat() else -1F
        out[base + 2] = power.toFloat()
        out[base + 3] = shape.ordinal.toFloat()
        out[base + 4] = zDirection.ordinal.toFloat()
    }
}

private fun Vec3.safeNormalize(): Vec3 {
    val length = length()
    return if (length.isFinite() && length > 1e-9) normalize() else Vec3.ZERO
}
