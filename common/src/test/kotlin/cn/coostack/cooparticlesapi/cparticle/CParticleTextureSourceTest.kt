package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.particles.impl.ControlableFallingDustEffect
import java.util.UUID
import net.minecraft.SharedConstants
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class CParticleTextureSourceTest {
    @BeforeTest
    fun bootstrapRegistries() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    @Test
    fun `public factories preserve source options`() {
        val atlas = TextureAtlas.LOCATION_BLOCKS
        val sprite = ResourceLocation.fromNamespaceAndPath("test", "sprite")
        val texture = ResourceLocation.fromNamespaceAndPath("test", "textures/particle/custom.png")
        val uv = CParticleUv(0.1f, 0.2f, 0.7f, 0.8f)

        assertEquals(
            CParticleTextureSource.Block(
                Blocks.STONE.defaultBlockState(),
                randomCrop = false,
                applyTint = false,
                applyBrightness = false,
            ),
            textureOfBlock(
                Blocks.STONE.defaultBlockState(),
                randomCrop = false,
                applyTint = false,
                applyBrightness = false,
            ),
        )
        assertEquals(CParticleTextureSource.AtlasSprite(atlas, sprite), textureOfAtlas(atlas, sprite))
        assertEquals(CParticleTextureSource.Custom(texture, uv), textureOf(texture, uv))
        assertEquals(CParticleUv.FULL, assertIs<CParticleTextureSource.Custom>(textureOf(texture)).uv)
        assertEquals(ParticleTypes.END_ROD, assertIs<CParticleTextureSource.ParticleEffect>(
            textureOfEffect(ParticleTypes.END_ROD)
        ).effect)
    }

    @Test
    fun `item source snapshots the caller stack and tint options`() {
        val stack = ItemStack(Items.DIAMOND, 2)
        val source = assertIs<CParticleTextureSource.Item>(
            textureOfItem(stack, modelSeed = 17, applyTint = false, tintIndex = 3)
        )

        stack.count = 31
        val firstCopy = source.stackCopy()
        val secondCopy = source.stackCopy()

        assertEquals(2, firstCopy.count)
        assertEquals(17, source.modelSeed)
        assertEquals(false, source.applyTint)
        assertEquals(3, source.tintIndex)
        assertNotSame(firstCopy, secondCopy)
    }

    /**
     * 检查 Item descriptor 在资源重载换图集后会生成新的键。
     *
     * Example: 旧粒子留在旧 atlas 并使用该 atlas 的 missing UV，新粒子可进入新 atlas。
     * Forbidden: 相同 stack 和 seed 不能掩盖 binding 的变化。
     */
    @Test
    fun `item descriptor key includes its resolved atlas binding`() {
        val stack = ItemStack(Items.DIAMOND)
        val oldBinding = CParticleTextureBindingKey.BLOCK_ATLAS
        val newBinding = CParticleTextureBindingKey.PARTICLE_ATLAS

        assertNotEquals(
            CParticleTextureResolver.ItemDescriptorKey(stack, 7, oldBinding),
            CParticleTextureResolver.ItemDescriptorKey(stack, 7, newBinding),
        )
    }

    @Test
    fun `atlas animation copies the mutable frame list`() {
        val atlas = TextureAtlas.LOCATION_BLOCKS
        val first = ResourceLocation.fromNamespaceAndPath("test", "first")
        val second = ResourceLocation.fromNamespaceAndPath("test", "second")
        val frames = mutableListOf(first, second)
        val source = assertIs<CParticleTextureSource.AtlasAnimation>(
            textureAnimationOfAtlas(atlas, frames)
        )

        frames.clear()

        assertEquals(listOf(first, second), source.spriteLocations)
    }

    @Test
    fun `extra source keeps legacy sprite ahead of effect`() {
        val defaultSource = assertIs<CParticleTextureSource.ParticleEffect>(
            CParticle().effectiveTextureSource()
        )
        assertEquals(ParticleTypes.END_ROD, defaultSource.effect)
        assertEquals(false, defaultSource.animateByAge)
        assertEquals(defaultSource, textureOfParticleSprite(CParticleSprites.DEFAULT))

        val legacyDefault = CParticle().apply { sprite = CParticleSprites.DEFAULT }
        assertEquals(defaultSource, legacyDefault.effectiveTextureSource())

        val fixedSprite = ResourceLocation.fromNamespaceAndPath("test", "fixed")
        val customTexture = ResourceLocation.fromNamespaceAndPath("test", "textures/custom.png")
        val particle = CParticle().apply {
            effect = ParticleTypes.END_ROD
            sprite = fixedSprite
        }

        assertEquals(
            CParticleTextureSource.AtlasSprite(TextureAtlas.LOCATION_PARTICLES, fixedSprite),
            particle.effectiveTextureSource(),
        )

        val explicit = textureOf(customTexture)
        particle.textureSource = explicit
        assertEquals(explicit, particle.textureSource)
        assertEquals(
            CParticleTextureSource.AtlasSprite(TextureAtlas.LOCATION_PARTICLES, fixedSprite),
            particle.effectiveTextureSource(),
        )
    }

    @Test
    fun `effect source providers keep model based particle appearance`() {
        val state = Blocks.STONE.defaultBlockState()
        val effect = ControlableFallingDustEffect(UUID.randomUUID(), state)

        assertEquals(textureOfBlock(state), textureOfEffect(effect))

        val particle = CParticle().apply { this.effect = effect }
        val stoneRevision = particle.textureRevision
        val dirtState = Blocks.DIRT.defaultBlockState()
        particle.effect = ControlableFallingDustEffect(UUID.randomUUID(), dirtState)

        assertEquals(stoneRevision + 1, particle.textureRevision)
        assertEquals(textureOfBlock(dirtState), particle.effectiveTextureSource())
    }

    @Test
    fun `invalid model sources are rejected without treating explicit textures as invalid`() {
        assertFalse(CParticleBlockAppearanceResolver.isRenderable(Blocks.AIR.defaultBlockState()))
        assertFalse(resolved(CParticleTextureBindingKey.MISSING).isValid)
        assertTrue(
            resolved(
                CParticleTextureBindingKey(
                    CParticleTextureBindingKind.TEXTURE,
                    ResourceLocation.fromNamespaceAndPath("test", "missing.png"),
                )
            ).isValid
        )
    }

    @Test
    fun `texture revision follows the effective legacy source`() {
        val particle = CParticle()
        particle.effect = TestParticleOptions(ParticleTypes.END_ROD.type)
        val effectRevision = particle.textureRevision

        particle.effect = TestParticleOptions(ParticleTypes.END_ROD.type)
        assertEquals(effectRevision, particle.textureRevision)

        particle.effect = TestParticleOptions(ParticleTypes.FLAME.type)
        assertEquals(effectRevision + 1, particle.textureRevision)

        particle.sprite = ResourceLocation.fromNamespaceAndPath("test", "fixed")
        val spriteRevision = particle.textureRevision
        particle.effect = TestParticleOptions(ParticleTypes.END_ROD.type)
        assertEquals(spriteRevision, particle.textureRevision)

        particle.textureSource = textureOf(ResourceLocation.fromNamespaceAndPath("test", "custom"))
        val explicitRevision = particle.textureRevision
        particle.sprite = ResourceLocation.fromNamespaceAndPath("test", "ignored")
        assertEquals(explicitRevision + 1, particle.textureRevision)
    }

    private class TestParticleOptions(private val particleType: ParticleType<*>) : ParticleOptions {
        override fun getType(): ParticleType<*> = particleType
    }

    private fun resolved(bindingKey: CParticleTextureBindingKey) = CParticleResolvedTexture(
        bindingKey,
        descriptorId = 0,
        uv = CParticleUv.FULL,
        animationId = null,
        colorMultiplier = org.joml.Vector3f(1f),
    )
}
