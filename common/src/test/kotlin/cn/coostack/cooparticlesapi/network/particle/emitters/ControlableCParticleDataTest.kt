package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleColorCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureSource
import cn.coostack.cooparticlesapi.cparticle.CParticleUv
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.textureOf
import cn.coostack.cooparticlesapi.cparticle.textureOfAtlas
import cn.coostack.cooparticlesapi.cparticle.textureOfBlock
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestCParticleEmitter
import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.SharedConstants
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 检查 emitter 中 CParticle 专用 data 的逐实例蒙版与序列化契约。
 *
 * Example: 同一次 `genParticles()` 可以返回叠加方块图集和独立纹理蒙版的两份 data。
 * Forbidden: 不能把蒙版来源提升到 emitter 级别后再由所有 data 共用。
 */
class ControlableCParticleDataTest {
    /**
     * 初始化原版注册表，供测试中的默认 effect 和方块状态使用。
     *
     * Example: 每个测试都可以构造默认的 `ControlableParticleData`。
     * Forbidden: 不要在注册表初始化前访问依赖内置注册项的数据。
     */
    @BeforeTest
    fun bootstrapRegistries() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    /**
     * 验证不同 data 转换成 CParticle 后保留基础 effect 和各自的蒙版来源。
     *
     * Example: `sign = 1` 和 `sign = 2` 的 data 可以进入不同蒙版 binding。
     * Forbidden: 蒙版不得替换每份 data 自己的基础 effect。
     */
    @Test
    fun `each emitted data keeps its base effect and mask source`() {
        val atlasSource = textureOfAtlas(
            TextureAtlas.LOCATION_BLOCKS,
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/stone"),
        )
        val customSource = textureOf(
            ResourceLocation.fromNamespaceAndPath("test", "textures/particle/custom.png"),
            CParticleUv(0.1f, 0.2f, 0.8f, 0.9f),
        )
        val first = ControlableCParticleData().apply {
            sign = 1
            textureSource = atlasSource
        }
        val second = ControlableCParticleData().apply {
            sign = 2
            textureSource = customSource
        }

        val firstParticle = CParticle.from(first)
        val secondParticle = CParticle.from(second)
        assertSame(atlasSource, firstParticle.textureSource)
        assertSame(customSource, secondParticle.textureSource)
        assertSame(
            first.effect,
            assertIs<CParticleTextureSource.ParticleEffect>(firstParticle.effectiveTextureSource()).effect,
        )
        assertSame(
            second.effect,
            assertIs<CParticleTextureSource.ParticleEffect>(secondParticle.effectiveTextureSource()).effect,
        )
        val legacyData = ControlableParticleData()
        assertSame(
            legacyData.effect,
            assertIs<CParticleTextureSource.ParticleEffect>(
                CParticle.from(legacyData).effectiveTextureSource()
            ).effect,
        )
    }

    /**
     * 验证模板 clone 保留子类和纹理，同时继续生成新的粒子 UUID。
     *
     * Example: `template.clone()` 可直接放回 `genParticles()` 的结果。
     * Forbidden: clone 不能退化为 `ControlableParticleData` 或丢失纹理来源。
     */
    @Test
    fun `clone preserves cparticle data subtype and source`() {
        val source = textureOfBlock(Blocks.STONE.defaultBlockState())
        val original = ControlableCParticleData().apply {
            sign = 7
            velocity = Vec3(1.0, 2.0, 3.0)
            textureSource = source
            updateMode = CParticleUpdateMode.STATIC
            alphaCurve = CParticleCurve.linear(0.2f, 0.8f)
            scaleCurve = CParticleCurve.fadeInOut()
            scaleXCurve = CParticleCurve.linear(0.25f, 1f)
            scaleYCurve = CParticleCurve.linear(1f, 0.5f)
            colorCurve = CParticleColorCurve.linear(Vector3f(1f, 0f, 0f), Vector3f(0f, 0f, 1f))
            rotationDirection = Vector3f(1f, 2f, 3f)
            angularVelocity = Vector3f(0.1f, 0.2f, 0.3f)
            randomAgePreTick = true
            randomSeed = 42
            blockCollision = true
        }

        val cloned = original.clone()

        assertIs<ControlableCParticleData>(cloned)
        assertNotEquals(original.uuid, cloned.uuid)
        assertEquals(7, cloned.sign)
        assertEquals(Vec3(1.0, 2.0, 3.0), cloned.velocity)
        assertSame(source, cloned.textureSource)
        assertEquals(CParticleUpdateMode.STATIC, cloned.updateMode)
        assertSame(original.alphaCurve, cloned.alphaCurve)
        assertSame(original.scaleCurve, cloned.scaleCurve)
        assertSame(original.scaleXCurve, cloned.scaleXCurve)
        assertSame(original.scaleYCurve, cloned.scaleYCurve)
        assertSame(original.colorCurve, cloned.colorCurve)
        assertEquals(Vector3f(1f, 2f, 3f), cloned.rotationDirection)
        assertEquals(Vector3f(0.1f, 0.2f, 0.3f), cloned.angularVelocity)
        assertTrue(cloned.randomAgePreTick)
        assertEquals(42, cloned.randomSeed)
        assertTrue(cloned.blockCollision)
        assertFalse(original.rotationDirection === cloned.rotationDirection)
        assertFalse(original.angularVelocity === cloned.angularVelocity)

        val particle = CParticle.from(original)
        assertEquals(CParticleUpdateMode.STATIC, particle.updateMode)
        assertSame(original.alphaCurve, particle.alphaCurve)
        assertSame(original.scaleCurve, particle.scaleCurve)
        assertSame(original.scaleXCurve, particle.scaleXCurve)
        assertSame(original.scaleYCurve, particle.scaleYCurve)
        assertSame(original.colorCurve, particle.colorCurve)
        assertEquals(original.rotationDirection, particle.rotationDirection)
        assertEquals(original.angularVelocity, particle.angularVelocity)
        assertTrue(particle.randomAgePreTick)
        assertEquals(42, particle.randomSeed)
        assertTrue(particle.blockCollision)

        val clonedParticle = particle.clone()
        assertSame(particle.scaleCurve, clonedParticle.scaleCurve)
        assertSame(particle.scaleXCurve, clonedParticle.scaleXCurve)
        assertSame(particle.scaleYCurve, clonedParticle.scaleYCurve)
    }

