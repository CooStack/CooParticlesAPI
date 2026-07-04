package cn.coostack.cooparticlesapi.particles.impl

import cn.coostack.cooparticlesapi.particles.CooModParticles
import java.util.UUID

class ControlableAngryVillagerEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableAngryVillager.get() },
    ::ControlableAngryVillagerEffect,
    { ControlableAngryVillagerEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableAngryVillagerEffect>(::ControlableAngryVillagerEffect)
}

class ControlableBubbleEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableBubble.get() },
    ::ControlableBubbleEffect,
    { ControlableBubbleEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableBubbleEffect>(::ControlableBubbleEffect)
}

class ControlableBubbleColumnUpEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableBubbleColumnUp.get() },
        ::ControlableBubbleColumnUpEffect,
        { ControlableBubbleColumnUpEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableBubbleColumnUpEffect>(::ControlableBubbleColumnUpEffect)
}

class ControlableBubblePopEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableBubblePop.get() },
    ::ControlableBubblePopEffect,
    { ControlableBubblePopEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableBubblePopEffect>(::ControlableBubblePopEffect)
}

class ControlableCampfireCosySmokeEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableCampfireCosySmoke.get() },
        ::ControlableCampfireCosySmokeEffect,
        { ControlableCampfireCosySmokeEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableCampfireCosySmokeEffect>(::ControlableCampfireCosySmokeEffect)
}

class ControlableCampfireSignalSmokeEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableCampfireSignalSmoke.get() },
        ::ControlableCampfireSignalSmokeEffect,
        { ControlableCampfireSignalSmokeEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableCampfireSignalSmokeEffect>(::ControlableCampfireSignalSmokeEffect)
}

class ControlableComposterEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableComposter.get() },
    ::ControlableComposterEffect,
    { ControlableComposterEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableComposterEffect>(::ControlableComposterEffect)
}

class ControlableCritEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableCrit.get() },
    ::ControlableCritEffect,
    { ControlableCritEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableCritEffect>(::ControlableCritEffect)
}

class ControlableCurrentDownEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableCurrentDown.get() },
    ::ControlableCurrentDownEffect,
    { ControlableCurrentDownEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableCurrentDownEffect>(::ControlableCurrentDownEffect)
}

class ControlableDamageIndicatorEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableDamageIndicator.get() },
        ::ControlableDamageIndicatorEffect,
        { ControlableDamageIndicatorEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableDamageIndicatorEffect>(::ControlableDamageIndicatorEffect)
}

class ControlableDragonBreathEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableDragonBreath.get() },
    ::ControlableDragonBreathEffect,
    { ControlableDragonBreathEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableDragonBreathEffect>(::ControlableDragonBreathEffect)
}

class ControlableDolphinEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableDolphin.get() },
    ::ControlableDolphinEffect,
    { ControlableDolphinEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableDolphinEffect>(::ControlableDolphinEffect)
}

class ControlableDrippingLavaEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableDrippingLava.get() },
    ::ControlableDrippingLavaEffect,
    { ControlableDrippingLavaEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableDrippingLavaEffect>(::ControlableDrippingLavaEffect)
}

class ControlableFallingLavaEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableFallingLava.get() },
    ::ControlableFallingLavaEffect,
    { ControlableFallingLavaEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableFallingLavaEffect>(::ControlableFallingLavaEffect)
}

class ControlableLandingLavaEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableLandingLava.get() },
    ::ControlableLandingLavaEffect,
    { ControlableLandingLavaEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableLandingLavaEffect>(::ControlableLandingLavaEffect)
}

class ControlableDrippingWaterEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableDrippingWater.get() },
    ::ControlableDrippingWaterEffect,
    { ControlableDrippingWaterEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableDrippingWaterEffect>(::ControlableDrippingWaterEffect)
}

class ControlableFallingWaterEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableFallingWater.get() },
    ::ControlableFallingWaterEffect,
    { ControlableFallingWaterEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableFallingWaterEffect>(::ControlableFallingWaterEffect)
}

class ControlableEffectParticleEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableEffect.get() },
        ::ControlableEffectParticleEffect,
        { ControlableEffectParticleEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableEffectParticleEffect>(::ControlableEffectParticleEffect)
}

class ControlableEnchantedHitEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableEnchantedHit.get() },
    ::ControlableEnchantedHitEffect,
    { ControlableEnchantedHitEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableEnchantedHitEffect>(::ControlableEnchantedHitEffect)
}

class ControlableExplosionEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableExplosion.get() },
    ::ControlableExplosionEffect,
    { ControlableExplosionEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableExplosionEffect>(::ControlableExplosionEffect)
}

class ControlableSonicBoomEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSonicBoom.get() },
    ::ControlableSonicBoomEffect,
    { ControlableSonicBoomEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableSonicBoomEffect>(::ControlableSonicBoomEffect)
}

class ControlableGustEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableGust.get() },
    ::ControlableGustEffect,
    { ControlableGustEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableGustEffect>(::ControlableGustEffect)
}

class ControlableSmallGustEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSmallGust.get() },
    ::ControlableSmallGustEffect,
    { ControlableSmallGustEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableSmallGustEffect>(::ControlableSmallGustEffect)
}

class ControlableFishingEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableFishing.get() },
    ::ControlableFishingEffect,
    { ControlableFishingEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableFishingEffect>(::ControlableFishingEffect)
}

class ControlableFlameEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableFlame.get() },
    ::ControlableFlameEffect,
    { ControlableFlameEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableFlameEffect>(::ControlableFlameEffect)
}

class ControlableInfestedEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableInfested.get() },
    ::ControlableInfestedEffect,
    { ControlableInfestedEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableInfestedEffect>(::ControlableInfestedEffect)
}

class ControlableCherryLeavesEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableCherryLeaves.get() },
    ::ControlableCherryLeavesEffect,
    { ControlableCherryLeavesEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableCherryLeavesEffect>(::ControlableCherryLeavesEffect)
}

class ControlableSculkSoulEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSculkSoul.get() },
    ::ControlableSculkSoulEffect,
    { ControlableSculkSoulEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableSculkSoulEffect>(::ControlableSculkSoulEffect)
}

class ControlableSculkChargePopEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableSculkChargePop.get() },
        ::ControlableSculkChargePopEffect,
        { ControlableSculkChargePopEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableSculkChargePopEffect>(::ControlableSculkChargePopEffect)
}

class ControlableSoulEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSoul.get() },
    ::ControlableSoulEffect,
    { ControlableSoulEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableSoulEffect>(::ControlableSoulEffect)
}

class ControlableSoulFireFlameEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSoulFireFlame.get() },
    ::ControlableSoulFireFlameEffect,
    { ControlableSoulFireFlameEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableSoulFireFlameEffect>(::ControlableSoulFireFlameEffect)
}

class ControlableHappyVillagerEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableHappyVillager.get() },
    ::ControlableHappyVillagerEffect,
    { ControlableHappyVillagerEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableHappyVillagerEffect>(::ControlableHappyVillagerEffect)
}

class ControlableHeartEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableHeart.get() },
    ::ControlableHeartEffect,
    { ControlableHeartEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableHeartEffect>(::ControlableHeartEffect)
}

class ControlableInstantEffectParticleEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableInstantEffect.get() },
        ::ControlableInstantEffectParticleEffect,
        { ControlableInstantEffectParticleEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableInstantEffectParticleEffect>(::ControlableInstantEffectParticleEffect)
}

class ControlableLargeSmokeEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableLargeSmoke.get() },
    ::ControlableLargeSmokeEffect,
    { ControlableLargeSmokeEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableLargeSmokeEffect>(::ControlableLargeSmokeEffect)
}

class ControlableLavaEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableLava.get() },
    ::ControlableLavaEffect,
    { ControlableLavaEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableLavaEffect>(::ControlableLavaEffect)
}

class ControlableMyceliumEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableMycelium.get() },
    ::ControlableMyceliumEffect,
    { ControlableMyceliumEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableMyceliumEffect>(::ControlableMyceliumEffect)
}

class ControlableNautilusEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableNautilus.get() },
    ::ControlableNautilusEffect,
    { ControlableNautilusEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableNautilusEffect>(::ControlableNautilusEffect)
}

class ControlableNoteEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableNote.get() },
    ::ControlableNoteEffect,
    { ControlableNoteEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableNoteEffect>(::ControlableNoteEffect)
}

class ControlablePoofEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlablePoof.get() },
    ::ControlablePoofEffect,
    { ControlablePoofEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlablePoofEffect>(::ControlablePoofEffect)
}

class ControlablePortalEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlablePortal.get() },
    ::ControlablePortalEffect,
    { ControlablePortalEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlablePortalEffect>(::ControlablePortalEffect)
}

class ControlableRainEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableRain.get() },
    ::ControlableRainEffect,
    { ControlableRainEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableRainEffect>(::ControlableRainEffect)
}

class ControlableSmokeEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSmoke.get() },
    ::ControlableSmokeEffect,
    { ControlableSmokeEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableSmokeEffect>(::ControlableSmokeEffect)
}

class ControlableWhiteSmokeEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableWhiteSmoke.get() },
    ::ControlableWhiteSmokeEffect,
    { ControlableWhiteSmokeEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableWhiteSmokeEffect>(::ControlableWhiteSmokeEffect)
}

class ControlableSneezeEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSneeze.get() },
    ::ControlableSneezeEffect,
    { ControlableSneezeEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableSneezeEffect>(::ControlableSneezeEffect)
}

class ControlableSnowflakeEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSnowflake.get() },
    ::ControlableSnowflakeEffect,
    { ControlableSnowflakeEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableSnowflakeEffect>(::ControlableSnowflakeEffect)
}

class ControlableSpitEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSpit.get() },
    ::ControlableSpitEffect,
    { ControlableSpitEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableSpitEffect>(::ControlableSpitEffect)
}

class ControlableSweepAttackEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSweepAttack.get() },
    ::ControlableSweepAttackEffect,
    { ControlableSweepAttackEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableSweepAttackEffect>(::ControlableSweepAttackEffect)
}

class ControlableTotemOfUndyingEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableTotemOfUndying.get() },
        ::ControlableTotemOfUndyingEffect,
        { ControlableTotemOfUndyingEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableTotemOfUndyingEffect>(::ControlableTotemOfUndyingEffect)
}

class ControlableSquidInkEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSquidInk.get() },
    ::ControlableSquidInkEffect,
    { ControlableSquidInkEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableSquidInkEffect>(::ControlableSquidInkEffect)
}

class ControlableUnderwaterEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableUnderwater.get() },
    ::ControlableUnderwaterEffect,
    { ControlableUnderwaterEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableUnderwaterEffect>(::ControlableUnderwaterEffect)
}

class ControlableWitchEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableWitch.get() },
    ::ControlableWitchEffect,
    { ControlableWitchEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableWitchEffect>(::ControlableWitchEffect)
}

class ControlableDrippingHoneyEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableDrippingHoney.get() },
    ::ControlableDrippingHoneyEffect,
    { ControlableDrippingHoneyEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableDrippingHoneyEffect>(::ControlableDrippingHoneyEffect)
}

class ControlableFallingHoneyEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableFallingHoney.get() },
    ::ControlableFallingHoneyEffect,
    { ControlableFallingHoneyEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableFallingHoneyEffect>(::ControlableFallingHoneyEffect)
}

class ControlableLandingHoneyEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableLandingHoney.get() },
    ::ControlableLandingHoneyEffect,
    { ControlableLandingHoneyEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableLandingHoneyEffect>(::ControlableLandingHoneyEffect)
}

class ControlableFallingNectarEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableFallingNectar.get() },
    ::ControlableFallingNectarEffect,
    { ControlableFallingNectarEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableFallingNectarEffect>(::ControlableFallingNectarEffect)
}

class ControlableFallingSporeBlossomEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableFallingSporeBlossom.get() },
        ::ControlableFallingSporeBlossomEffect,
        { ControlableFallingSporeBlossomEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableFallingSporeBlossomEffect>(::ControlableFallingSporeBlossomEffect)
}

class ControlableSporeBlossomAirEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableSporeBlossomAir.get() },
        ::ControlableSporeBlossomAirEffect,
        { ControlableSporeBlossomAirEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableSporeBlossomAirEffect>(::ControlableSporeBlossomAirEffect)
}

class ControlableAshEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableAsh.get() },
    ::ControlableAshEffect,
    { ControlableAshEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableAshEffect>(::ControlableAshEffect)
}

class ControlableCrimsonSporeEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableCrimsonSpore.get() },
    ::ControlableCrimsonSporeEffect,
    { ControlableCrimsonSporeEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableCrimsonSporeEffect>(::ControlableCrimsonSporeEffect)
}

class ControlableWarpedSporeEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableWarpedSpore.get() },
    ::ControlableWarpedSporeEffect,
    { ControlableWarpedSporeEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableWarpedSporeEffect>(::ControlableWarpedSporeEffect)
}

class ControlableDrippingObsidianTearEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableDrippingObsidianTear.get() },
        ::ControlableDrippingObsidianTearEffect,
        { ControlableDrippingObsidianTearEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableDrippingObsidianTearEffect>(::ControlableDrippingObsidianTearEffect)
}

class ControlableFallingObsidianTearEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableFallingObsidianTear.get() },
        ::ControlableFallingObsidianTearEffect,
        { ControlableFallingObsidianTearEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableFallingObsidianTearEffect>(::ControlableFallingObsidianTearEffect)
}

class ControlableLandingObsidianTearEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableLandingObsidianTear.get() },
        ::ControlableLandingObsidianTearEffect,
        { ControlableLandingObsidianTearEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableLandingObsidianTearEffect>(::ControlableLandingObsidianTearEffect)
}

class ControlableReversePortalEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableReversePortal.get() },
    ::ControlableReversePortalEffect,
    { ControlableReversePortalEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableReversePortalEffect>(::ControlableReversePortalEffect)
}

class ControlableWhiteAshEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableWhiteAsh.get() },
    ::ControlableWhiteAshEffect,
    { ControlableWhiteAshEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableWhiteAshEffect>(::ControlableWhiteAshEffect)
}

class ControlableSmallFlameEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableSmallFlame.get() },
    ::ControlableSmallFlameEffect,
    { ControlableSmallFlameEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableSmallFlameEffect>(::ControlableSmallFlameEffect)
}

class ControlableDrippingDripstoneWaterEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableDrippingDripstoneWater.get() },
        ::ControlableDrippingDripstoneWaterEffect,
        { ControlableDrippingDripstoneWaterEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableDrippingDripstoneWaterEffect>(::ControlableDrippingDripstoneWaterEffect)
}

class ControlableFallingDripstoneWaterEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableFallingDripstoneWater.get() },
        ::ControlableFallingDripstoneWaterEffect,
        { ControlableFallingDripstoneWaterEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableFallingDripstoneWaterEffect>(::ControlableFallingDripstoneWaterEffect)
}

class ControlableDrippingDripstoneLavaEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableDrippingDripstoneLava.get() },
        ::ControlableDrippingDripstoneLavaEffect,
        { ControlableDrippingDripstoneLavaEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableDrippingDripstoneLavaEffect>(::ControlableDrippingDripstoneLavaEffect)
}

class ControlableFallingDripstoneLavaEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableFallingDripstoneLava.get() },
        ::ControlableFallingDripstoneLavaEffect,
        { ControlableFallingDripstoneLavaEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableFallingDripstoneLavaEffect>(::ControlableFallingDripstoneLavaEffect)
}

class ControlableGlowSquidInkEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableGlowSquidInk.get() },
    ::ControlableGlowSquidInkEffect,
    { ControlableGlowSquidInkEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableGlowSquidInkEffect>(::ControlableGlowSquidInkEffect)
}

class ControlableGlowEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableGlow.get() },
    ::ControlableGlowEffect,
    { ControlableGlowEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableGlowEffect>(::ControlableGlowEffect)
}

class ControlableWaxOnEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableWaxOn.get() },
    ::ControlableWaxOnEffect,
    { ControlableWaxOnEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableWaxOnEffect>(::ControlableWaxOnEffect)
}

class ControlableWaxOffEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableWaxOff.get() },
    ::ControlableWaxOffEffect,
    { ControlableWaxOffEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableWaxOffEffect>(::ControlableWaxOffEffect)
}

class ControlableElectricSparkEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableElectricSpark.get() },
    ::ControlableElectricSparkEffect,
    { ControlableElectricSparkEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableElectricSparkEffect>(::ControlableElectricSparkEffect)
}

class ControlableScrapeEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableScrape.get() },
    ::ControlableScrapeEffect,
    { ControlableScrapeEffect.packetCode }) {
    companion object : SimpleControlableParticleEffectCodecProvider<ControlableScrapeEffect>(::ControlableScrapeEffect)
}

class ControlableEggCrackEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableEggCrack.get() },
    ::ControlableEggCrackEffect,
    { ControlableEggCrackEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableEggCrackEffect>(::ControlableEggCrackEffect)
}

class ControlableDustPlumeEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableDustPlume.get() },
    ::ControlableDustPlumeEffect,
    { ControlableDustPlumeEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableDustPlumeEffect>(::ControlableDustPlumeEffect)
}

class ControlableTrialSpawnerDetectionEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableTrialSpawnerDetection.get() },
        ::ControlableTrialSpawnerDetectionEffect,
        { ControlableTrialSpawnerDetectionEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableTrialSpawnerDetectionEffect>(::ControlableTrialSpawnerDetectionEffect)
}

class ControlableTrialSpawnerDetectionOminousEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableTrialSpawnerDetectionOminous.get() },
        ::ControlableTrialSpawnerDetectionOminousEffect,
        { ControlableTrialSpawnerDetectionOminousEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableTrialSpawnerDetectionOminousEffect>(::ControlableTrialSpawnerDetectionOminousEffect)
}

class ControlableVaultConnectionEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableVaultConnection.get() },
        ::ControlableVaultConnectionEffect,
        { ControlableVaultConnectionEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableVaultConnectionEffect>(::ControlableVaultConnectionEffect)
}

class ControlableRaidOmenEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableRaidOmen.get() },
    ::ControlableRaidOmenEffect,
    { ControlableRaidOmenEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableRaidOmenEffect>(::ControlableRaidOmenEffect)
}

class ControlableTrialOmenEffect(controlUUID: UUID, faceToPlayer: Boolean = true) : SimpleControlableParticleEffect(
    controlUUID,
    faceToPlayer,
    { CooModParticles.controlableTrialOmen.get() },
    ::ControlableTrialOmenEffect,
    { ControlableTrialOmenEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableTrialOmenEffect>(::ControlableTrialOmenEffect)
}

class ControlableOminousSpawningEffect(controlUUID: UUID, faceToPlayer: Boolean = true) :
    SimpleControlableParticleEffect(
        controlUUID,
        faceToPlayer,
        { CooModParticles.controlableOminousSpawning.get() },
        ::ControlableOminousSpawningEffect,
        { ControlableOminousSpawningEffect.packetCode }) {
    companion object :
        SimpleControlableParticleEffectCodecProvider<ControlableOminousSpawningEffect>(::ControlableOminousSpawningEffect)
}
