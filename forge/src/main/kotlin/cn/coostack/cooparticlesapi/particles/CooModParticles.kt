package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.particles.impl.*
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import com.mojang.serialization.MapCodec
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries



import net.minecraft.resources.ResourceLocation

object CooModParticles {
    val particleTypes = mutableListOf<CommonDeferredRegistry<ParticleType<*>>>()
    val controlableEndRod = register(
        "controlable_end_rod", false, { ControlableEndRodEffect.codec }
    )

    val controlableEnchantment = register(
        "controlable_enchantment",
        false,
        { ControlableEnchantmentEffect.codec },
        { ControlableEnchantmentEffect.packetCode }
    )

    val controlableCloud = register(
        "controlable_cloud", false, { ControlableCloudEffect.codec }
    )

    val controlableFlash = register(
        "controlable_flash", false, { ControlableFlashEffect.codec }
    )

    val controlableFirework = register(
        "controlable_firework", false, { ControlableFireworkEffect.codec }
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
        "controlable_angry_villager", false, { ControlableAngryVillagerEffect.codec }
    )
    val controlableBubble = register(
        "controlable_bubble", false, { ControlableBubbleEffect.codec }
    )
    val controlableBubbleColumnUp = register(
        "controlable_bubble_column_up", false, { ControlableBubbleColumnUpEffect.codec }
    )
    val controlableBubblePop = register(
        "controlable_bubble_pop", false, { ControlableBubblePopEffect.codec }
    )
    val controlableCampfireCosySmoke = register(
        "controlable_campfire_cosy_smoke", true, { ControlableCampfireCosySmokeEffect.codec }
    )
    val controlableCampfireSignalSmoke = register(
        "controlable_campfire_signal_smoke", true, { ControlableCampfireSignalSmokeEffect.codec }
    )
    val controlableComposter = register(
        "controlable_composter", false, { ControlableComposterEffect.codec }
    )
    val controlableCrit = register(
        "controlable_crit", false, { ControlableCritEffect.codec }
    )
    val controlableCurrentDown = register(
        "controlable_current_down", false, { ControlableCurrentDownEffect.codec }
    )
    val controlableDamageIndicator = register(
        "controlable_damage_indicator", true, { ControlableDamageIndicatorEffect.codec }
    )
    val controlableDragonBreath = register(
        "controlable_dragon_breath", false, { ControlableDragonBreathEffect.codec }
    )
    val controlableDolphin = register(
        "controlable_dolphin", false, { ControlableDolphinEffect.codec }
    )
    val controlableDrippingLava = register(
        "controlable_dripping_lava", false, { ControlableDrippingLavaEffect.codec }
    )
    val controlableFallingLava = register(
        "controlable_falling_lava", false, { ControlableFallingLavaEffect.codec }
    )
    val controlableLandingLava = register(
        "controlable_landing_lava", false, { ControlableLandingLavaEffect.codec }
    )
    val controlableDrippingWater = register(
        "controlable_dripping_water", false, { ControlableDrippingWaterEffect.codec }
    )
    val controlableFallingWater = register(
        "controlable_falling_water", false, { ControlableFallingWaterEffect.codec }
    )
    val controlableEffect = register(
        "controlable_effect", false, { ControlableEffectParticleEffect.codec }
    )
    val controlableEnchantedHit = register(
        "controlable_enchanted_hit", false, { ControlableEnchantedHitEffect.codec }
    )
    val controlableExplosion = register(
        "controlable_explosion", true, { ControlableExplosionEffect.codec }
    )
    val controlableSonicBoom = register(
        "controlable_sonic_boom", true, { ControlableSonicBoomEffect.codec }
    )
    val controlableGust = register(
        "controlable_gust", true, { ControlableGustEffect.codec }
    )
    val controlableSmallGust = register(
        "controlable_small_gust", false, { ControlableSmallGustEffect.codec }
    )
    val controlableFishing = register(
        "controlable_fishing", false, { ControlableFishingEffect.codec }
    )
    val controlableFlame = register(
        "controlable_flame", false, { ControlableFlameEffect.codec }
    )
    val controlableInfested = register(
        "controlable_infested", false, { ControlableInfestedEffect.codec }
    )
    val controlableCherryLeaves = register(
        "controlable_cherry_leaves", false, { ControlableCherryLeavesEffect.codec }
    )
    val controlableSculkSoul = register(
        "controlable_sculk_soul", false, { ControlableSculkSoulEffect.codec }
    )
    val controlableSculkChargePop = register(
        "controlable_sculk_charge_pop", true, { ControlableSculkChargePopEffect.codec }
    )
    val controlableSoul = register(
        "controlable_soul", false, { ControlableSoulEffect.codec }
    )
    val controlableSoulFireFlame = register(
        "controlable_soul_fire_flame", false, { ControlableSoulFireFlameEffect.codec }
    )
    val controlableHappyVillager = register(
        "controlable_happy_villager", false, { ControlableHappyVillagerEffect.codec }
    )
    val controlableHeart = register(
        "controlable_heart", false, { ControlableHeartEffect.codec }
    )
    val controlableInstantEffect = register(
        "controlable_instant_effect", false, { ControlableInstantEffectParticleEffect.codec }
    )
    val controlableLargeSmoke = register(
        "controlable_large_smoke", false, { ControlableLargeSmokeEffect.codec }
    )
    val controlableLava = register(
        "controlable_lava", false, { ControlableLavaEffect.codec }
    )
    val controlableMycelium = register(
        "controlable_mycelium", false, { ControlableMyceliumEffect.codec }
    )
    val controlableNautilus = register(
        "controlable_nautilus", false, { ControlableNautilusEffect.codec }
    )
    val controlableNote = register(
        "controlable_note", false, { ControlableNoteEffect.codec }
    )
    val controlablePoof = register(
        "controlable_poof", true, { ControlablePoofEffect.codec }
    )
    val controlablePortal = register(
        "controlable_portal", false, { ControlablePortalEffect.codec }
    )
    val controlableRain = register(
        "controlable_rain", false, { ControlableRainEffect.codec }
    )
    val controlableSmoke = register(
        "controlable_smoke", false, { ControlableSmokeEffect.codec }
    )
    val controlableWhiteSmoke = register(
        "controlable_white_smoke", false, { ControlableWhiteSmokeEffect.codec }
    )
    val controlableSneeze = register(
        "controlable_sneeze", false, { ControlableSneezeEffect.codec }
    )
    val controlableSnowflake = register(
        "controlable_snowflake", false, { ControlableSnowflakeEffect.codec }
    )
    val controlableSpit = register(
        "controlable_spit", true, { ControlableSpitEffect.codec }
    )
    val controlableSweepAttack = register(
        "controlable_sweep_attack", true, { ControlableSweepAttackEffect.codec }
    )
    val controlableTotemOfUndying = register(
        "controlable_totem_of_undying", false, { ControlableTotemOfUndyingEffect.codec }
    )
    val controlableSquidInk = register(
        "controlable_squid_ink", true, { ControlableSquidInkEffect.codec }
    )
    val controlableUnderwater = register(
        "controlable_underwater", false, { ControlableUnderwaterEffect.codec }
    )
    val controlableWitch = register(
        "controlable_witch", false, { ControlableWitchEffect.codec }
    )
    val controlableDrippingHoney = register(
        "controlable_dripping_honey", false, { ControlableDrippingHoneyEffect.codec }
    )
    val controlableFallingHoney = register(
        "controlable_falling_honey", false, { ControlableFallingHoneyEffect.codec }
    )
    val controlableLandingHoney = register(
        "controlable_landing_honey", false, { ControlableLandingHoneyEffect.codec }
    )
    val controlableFallingNectar = register(
        "controlable_falling_nectar", false, { ControlableFallingNectarEffect.codec }
    )
    val controlableFallingSporeBlossom = register(
        "controlable_falling_spore_blossom", false, { ControlableFallingSporeBlossomEffect.codec }
    )
    val controlableSporeBlossomAir = register(
        "controlable_spore_blossom_air", false, { ControlableSporeBlossomAirEffect.codec }
    )
    val controlableAsh = register(
        "controlable_ash", false, { ControlableAshEffect.codec }
    )
    val controlableCrimsonSpore = register(
        "controlable_crimson_spore", false, { ControlableCrimsonSporeEffect.codec }
    )
    val controlableWarpedSpore = register(
        "controlable_warped_spore", false, { ControlableWarpedSporeEffect.codec }
    )
    val controlableDrippingObsidianTear = register(
        "controlable_dripping_obsidian_tear", false, { ControlableDrippingObsidianTearEffect.codec }
    )
    val controlableFallingObsidianTear = register(
        "controlable_falling_obsidian_tear", false, { ControlableFallingObsidianTearEffect.codec }
    )
    val controlableLandingObsidianTear = register(
        "controlable_landing_obsidian_tear", false, { ControlableLandingObsidianTearEffect.codec }
    )
    val controlableReversePortal = register(
        "controlable_reverse_portal", false, { ControlableReversePortalEffect.codec }
    )
    val controlableWhiteAsh = register(
        "controlable_white_ash", false, { ControlableWhiteAshEffect.codec }
    )
    val controlableSmallFlame = register(
        "controlable_small_flame", false, { ControlableSmallFlameEffect.codec }
    )
    val controlableDrippingDripstoneWater = register(
        "controlable_dripping_dripstone_water", false, { ControlableDrippingDripstoneWaterEffect.codec }
    )
    val controlableFallingDripstoneWater = register(
        "controlable_falling_dripstone_water", false, { ControlableFallingDripstoneWaterEffect.codec }
    )
    val controlableDrippingDripstoneLava = register(
        "controlable_dripping_dripstone_lava", false, { ControlableDrippingDripstoneLavaEffect.codec }
    )
    val controlableFallingDripstoneLava = register(
        "controlable_falling_dripstone_lava", false, { ControlableFallingDripstoneLavaEffect.codec }
    )
    val controlableGlowSquidInk = register(
        "controlable_glow_squid_ink", true, { ControlableGlowSquidInkEffect.codec }
    )
    val controlableGlow = register(
        "controlable_glow", true, { ControlableGlowEffect.codec }
    )
    val controlableWaxOn = register(
        "controlable_wax_on", true, { ControlableWaxOnEffect.codec }
    )
    val controlableWaxOff = register(
        "controlable_wax_off", true, { ControlableWaxOffEffect.codec }
    )
    val controlableElectricSpark = register(
        "controlable_electric_spark", true, { ControlableElectricSparkEffect.codec }
    )
    val controlableScrape = register(
        "controlable_scrape", true, { ControlableScrapeEffect.codec }
    )
    val controlableEggCrack = register(
        "controlable_egg_crack", false, { ControlableEggCrackEffect.codec }
    )
    val controlableDustPlume = register(
        "controlable_dust_plume", false, { ControlableDustPlumeEffect.codec }
    )
    val controlableTrialSpawnerDetection = register(
        "controlable_trial_spawner_detection", true, { ControlableTrialSpawnerDetectionEffect.codec }
    )
    val controlableTrialSpawnerDetectionOminous = register(
        "controlable_trial_spawner_detection_ominous", true, { ControlableTrialSpawnerDetectionOminousEffect.codec }
    )
    val controlableVaultConnection = register(
        "controlable_vault_connection", true, { ControlableVaultConnectionEffect.codec }
    )
    val controlableRaidOmen = register(
        "controlable_raid_omen", false, { ControlableRaidOmenEffect.codec }
    )
    val controlableTrialOmen = register(
        "controlable_trial_omen", false, { ControlableTrialOmenEffect.codec }
    )
    val controlableOminousSpawning = register(
        "controlable_ominous_spawning", true, { ControlableOminousSpawningEffect.codec }
    )

    fun reg() {
    }

    fun <T : ParticleOptions?> register(
        id: String, alwaysShow: Boolean,
        codecGetter: (type: ParticleType<T>) -> MapCodec<T>,
    ): CommonDeferredRegistry<ParticleType<T>> {
        val registry = CommonDeferredRegistry(
            BuiltInRegistries.PARTICLE_TYPE,
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, id)
        ) {
            object : ParticleType<T>(alwaysShow) {
                override fun codec(): MapCodec<T> {
                    return codecGetter(this)
                }
            }
        }
        particleTypes.add(registry)
        return registry as CommonDeferredRegistry<ParticleType<T>>
    }
}