    /**
     * 验证统一 stream codec 为所有公共纹理来源提供编码和解码分支。
     *
     * Example: `ControlableCParticleData.PACKET_CODEC` 可以复用这个来源 codec。
     * Forbidden: 新增来源时不能只修改编码端或解码端。
     */
    @Test
    fun `texture source codec covers every public source`() {
        val source = Files.readString(
            findRepoRoot().resolve(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleTextureSource.kt"
            )
        )

        listOf("ParticleEffect", "AtlasSprite", "AtlasAnimation", "Block", "Item", "Custom").forEach { type ->
            assertTrue("is $type ->" in source, "Missing encoder branch for $type")
            assertTrue("$type(" in source.substringAfter("private fun decodeSource"), "Missing decoder branch for $type")
        }
        assertTrue("ParticleTypes.STREAM_CODEC" in source)
        assertTrue("ItemStack.OPTIONAL_STREAM_CODEC" in source)
        assertTrue("Block.BLOCK_STATE_REGISTRY" in source)
    }

    /**
     * 验证 atlas 动画在构造阶段拒绝不受控的大帧列表。
     *
     * Example: `MAX_ATLAS_ANIMATION_FRAMES` 帧仍属于合法协议值。
     * Forbidden: 解码端不能按 descriptor 的 24 位上限直接分配列表。
     */
    @Test
    fun `atlas animation enforces its network frame limit`() {
        val frame = ResourceLocation.fromNamespaceAndPath("test", "frame")

        assertFailsWith<IllegalArgumentException> {
            CParticleTextureSource.AtlasAnimation(
                TextureAtlas.LOCATION_BLOCKS,
                List(CParticleTextureSource.MAX_ATLAS_ANIMATION_FRAMES + 1) { frame },
            )
        }
    }

    /**
     * 验证 `@CodecField` 反射到专用 data 时能找到准确的 codec。
     *
     * Example: [TestCParticleEmitter.template] 可以由自动 emitter codec 处理。
     * Forbidden: 不能只注册父类 codec，否则解码会丢失 `textureSource`。
     */
    @Test
    fun `codec helper supports cparticle data codec fields`() {
        val templateField = TestCParticleEmitter::class.java.getDeclaredField("template")
        val dataSource = Files.readString(
            findRepoRoot().resolve(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ControlableCParticleData.kt"
            )
        )

        assertEquals(ControlableCParticleData::class.java, templateField.type)
        assertSame(
            ControlableCParticleData.PACKET_CODEC,
            CodecHelper.registryCodecOf(templateField.genericType),
        )
        assertSame(
            CParticleTextureSource.STREAM_CODEC,
            CodecHelper.registryCodecOf(CParticleTextureSource::class.java),
        )
        assertFailsWith<IllegalArgumentException> {
            CodecHelper.codecOf(ControlableCParticleData::class.java)
        }
        assertFailsWith<IllegalArgumentException> {
            CodecHelper.codecOf(CParticleTextureSource::class.java)
        }
        assertSame(CParticleCurve.STREAM_CODEC, CodecHelper.codecOf(CParticleCurve::class.java))
        assertSame(CParticleColorCurve.STREAM_CODEC, CodecHelper.codecOf(CParticleColorCurve::class.java))
        CodecHelper.codecOf(CParticleUpdateMode::class.java)
        assertTrue("encodeBase(buf, data)" in dataSource)
        assertTrue("decodeBase(buf, ControlableCParticleData())" in dataSource)
        assertTrue("CParticleTextureSource.STREAM_CODEC.encode(buf, source)" in dataSource)
        assertTrue("data.textureSource = CParticleTextureSource.STREAM_CODEC.decode(buf)" in dataSource)
    }

    /**
     * 验证默认 GPU 选择属于每份 data，而不是整个 emitter。
     *
     * Example: 同一 `genParticles()` 可混合普通 data 与 CParticle data。
     * Forbidden: 普通 data 不能因为同批次里出现 CParticle data 就跳过 `singleParticleAction`。
     */
    @Test
    fun `cparticle gpu selection is data scoped`() {
        val emitterSource = Files.readString(
            findRepoRoot().resolve(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ClassParticleEmitters.kt"
            )
        )

        assertTrue("if (data is ControlableCParticleData &&" in emitterSource)
        assertFalse("useCParticleSystem" in emitterSource)
        assertFalse("shouldUseCParticleSystem" in emitterSource)
        assertFalse("configureCParticleSystem" in emitterSource)
    }

    /**
     * 找到包含 `settings.gradle` 的仓库根目录。
     *
     * Example: Gradle 从 `common` 子目录启动时仍能读取主源码。
     * Forbidden: 找不到仓库时不能默默使用当前目录。
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
