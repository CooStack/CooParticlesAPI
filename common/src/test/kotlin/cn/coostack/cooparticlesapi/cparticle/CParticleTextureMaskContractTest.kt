package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.SharedConstants
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 检查 CParticle 基础纹理和额外蒙版纹理的分工。
 *
 * Example: end rod 粒子可以叠加石头纹理，而不会丢失 end rod 的轮廓。
 * Forbidden: 方块或物品来源不能替换 [CParticle.effect] 对应的基础纹理。
 */
class CParticleTextureMaskContractTest {
    /**
     * 初始化测试使用的原版注册表。
     *
     * Example: 测试可直接使用 [ParticleTypes.END_ROD] 和 [Blocks.STONE]。
     * Forbidden: 不要在单个测试中重复执行完整 bootstrap。
     */
    @BeforeTest
    fun bootstrapRegistries() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    /**
     * 方块来源只提供额外蒙版，基础粒子来源保持不变。
     *
     * Example: end rod 加石头蒙版后，基础来源仍是 end rod 的 SpriteSet。
     * Forbidden: [CParticle.textureSource] 不能抢占 [CParticle.effect] 的优先级。
     */
    @Test
    fun `extra texture keeps the original particle texture`() {
        val particle = CParticle().apply {
            effect = ParticleTypes.END_ROD
            textureSource = textureOfBlock(Blocks.STONE.defaultBlockState())
        }

        assertEquals(
            CParticleTextureSource.ParticleEffect(ParticleTypes.END_ROD),
            particle.effectiveTextureSource(),
        )
    }

