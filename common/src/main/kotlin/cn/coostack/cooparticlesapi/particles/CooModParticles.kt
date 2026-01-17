package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableEnchantmentEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableFireworkEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableFlashEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableFallingDustEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableSplashEffect
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import com.mojang.serialization.MapCodec
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/** 为了兼容 傻逼的 neoforge 的 延迟注册 做出以下修改 */
object CooModParticles {
    val particleTypes = mutableListOf<CommonDeferredRegistry<ParticleType<*>>>()
    val controlableEndRod = register(
        "controlable_end_rod", false, { ControlableEndRodEffect.codec }, { ControlableEndRodEffect.packetCode }
    )

    val controlableEnchantment = register(
        "controlable_enchantment",
        false,
        { ControlableEnchantmentEffect.codec },
        { ControlableEnchantmentEffect.packetCode }
    )

    val controlableCloud = register(
        "controlable_cloud", false, { ControlableCloudEffect.codec }, { ControlableCloudEffect.packetCode }
    )

    val controlableFlash = register(
        "controlable_flash", false, { ControlableFlashEffect.codec }, { ControlableFlashEffect.packetCode }
    )

    val controlableFirework = register(
        "controlable_firework", false, { ControlableFireworkEffect.codec }, { ControlableFireworkEffect.packetCode }
    )

    val controlableFallingDust = register(
        "controlable_falling_dust",
        false,
        { ControlableFallingDustEffect.codec },
        { ControlableFallingDustEffect.packetCode }
    )

    val controlableSplash = register(
        "controlable_splash",
        false,
        { ControlableSplashEffect.codec },
        { ControlableSplashEffect.packetCode }
    )


    fun reg() {
    }

    fun <T : ParticleOptions?> register(
        id: String, alwaysShow: Boolean,
        codecGetter: (type: ParticleType<T>) -> MapCodec<T>,
        packetCodec: (type: ParticleType<T>) -> StreamCodec<FriendlyByteBuf, T>,
    ): CommonDeferredRegistry<ParticleType<T>> {
        val registry = CommonDeferredRegistry(
            BuiltInRegistries.PARTICLE_TYPE,
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, id)
        ) {
            object : ParticleType<T>(alwaysShow) {
                override fun codec(): MapCodec<T> {
                    return codecGetter(this)
                }

                override fun streamCodec(): StreamCodec<in RegistryFriendlyByteBuf, T> {
                    return packetCodec(this)
                }
            }
        }
        particleTypes.add(registry)
        return registry as CommonDeferredRegistry<ParticleType<T>>
    }
}