package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableEnchantmentEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableFireworkEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableFlashEffect
import cn.coostack.cooparticlesapi.particles.impl.TestEndRodEffect
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.serialization.MapCodec
import net.minecraft.client.particle.ParticleTextureSheet
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.texture.SpriteAtlasTexture
import net.minecraft.client.texture.TextureManager
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object CooModParticles {


    fun reg() {}
    val testEndRod: ParticleType<TestEndRodEffect> = register(
        "test_end_rod", false, { TestEndRodEffect.codec }, { TestEndRodEffect.packetCode }
    )

    val enchantment: ParticleType<ControlableEnchantmentEffect> = register(
        "enchantment", false, { ControlableEnchantmentEffect.codec }, { ControlableEnchantmentEffect.packetCode }
    )

    val controlableCloud: ParticleType<ControlableCloudEffect> = register(
        "controlable_cloud", false, { ControlableCloudEffect.codec }, { ControlableCloudEffect.packetCode }
    )

    val controlableFlash: ParticleType<ControlableFlashEffect> = register(
        "controlable_flash", false, { ControlableFlashEffect.codec }, { ControlableFlashEffect.packetCode }
    )

    val controlableFirework: ParticleType<ControlableFireworkEffect> = register(
        "controlable_firework", false, { ControlableFireworkEffect.codec }, { ControlableFireworkEffect.packetCode }
    )

    fun <T : ParticleEffect?> register(
        id: String, alwaysShow: Boolean,
        codecGetter: (type: ParticleType<T>) -> MapCodec<T>,
        packetCodec: (type: ParticleType<T>) -> PacketCodec<RegistryByteBuf, T>,
    ): ParticleType<T> {
        val particleType = object : ParticleType<T>(alwaysShow) {
            override fun getCodec(): MapCodec<T> {
                return codecGetter(this)
            }

            override fun getPacketCodec(): PacketCodec<in RegistryByteBuf, T> {
                return packetCodec(this)
            }
        }
        return Registry.register(
            Registries.PARTICLE_TYPE, Identifier.of(CooParticleAPI.MOD_ID, id), particleType
        )
    }
}