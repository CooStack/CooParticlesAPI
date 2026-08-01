package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.BezierFloatKeyframe
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestCParticleEmitter
import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CParticleAppearanceDescriptorTest {
    /**
     * 初始化默认 effect 所依赖的原版注册表。
     *
     * 示例：测试可以通过 [CParticle.from] 转换 emitter 模板。
     * 禁止：不要在 bootstrap 前访问默认 end rod 的粒子类型。
     */
    @BeforeTest
    fun bootstrapRegistries() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    /**
     * 检查压力测试 emitter 的首个可见 tick 仍处于淡入阶段。
     *
     * 示例：`age=1` 时 alpha 倍率应小于峰值，随后继续上升。
     * 禁止：`fadeIn=0` 只会让精确的出生帧透明，不能算生命周期淡入。
     */
    @Test
    fun `test emitter keeps a visible fade in after its spawn frame`() {
        val emitter = TestCParticleEmitter(Vec3.ZERO, null)
        val curve = requireNotNull(emitter.template.alphaCurve)
        val firstVisibleTick = curve.sample(1f / emitter.particleMaxAge)
        val laterTick = curve.sample(10f / emitter.particleMaxAge)
        val particle = CParticle.from(emitter.template.clone())
        val descriptorId = particle.appearanceDescriptorId()
        val descriptorCurve = requireNotNull(CParticleAppearanceDescriptors.definition(descriptorId).alpha)
        val descriptorFirstTick = CParticleCurve.of(
            *Array(descriptorCurve.keyCount) { index ->
                descriptorCurve.times[index] to descriptorCurve.values[index]
            },
        ).sample(1f / emitter.particleMaxAge)
        val store = CParticleStore(1)
        val slot = store.spawn(particle, Vec3.ZERO, 0, 15, 15)
        val base = slot * CParticleStore.STRIDE

        assertTrue(firstVisibleTick in 0f..<0.2f)
        assertTrue(laterTick > firstVisibleTick)
        assertEquals(firstVisibleTick, descriptorFirstTick)
        assertEquals(descriptorId.toFloat(), store.data[base + CParticleStore.OFF_APPEARANCE])
        assertEquals(emitter.template.alpha, store.data[base + CParticleStore.OFF_COLOR + 3])
    }

    @Test
    fun `equal lifetime curves share one descriptor`() {
        val first = CParticleAppearanceDescriptors.register(
            CParticleCurve.linear(0f, 1f),
            CParticleCurve.linear(1f, 2f),
            CParticleCurve.linear(0.5f, 1f),
            CParticleCurve.linear(1f, 0.5f),
            CParticleColorCurve.linear(Vector3f(1f, 0f, 0f), Vector3f(0f, 0f, 1f)),
        )
        val second = CParticleAppearanceDescriptors.register(
            CParticleCurve.linear(0f, 1f),
            CParticleCurve.linear(1f, 2f),
            CParticleCurve.linear(0.5f, 1f),
            CParticleCurve.linear(1f, 0.5f),
            CParticleColorCurve.linear(Vector3f(1f, 0f, 0f), Vector3f(0f, 0f, 1f)),
        )
        val different = CParticleAppearanceDescriptors.register(
            CParticleCurve.linear(1f, 0f),
            null,
            null,
            null,
            null,
        )
        val differentAxis = CParticleAppearanceDescriptors.register(
            CParticleCurve.linear(0f, 1f),
            CParticleCurve.linear(1f, 2f),
            CParticleCurve.linear(0.25f, 1f),
            CParticleCurve.linear(1f, 0.5f),
            CParticleColorCurve.linear(Vector3f(1f, 0f, 0f), Vector3f(0f, 0f, 1f)),
        )

        assertEquals(first, second)
        assertNotEquals(first, different)
        assertNotEquals(first, differentAxis)
        assertEquals(0, CParticleAppearanceDescriptors.IDENTITY_DESCRIPTOR_ID)
    }

    /**
     * 验证外观 descriptor 会包含插值类型和全部贝塞尔控制柄。
     *
     * 示例：锚点相同但控制柄不同的曲线会得到不同的 descriptor ID。
     * 禁止：恢复 STATIC 粒子时不能把曲线降级成线性插值。
     */
    @Test
    fun `descriptors distinguish and restore scalar and color bezier handles`() {
        val scalar = CParticleCurve.bezier(
            BezierFloatKeyframe(0.0, 0.0, outX = 25.0, outY = 1.0),
            BezierFloatKeyframe(1.0, 0.0, inX = -25.0, inY = 1.0),
        )
        val changedScalar = CParticleCurve.bezier(
            BezierFloatKeyframe(0.0, 0.0, outX = 25.0, outY = 0.5),
            BezierFloatKeyframe(1.0, 0.0, inX = -25.0, inY = 0.5),
        )
        val color = CParticleColorCurve.bezier(
            CParticleBezierColorKeyframe(
                0.0,
                Vector3f(1f, 0f, 0f),
                outX = 25.0,
                outValueOffset = Vector3f(0f, 1f, 0f),
            ),
            CParticleBezierColorKeyframe(
                1.0,
                Vector3f(0f, 0f, 1f),
                inX = -25.0,
                inValueOffset = Vector3f(0f, 1f, 0f),
            ),
        )
        val first = CParticleAppearanceDescriptors.register(scalar, scalar, scalar, scalar, color)
        val different = CParticleAppearanceDescriptors.register(changedScalar, scalar, scalar, scalar, color)
        val restored = CParticle()

        CParticleAppearanceDescriptors.applyTo(restored, first)

        assertNotEquals(first, different)
        assertEquals(CParticleCurveInterpolation.CUBIC_BEZIER, restored.alphaCurve?.interpolation)
        assertEquals(scalar.packedHandles.toList(), restored.alphaCurve?.packedHandles?.toList())
        assertEquals(CParticleCurveInterpolation.CUBIC_BEZIER, restored.colorCurve?.interpolation)
        assertEquals(color.packedOutHandles.toList(), restored.colorCurve?.packedOutHandles?.toList())
        assertEquals(color.packedInHandles.toList(), restored.colorCurve?.packedInHandles?.toList())
    }

    @Test
    fun `static slots retain only an appearance descriptor id`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply {
            updateMode = CParticleUpdateMode.STATIC
            alphaCurve = CParticleCurve.fadeInOut()
            scaleCurve = CParticleCurve.linear(0.25f, 1f)
            scaleXCurve = CParticleCurve.linear(0.5f, 1f)
            scaleYCurve = CParticleCurve.linear(1f, 0.5f)
        }

        val slot = store.spawn(particle, Vec3.ZERO, 0, 15, 15)
        val base = slot * CParticleStore.STRIDE

        assertEquals(
            particle.appearanceDescriptorId().toFloat(),
            store.data[base + CParticleStore.OFF_APPEARANCE],
        )
        assertNull(store.dynamicSource(slot))
    }

    /**
     * 验证共享描述符能区分并恢复等比、X 和 Y 三条缩放曲线。
     *
     * 示例：CPU fallback 可以从 STATIC 槽位恢复非等比生命周期尺寸。
     * 禁止：X/Y 曲线不能交换或折叠成一条等比曲线。
     */
    @Test
    fun `descriptor restores uniform and per-axis scale curves`() {
        val source = CParticle().apply {
            scaleCurve = CParticleCurve.linear(0.5f, 1f)
            scaleXCurve = CParticleCurve.linear(0.25f, 2f)
            scaleYCurve = CParticleCurve.linear(1.5f, 0.75f)
        }
        val restored = CParticle()

        CParticleAppearanceDescriptors.applyTo(restored, source.appearanceDescriptorId())

        assertEquals(source.scaleCurve?.packed?.toList(), restored.scaleCurve?.packed?.toList())
        assertEquals(source.scaleXCurve?.packed?.toList(), restored.scaleXCurve?.packed?.toList())
        assertEquals(source.scaleYCurve?.packed?.toList(), restored.scaleYCurve?.packed?.toList())
    }

    /**
     * 验证顶点着色器使用相同的描述符跨度，并把等比倍率与 X/Y 倍率相乘。
     *
     * 示例：只设置 X 曲线时，shader 的 Y 倍率仍通过缺省值保持为 `1`。
     * 禁止：shader 不能把 X/Y 两条曲线重新折叠成一个 float 倍率。
     */
    @Test
    fun `vertex shader composes uniform and per-axis scale curves`() {
        val shader = Files.readString(
            findRepoRoot().resolve(
                "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/cparticle.vsh"
            )
        )

        assertTrue("const int APPEARANCE_TEXELS = ${CParticleAppearanceDescriptors.TEXELS_PER_DESCRIPTOR};" in shader)
        assertTrue("float sampleBezierParameter" in shader)
        assertTrue("sampleParticleColorCurve" in shader)
        assertTrue("uColorCurveOutHandles" in shader)
        assertTrue("sampleParticleScalarCurve(t, encoded, 1, 25, 0, 1)" in shader)
        assertTrue("sampleParticleScalarCurve(t, encoded, 1, 33, 2, 3)" in shader)
        assertTrue("sampleParticleScalarCurve(t, encoded, 9, 41, 0, 1)" in shader)
        assertTrue("sampleParticleScalarCurve(t, packedMetadata % PACKED_CURVE_RADIX, 9, 49, 2, 3)" in shader)
        assertTrue("base + 57 + i - 1" in shader)
        assertTrue("base + 65 + i" in shader)
        assertTrue("float uniformScale = sampleParticleScaleCurve(lifeT) * sampleScaleCurve(curveT);" in shader)
        assertTrue("sampleParticleScaleXCurve(lifeT)" in shader)
        assertTrue("sampleParticleScaleYCurve(lifeT)" in shader)
        assertTrue("vec2 size = iSizeRot.xy * scale;" in shader)
        assertTrue("baseAlpha * alphaScale" in shader)
    }

    /**
     * 验证 DYNAMIC 粒子替换轴向缩放曲线时只更新外观描述符字段。
     *
     * 示例：保留 CParticle 引用的调用方可以在下一帧替换 X 轴缩放曲线。
     * 禁止：曲线替换不能触发纹理重新解析。
     */
    @Test
    fun `dynamic axis scale replacement updates only the descriptor field`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply {
            scaleXCurve = CParticleCurve.linear(1f, 0f)
        }
        val slot = store.spawn(particle, Vec3.ZERO, 0, 15, 15)
        val base = slot * CParticleStore.STRIDE
        val before = store.data[base + CParticleStore.OFF_APPEARANCE]

        particle.scaleXCurve = CParticleCurve.linear(0f, 1f)
        val dirtyCount = store.prepareDynamicVisuals(1) { _, _ ->
            error("age or appearance changes must not resolve texture UV")
        }

        assertEquals(1, dirtyCount)
        assertNotEquals(before, store.data[base + CParticleStore.OFF_APPEARANCE])
    }

    @Test
    fun `appearance descriptor offsets stay exactly representable`() {
        assertEquals(
            CParticleInstanceFlags.FLOAT_EXACT_INTEGER_LIMIT / CParticleAppearanceDescriptors.TEXELS_PER_DESCRIPTOR,
            CParticleAppearanceDescriptors.MAX_DESCRIPTOR_COUNT,
        )
        CParticleAppearanceDescriptors.requireValidDescriptorId(
            CParticleAppearanceDescriptors.MAX_DESCRIPTOR_ID,
        )
        val lastTexel = CParticleAppearanceDescriptors.MAX_DESCRIPTOR_ID *
                CParticleAppearanceDescriptors.TEXELS_PER_DESCRIPTOR +
                CParticleAppearanceDescriptors.TEXELS_PER_DESCRIPTOR - 1
        assertEquals(lastTexel, lastTexel.toFloat().toInt())
    }

    /**
     * 找到包含 `settings.gradle` 的仓库根目录。
     *
     * 示例：Gradle 从根项目或子项目启动时都能读取 shader 源码。
     * 禁止：找不到仓库时不能静默使用错误目录。
     *
     * @return 当前测试所属仓库的绝对路径
     */
    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
