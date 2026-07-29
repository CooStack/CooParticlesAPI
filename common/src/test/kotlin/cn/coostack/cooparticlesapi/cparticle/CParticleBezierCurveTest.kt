package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.BezierFloatKeyframe
import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.BezierKeyframeFloatCurve
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 检查标量、颜色与 GPU 贝塞尔曲线共用的 CPU 约定。
 *
 * 示例：曲线数据进入 renderer 前，对称控制柄必须得到相同的中点值。
 * 禁止：测试不能接受会让 GPU 选根不明确的非单调时间控制柄。
 */
class CParticleBezierCurveTest {
    /**
     * 验证标量曲线会保留三次贝塞尔控制柄并按其求值。
     *
     * 示例：两个相等的值控制柄会得到 `0.75` 的中点值。
     * 禁止：把曲线转换成 8 个线性采样点后不能通过控制柄保留检查。
     */
    @Test
    fun `scalar curve evaluates stored bezier handles`() {
        val curve = CParticleCurve.bezier(
            BezierFloatKeyframe(time = 0.0, value = 0.0, outX = 25.0, outY = 1.0),
            BezierFloatKeyframe(time = 1.0, value = 0.0, inX = -25.0, inY = 1.0),
        )

        assertEquals(CParticleCurveInterpolation.CUBIC_BEZIER, curve.interpolation)
        assertEquals(0f, curve.sample(0f))
        assertEquals(0.75f, curve.sample(0.5f), 1.0E-3f)
        assertEquals(0f, curve.sample(1f))
        assertEquals(listOf(25f, 1f, 0f, 0f), curve.packedHandles.take(4))
    }

    /**
     * 验证 emitter 兼容入口会保留原始贝塞尔控制柄。
     *
     * 示例：转换 [BezierKeyframeFloatCurve] 后仍使用三次插值。
     * 禁止：可直接保留控制柄时，此路径不能静默返回 8 个线性采样点。
     */
    @Test
    fun `float curve conversion keeps bezier handles`() {
        val source = BezierKeyframeFloatCurve(
            listOf(
                BezierFloatKeyframe(0.0, 0.0, outX = 25.0, outY = 1.0),
                BezierFloatKeyframe(1.0, 0.0, inX = -25.0, inY = 1.0),
            )
        )

        val converted = CParticleCurve.fromFloatCurve(source)

        assertEquals(CParticleCurveInterpolation.CUBIC_BEZIER, converted.interpolation)
        assertEquals(0.75f, converted.sample(0.5f), 1.0E-3f)
    }

    /**
     * 验证超出固定 GPU 约定的旧贝塞尔形状仍保留原有采样行为。
     *
     * 示例：包含 9 个关键帧的 emitter 曲线会转换成 8 个 GPU 线性采样点。
     * 禁止：兼容转换不能拒绝 emitter 本身可以采样的曲线。
     */
    @Test
    fun `unsupported emitter bezier curves fall back to linear samples`() {
        val tooManyKeys = BezierKeyframeFloatCurve(
            (0..8).map { index ->
                BezierFloatKeyframe(index / 8.0, index.toDouble())
            }
        )
        val ambiguousTime = BezierKeyframeFloatCurve(
            listOf(
                BezierFloatKeyframe(0.0, 0.0, outX = 80.0),
                BezierFloatKeyframe(1.0, 1.0, inX = -80.0),
            )
        )
        val duplicateTime = BezierKeyframeFloatCurve(
            listOf(
                BezierFloatKeyframe(0.0, 0.0),
                BezierFloatKeyframe(0.5, 0.5),
                BezierFloatKeyframe(0.5, 1.0),
                BezierFloatKeyframe(1.0, 0.0),
            )
        )

        assertEquals(CParticleCurveInterpolation.LINEAR, CParticleCurve.fromFloatCurve(tooManyKeys).interpolation)
        assertEquals(CParticleCurveInterpolation.LINEAR, CParticleCurve.fromFloatCurve(ambiguousTime).interpolation)
        assertEquals(CParticleCurveInterpolation.LINEAR, CParticleCurve.fromFloatCurve(duplicateTime).interpolation)
    }