    /**
     * 渲染器必须为基础纹理和蒙版纹理保留独立采样路径。
     *
     * Example: shader 用蒙版的颜色和 alpha 乘到基础粒子采样上。
     * Forbidden: 不能只把方块图集绑定给 `uMainTexture`。
     */
    @Test
    fun `renderer samples the extra texture as a mask`() {
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt",
        )
        val vertex = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/cparticle.vsh",
        )
        val fragment = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/fragment/cparticle.fsh",
        )

        assertTrue("uMaskTexture" in renderer)
        assertTrue("uHasMask" in renderer)
        assertTrue("vMaskUv" in vertex)
        assertTrue("uniform int uHasMask" in vertex)
        assertTrue("if (uHasMask != 0)" in vertex)
        assertTrue("uint packedColor = uint(packedValue);" in vertex)
        assertTrue("uint packed =" !in vertex)
        assertTrue("packedValue + 0.5" !in vertex)
        assertTrue("uniform sampler2D uMaskTexture" in fragment)
        assertTrue("mask.rgb *= vMaskTint" in fragment)
        assertTrue("tex.rgb *= mix(vec3(1.0), mask.rgb, mask.a)" in fragment)
        assertTrue("tex.a *= mask.a" in fragment)
    }

    /**
     * GPU 实例为蒙版保留独立 descriptor 和裁剪位。
     *
     * Example: 基础 descriptor `5` 和蒙版 descriptor `9` 会写入不同字段。
     * Forbidden: 蒙版 descriptor 不能覆盖 [CParticleStore.OFF_ANIMATION]。
     */
    @Test
    fun `store keeps separate base and mask descriptors`() {
        val store = CParticleStore(1)
        val slot = store.spawnWithMask(
            p = CParticle(),
            origin = Vec3.ZERO,
            animationId = 5,
            blockLight = 15,
            skyLight = 15,
            maskAnimationId = 9,
            randomMaskQuarterUv = true,
            maskTextureBindingKey = CParticleTextureBindingKey.BLOCK_ATLAS,
            maskColorMultiplier = Vector3f(1f, 0.5f, 0f),
        )
        val base = slot * CParticleStore.STRIDE

        assertEquals(5f, store.data[base + CParticleStore.OFF_ANIMATION])
        assertEquals(9f, store.data[base + CParticleStore.OFF_MASK_ANIMATION])
        assertEquals(0x0080FF, store.data[base + CParticleStore.OFF_MASK_COLOR].toInt())
        assertTrue(
            store.data[base + CParticleStore.OFF_FLAGS].toInt() and
                    CParticleInstanceFlags.MASK_RANDOM_QUARTER_UV != 0,
        )
    }

    /**
     * DYNAMIC 粒子可以在原有两个 binding 内更新基础和蒙版 descriptor。
     *
     * Example: 资源重载后两个 descriptor 会在一次补写中同时刷新。
     * Forbidden: 蒙版更新不能写进基础 descriptor 字段。
     */
    @Test
    fun `dynamic refresh updates base and mask descriptors independently`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply {
            effect = ParticleTypes.END_ROD
            textureSource = textureOfBlock(Blocks.STONE.defaultBlockState())
        }
        val slot = store.spawnWithMask(
            p = particle,
            origin = Vec3.ZERO,
            animationId = 5,
            blockLight = 15,
            skyLight = 15,
            textureBindingKey = CParticleTextureBindingKey.PARTICLE_ATLAS,
            textureGeneration = 1,
            maskAnimationId = 9,
            maskTextureBindingKey = CParticleTextureBindingKey.BLOCK_ATLAS,
        )
        particle.textureSource = textureOfBlock(Blocks.DIRT.defaultBlockState())

        val dirtyCount = store.prepareDynamicTextures(
            tick = 0,
            textureGeneration = 1,
            expectedBindingKey = CParticleTextureBindingKey.PARTICLE_ATLAS,
            expectedMaskBindingKey = CParticleTextureBindingKey.BLOCK_ATLAS,
            resolveTextures = {
                CParticleResolvedTextures(
                    base = resolved(CParticleTextureBindingKey.PARTICLE_ATLAS, 7),
                    mask = resolved(
                        CParticleTextureBindingKey.BLOCK_ATLAS,
                        11,
                        Vector3f(0f, 1f, 0f),
                    ),
                    randomBaseQuarterUv = false,
                    randomMaskQuarterUv = true,
                )
            },
            onBindingMismatch = { _, _, _ -> error("bindings must stay unchanged") },
        )
        val base = slot * CParticleStore.STRIDE

        assertEquals(1, dirtyCount)
        assertEquals(7f, store.data[base + CParticleStore.OFF_ANIMATION])
        assertEquals(11f, store.data[base + CParticleStore.OFF_MASK_ANIMATION])
        assertEquals(0x00FF00, store.data[base + CParticleStore.OFF_MASK_COLOR].toInt())
    }

    /**
     * 从仓库根目录读取实现文件。
     *
     * Example: Gradle 从根项目或 `common` 子项目启动时都能定位 shader。
     * Forbidden: 不要依赖开发机的绝对路径。
     *
     * @param relativePath 相对仓库根目录的路径
     * @return UTF-8 文件内容
     */
    private fun readProjectFile(relativePath: String): String {
        val direct = Path.of(relativePath)
        val parent = Path.of("..").resolve(relativePath).normalize()
        return Files.readString(if (Files.exists(direct)) direct else parent)
    }

    /**
     * 构造不依赖客户端纹理管理器的解析结果。
     *
     * Example: store 单元测试用 descriptor `7` 模拟粒子图集帧。
     * Forbidden: 该 helper 不验证真实 atlas 中是否存在 sprite。
     *
     * @param binding descriptor 所属纹理 binding
     * @param descriptorId 测试使用的 descriptor ID
     * @param colorMultiplier 该来源的 RGB 倍率
     * @return 单帧解析结果
     */
    private fun resolved(
        binding: CParticleTextureBindingKey,
        descriptorId: Int,
        colorMultiplier: Vector3f = Vector3f(1f),
    ) =
        CParticleResolvedTexture(
            bindingKey = binding,
            descriptorId = descriptorId,
            uv = CParticleUv.FULL,
            animationId = null,
            colorMultiplier = colorMultiplier,
        )
}
