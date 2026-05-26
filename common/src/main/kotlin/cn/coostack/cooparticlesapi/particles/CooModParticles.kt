package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.particles.impl.*
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

    val controlableAngryVillager = register(
        "controlable_angry_villager", false, { ControlableAngryVillagerEffect.codec }, { ControlableAngryVillagerEffect.packetCode }
    )
    val controlableBubble = register(
        "controlable_bubble", false, { ControlableBubbleEffect.codec }, { ControlableBubbleEffect.packetCode }
    )
    val controlableBubbleColumnUp = register(
        "controlable_bubble_column_up", false, { ControlableBubbleColumnUpEffect.codec }, { ControlableBubbleColumnUpEffect.packetCode }
    )
    val controlableBubblePop = register(
        "controlable_bubble_pop", false, { ControlableBubblePopEffect.codec }, { ControlableBubblePopEffect.packetCode }
    )
    val controlableCampfireCosySmoke = register(
        "controlable_campfire_cosy_smoke", true, { ControlableCampfireCosySmokeEffect.codec }, { ControlableCampfireCosySmokeEffect.packetCode }
    )
    val controlableCampfireSignalSmoke = register(
        "controlable_campfire_signal_smoke", true, { ControlableCampfireSignalSmokeEffect.codec }, { ControlableCampfireSignalSmokeEffect.packetCode }
    )
    val controlableComposter = register(
        "controlable_composter", false, { ControlableComposterEffect.codec }, { ControlableComposterEffect.packetCode }
    )
    val controlableCrit = register(
        "controlable_crit", false, { ControlableCritEffect.codec }, { ControlableCritEffect.packetCode }
    )
    val controlableCurrentDown = register(
        "controlable_current_down", false, { ControlableCurrentDownEffect.codec }, { ControlableCurrentDownEffect.packetCode }
    )
    val controlableDamageIndicator = register(
        "controlable_damage_indicator", true, { ControlableDamageIndicatorEffect.codec }, { ControlableDamageIndicatorEffect.packetCode }
    )
    val controlableDragonBreath = register(
        "controlable_dragon_breath", false, { ControlableDragonBreathEffect.codec }, { ControlableDragonBreathEffect.packetCode }
    )
    val controlableDolphin = register(
        "controlable_dolphin", false, { ControlableDolphinEffect.codec }, { ControlableDolphinEffect.packetCode }
    )
    val controlableDrippingLava = register(
        "controlable_dripping_lava", false, { ControlableDrippingLavaEffect.codec }, { ControlableDrippingLavaEffect.packetCode }
    )
    val controlableFallingLava = register(
        "controlable_falling_lava", false, { ControlableFallingLavaEffect.codec }, { ControlableFallingLavaEffect.packetCode }
    )
    val controlableLandingLava = register(
        "controlable_landing_lava", false, { ControlableLandingLavaEffect.codec }, { ControlableLandingLavaEffect.packetCode }
    )
    val controlableDrippingWater = register(
        "controlable_dripping_water", false, { ControlableDrippingWaterEffect.codec }, { ControlableDrippingWaterEffect.packetCode }
    )
    val controlableFallingWater = register(
        "controlable_falling_water", false, { ControlableFallingWaterEffect.codec }, { ControlableFallingWaterEffect.packetCode }
    )
    val controlableEffect = register(
        "controlable_effect", false, { ControlableEffectParticleEffect.codec }, { ControlableEffectParticleEffect.packetCode }
    )
    val controlableEnchantedHit = register(
        "controlable_enchanted_hit", false, { ControlableEnchantedHitEffect.codec }, { ControlableEnchantedHitEffect.packetCode }
    )
    val controlableExplosion = register(
        "controlable_explosion", true, { ControlableExplosionEffect.codec }, { ControlableExplosionEffect.packetCode }
    )
    val controlableSonicBoom = register(
        "controlable_sonic_boom", true, { ControlableSonicBoomEffect.codec }, { ControlableSonicBoomEffect.packetCode }
    )
    val controlableGust = register(
        "controlable_gust", true, { ControlableGustEffect.codec }, { ControlableGustEffect.packetCode }
    )
    val controlableSmallGust = register(
        "controlable_small_gust", false, { ControlableSmallGustEffect.codec }, { ControlableSmallGustEffect.packetCode }
    )
    val controlableFishing = register(
        "controlable_fishing", false, { ControlableFishingEffect.codec }, { ControlableFishingEffect.packetCode }
    )
    val controlableFlame = register(
        "controlable_flame", false, { ControlableFlameEffect.codec }, { ControlableFlameEffect.packetCode }
    )
    val controlableInfested = register(
        "controlable_infested", false, { ControlableInfestedEffect.codec }, { ControlableInfestedEffect.packetCode }
    )
    val controlableCherryLeaves = register(
        "controlable_cherry_leaves", false, { ControlableCherryLeavesEffect.codec }, { ControlableCherryLeavesEffect.packetCode }
    )
    val controlableSculkSoul = register(
        "controlable_sculk_soul", false, { ControlableSculkSoulEffect.codec }, { ControlableSculkSoulEffect.packetCode }
    )
    val controlableSculkChargePop = register(
        "controlable_sculk_charge_pop", true, { ControlableSculkChargePopEffect.codec }, { ControlableSculkChargePopEffect.packetCode }
    )
    val controlableSoul = register(
        "controlable_soul", false, { ControlableSoulEffect.codec }, { ControlableSoulEffect.packetCode }
    )
    val controlableSoulFireFlame = register(
        "controlable_soul_fire_flame", false, { ControlableSoulFireFlameEffect.codec }, { ControlableSoulFireFlameEffect.packetCode }
    )
    val controlableHappyVillager = register(
        "controlable_happy_villager", false, { ControlableHappyVillagerEffect.codec }, { ControlableHappyVillagerEffect.packetCode }
    )
    val controlableHeart = register(
        "controlable_heart", false, { ControlableHeartEffect.codec }, { ControlableHeartEffect.packetCode }
    )
    val controlableInstantEffect = register(
        "controlable_instant_effect", false, { ControlableInstantEffectParticleEffect.codec }, { ControlableInstantEffectParticleEffect.packetCode }
    )
    val controlableLargeSmoke = register(
        "controlable_large_smoke", false, { ControlableLargeSmokeEffect.codec }, { ControlableLargeSmokeEffect.packetCode }
    )
    val controlableLava = register(
        "controlable_lava", false, { ControlableLavaEffect.codec }, { ControlableLavaEffect.packetCode }
    )
    val controlableMycelium = register(
        "controlable_mycelium", false, { ControlableMyceliumEffect.codec }, { ControlableMyceliumEffect.packetCode }
    )
    val controlableNautilus = register(
        "controlable_nautilus", false, { ControlableNautilusEffect.codec }, { ControlableNautilusEffect.packetCode }
    )
    val controlableNote = register(
        "controlable_note", false, { ControlableNoteEffect.codec }, { ControlableNoteEffect.packetCode }
    )
    val controlablePoof = register(
        "controlable_poof", true, { ControlablePoofEffect.codec }, { ControlablePoofEffect.packetCode }
    )
    val controlablePortal = register(
        "controlable_portal", false, { ControlablePortalEffect.codec }, { ControlablePortalEffect.packetCode }
    )
    val controlableRain = register(
        "controlable_rain", false, { ControlableRainEffect.codec }, { ControlableRainEffect.packetCode }
    )
    val controlableSmoke = register(
        "controlable_smoke", false, { ControlableSmokeEffect.codec }, { ControlableSmokeEffect.packetCode }
    )
    val controlableWhiteSmoke = register(
        "controlable_white_smoke", false, { ControlableWhiteSmokeEffect.codec }, { ControlableWhiteSmokeEffect.packetCode }
    )
    val controlableSneeze = register(
        "controlable_sneeze", false, { ControlableSneezeEffect.codec }, { ControlableSneezeEffect.packetCode }
    )
    val controlableSnowflake = register(
        "controlable_snowflake", false, { ControlableSnowflakeEffect.codec }, { ControlableSnowflakeEffect.packetCode }
    )
    val controlableSpit = register(
        "controlable_spit", true, { ControlableSpitEffect.codec }, { ControlableSpitEffect.packetCode }
    )
    val controlableSweepAttack = register(
        "controlable_sweep_attack", true, { ControlableSweepAttackEffect.codec }, { ControlableSweepAttackEffect.packetCode }
    )
    val controlableTotemOfUndying = register(
        "controlable_totem_of_undying", false, { ControlableTotemOfUndyingEffect.codec }, { ControlableTotemOfUndyingEffect.packetCode }
    )
    val controlableSquidInk = register(
        "controlable_squid_ink", true, { ControlableSquidInkEffect.codec }, { ControlableSquidInkEffect.packetCode }
    )
    val controlableUnderwater = register(
        "controlable_underwater", false, { ControlableUnderwaterEffect.codec }, { ControlableUnderwaterEffect.packetCode }
    )
    val controlableWitch = register(
        "controlable_witch", false, { ControlableWitchEffect.codec }, { ControlableWitchEffect.packetCode }
    )
    val controlableDrippingHoney = register(
        "controlable_dripping_honey", false, { ControlableDrippingHoneyEffect.codec }, { ControlableDrippingHoneyEffect.packetCode }
    )
    val controlableFallingHoney = register(
        "controlable_falling_honey", false, { ControlableFallingHoneyEffect.codec }, { ControlableFallingHoneyEffect.packetCode }
    )
    val controlableLandingHoney = register(
        "controlable_landing_honey", false, { ControlableLandingHoneyEffect.codec }, { ControlableLandingHoneyEffect.packetCode }
    )
    val controlableFallingNectar = register(
        "controlable_falling_nectar", false, { ControlableFallingNectarEffect.codec }, { ControlableFallingNectarEffect.packetCode }
    )
    val controlableFallingSporeBlossom = register(
        "controlable_falling_spore_blossom", false, { ControlableFallingSporeBlossomEffect.codec }, { ControlableFallingSporeBlossomEffect.packetCode }
    )
    val controlableSporeBlossomAir = register(
        "controlable_spore_blossom_air", false, { ControlableSporeBlossomAirEffect.codec }, { ControlableSporeBlossomAirEffect.packetCode }
    )
    val controlableAsh = register(
        "controlable_ash", false, { ControlableAshEffect.codec }, { ControlableAshEffect.packetCode }
    )
    val controlableCrimsonSpore = register(
        "controlable_crimson_spore", false, { ControlableCrimsonSporeEffect.codec }, { ControlableCrimsonSporeEffect.packetCode }
    )
    val controlableWarpedSpore = register(
        "controlable_warped_spore", false, { ControlableWarpedSporeEffect.codec }, { ControlableWarpedSporeEffect.packetCode }
    )
    val controlableDrippingObsidianTear = register(
        "controlable_dripping_obsidian_tear", false, { ControlableDrippingObsidianTearEffect.codec }, { ControlableDrippingObsidianTearEffect.packetCode }
    )
    val controlableFallingObsidianTear = register(
        "controlable_falling_obsidian_tear", false, { ControlableFallingObsidianTearEffect.codec }, { ControlableFallingObsidianTearEffect.packetCode }
    )
    val controlableLandingObsidianTear = register(
        "controlable_landing_obsidian_tear", false, { ControlableLandingObsidianTearEffect.codec }, { ControlableLandingObsidianTearEffect.packetCode }
    )
    val controlableReversePortal = register(
        "controlable_reverse_portal", false, { ControlableReversePortalEffect.codec }, { ControlableReversePortalEffect.packetCode }
    )
    val controlableWhiteAsh = register(
        "controlable_white_ash", false, { ControlableWhiteAshEffect.codec }, { ControlableWhiteAshEffect.packetCode }
    )
    val controlableSmallFlame = register(
        "controlable_small_flame", false, { ControlableSmallFlameEffect.codec }, { ControlableSmallFlameEffect.packetCode }
    )
    val controlableDrippingDripstoneWater = register(
        "controlable_dripping_dripstone_water", false, { ControlableDrippingDripstoneWaterEffect.codec }, { ControlableDrippingDripstoneWaterEffect.packetCode }
    )
    val controlableFallingDripstoneWater = register(
        "controlable_falling_dripstone_water", false, { ControlableFallingDripstoneWaterEffect.codec }, { ControlableFallingDripstoneWaterEffect.packetCode }
    )
    val controlableDrippingDripstoneLava = register(
        "controlable_dripping_dripstone_lava", false, { ControlableDrippingDripstoneLavaEffect.codec }, { ControlableDrippingDripstoneLavaEffect.packetCode }
    )
    val controlableFallingDripstoneLava = register(
        "controlable_falling_dripstone_lava", false, { ControlableFallingDripstoneLavaEffect.codec }, { ControlableFallingDripstoneLavaEffect.packetCode }
    )
    val controlableGlowSquidInk = register(
        "controlable_glow_squid_ink", true, { ControlableGlowSquidInkEffect.codec }, { ControlableGlowSquidInkEffect.packetCode }
    )
    val controlableGlow = register(
        "controlable_glow", true, { ControlableGlowEffect.codec }, { ControlableGlowEffect.packetCode }
    )
    val controlableWaxOn = register(
        "controlable_wax_on", true, { ControlableWaxOnEffect.codec }, { ControlableWaxOnEffect.packetCode }
    )
    val controlableWaxOff = register(
        "controlable_wax_off", true, { ControlableWaxOffEffect.codec }, { ControlableWaxOffEffect.packetCode }
    )
    val controlableElectricSpark = register(
        "controlable_electric_spark", true, { ControlableElectricSparkEffect.codec }, { ControlableElectricSparkEffect.packetCode }
    )
    val controlableScrape = register(
        "controlable_scrape", true, { ControlableScrapeEffect.codec }, { ControlableScrapeEffect.packetCode }
    )
    val controlableEggCrack = register(
        "controlable_egg_crack", false, { ControlableEggCrackEffect.codec }, { ControlableEggCrackEffect.packetCode }
    )
    val controlableDustPlume = register(
        "controlable_dust_plume", false, { ControlableDustPlumeEffect.codec }, { ControlableDustPlumeEffect.packetCode }
    )
    val controlableTrialSpawnerDetection = register(
        "controlable_trial_spawner_detection", true, { ControlableTrialSpawnerDetectionEffect.codec }, { ControlableTrialSpawnerDetectionEffect.packetCode }
    )
    val controlableTrialSpawnerDetectionOminous = register(
        "controlable_trial_spawner_detection_ominous", true, { ControlableTrialSpawnerDetectionOminousEffect.codec }, { ControlableTrialSpawnerDetectionOminousEffect.packetCode }
    )
    val controlableVaultConnection = register(
        "controlable_vault_connection", true, { ControlableVaultConnectionEffect.codec }, { ControlableVaultConnectionEffect.packetCode }
    )
    val controlableRaidOmen = register(
        "controlable_raid_omen", false, { ControlableRaidOmenEffect.codec }, { ControlableRaidOmenEffect.packetCode }
    )
    val controlableTrialOmen = register(
        "controlable_trial_omen", false, { ControlableTrialOmenEffect.codec }, { ControlableTrialOmenEffect.packetCode }
    )
    val controlableOminousSpawning = register(
        "controlable_ominous_spawning", true, { ControlableOminousSpawningEffect.codec }, { ControlableOminousSpawningEffect.packetCode }
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