    /**
     * 验证用于诊断的 packed 数据不能修改已经校验的曲线。
     *
     * 示例：修改返回的控制柄快照后，中点求值保持不变。
     * 禁止：公开数组访问不能绕过 descriptor revision 跟踪。
     */
    @Test
    fun `packed curve arrays are defensive snapshots`() {
        val scalar = CParticleCurve.bezier(
            BezierFloatKeyframe(0.0, 0.0, outX = 25.0, outY = 1.0),
            BezierFloatKeyframe(1.0, 0.0, inX = -25.0, inY = 1.0),
        )
        val color = CParticleColorCurve.bezier(
            CParticleBezierColorKeyframe(0.0, Vector3f(), outX = 25.0, outValueOffset = Vector3f(1f)),
            CParticleBezierColorKeyframe(1.0, Vector3f(), inX = -25.0, inValueOffset = Vector3f(1f)),
        )

        scalar.packed[0] = 1f
        scalar.packedHandles[1] = 0f
        color.packedTimes[0] = 1f
        color.packedColors[0] = 10f
        color.packedOutHandles[1] = 0f
        color.packedInHandles[1] = 0f

        assertEquals(0.75f, scalar.sample(0.5f), 1.0E-3f)
        val sampledColor = color.sample(0.5f, Vector3f())
        assertEquals(0.75f, sampledColor.x, 1.0E-3f)
        assertEquals(0.75f, sampledColor.y, 1.0E-3f)
        assertEquals(0.75f, sampledColor.z, 1.0E-3f)
    }

    /**
     * 验证 RGB 曲线共用一个时间参数，并为每个通道使用独立值控制柄。
     *
     * 示例：绿色通道的中点幅度是红色控制柄幅度的一半。
     * 禁止：RGB 曲线不能把所有通道折叠成一个标量结果。
     */
    @Test
    fun `color curve evaluates bezier handles for every channel`() {
        val curve = CParticleColorCurve.bezier(
            CParticleBezierColorKeyframe(
                time = 0.0,
                value = Vector3f(),
                outX = 25.0,
                outValueOffset = Vector3f(1f, 0.5f, 0.25f),
            ),
            CParticleBezierColorKeyframe(
                time = 1.0,
                value = Vector3f(),
                inX = -25.0,
                inValueOffset = Vector3f(1f, 0.5f, 0.25f),
            ),
        )

        val sampled = curve.sample(0.5f, Vector3f())

        assertEquals(CParticleCurveInterpolation.CUBIC_BEZIER, curve.interpolation)
        assertEquals(0.75f, sampled.x, 1.0E-3f)
        assertEquals(0.375f, sampled.y, 1.0E-3f)
        assertEquals(0.1875f, sampled.z, 1.0E-3f)
    }

    /**
     * 验证 GPU 曲线会拒绝可把同一时间映射到多个贝塞尔参数的控制柄。
     *
     * 示例：位于曲线段内部的普通控制柄仍然有效。
     * 禁止：交叉的 X 控制柄不能进入固定迭代 shader 求解器。
     */
    @Test
    fun `gpu bezier curves require monotonic time handles`() {
        assertFailsWith<IllegalArgumentException> {
            CParticleCurve.bezier(
                BezierFloatKeyframe(time = 0.0, value = 0.0, outX = 80.0),
                BezierFloatKeyframe(time = 1.0, value = 1.0, inX = -80.0),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CParticleColorCurve.bezier(
                CParticleBezierColorKeyframe(time = 0.0, value = Vector3f(), outX = 80.0),
                CParticleBezierColorKeyframe(time = 1.0, value = Vector3f(1f), inX = -80.0),
            )
        }
    }

    /**
     * 验证每个关键帧都能在对应的归一化时间精确返回。
     *
     * 示例：位于 `0.5` 的中间关键帧直接返回 `1`，不会受二分误差影响。
     * 禁止：固定迭代求解不能扰动锚点值。
     */
    @Test
    fun `bezier sampling returns exact intermediate anchors`() {
        val curve = CParticleCurve.bezier(
            BezierFloatKeyframe(0.0, 0.0, outX = 15.0, outY = 0.4),
            BezierFloatKeyframe(0.5, 1.0, inX = -15.0, inY = -0.2, outX = 15.0, outY = -0.2),
            BezierFloatKeyframe(1.0, 0.0, inX = -15.0, inY = 0.4),
        )

        assertEquals(1f, curve.sample(0.5f))
    }

    /**
     * 验证有限的 Double 输入在打包到 GPU 时不能溢出。
     *
     * 示例：能装入 Float 的普通输入仍会被接受。
     * 禁止：`Double.MAX_VALUE` 不能在 descriptor 或 uniform 中变成无穷值。
     */
    @Test
    fun `bezier factories reject values that overflow gpu floats`() {
        assertFailsWith<IllegalArgumentException> {
            CParticleCurve.bezier(BezierFloatKeyframe(0.0, Double.MAX_VALUE))
        }
        assertFailsWith<IllegalArgumentException> {
            CParticleColorCurve.bezier(
                CParticleBezierColorKeyframe(0.0, Vector3f(), outX = Double.MAX_VALUE)
            )
        }
    }
}
