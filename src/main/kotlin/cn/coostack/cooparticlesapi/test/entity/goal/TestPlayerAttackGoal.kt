package cn.coostack.cooparticlesapi.test.entity.goal

import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.ExplodeClassParticleEmitters
import cn.coostack.cooparticlesapi.particles.impl.TestEndRodEffect
import cn.coostack.cooparticlesapi.test.entity.TestPlayerEntity
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import net.minecraft.client.particle.ParticleTextureSheet
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.goal.MeleeAttackGoal
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand

class TestPlayerAttackGoal(entity: TestPlayerEntity, speed: Double, pauseWhenMobIdle: Boolean) :
    MeleeAttackGoal(entity, speed, pauseWhenMobIdle) {
    var attackDelay = 10
    var attackCD = 10
        set(value) {
            field = value.coerceAtLeast(0)
        }

    var targetInDistance = false

    override fun start() {
        super.start()
        attackDelay = 10
        attackCD = 10
    }

    override fun stop() {
        super.stop()
        mob.isAttacking = false
    }

    override fun tick() {
        super.tick()
        // 攻击之后
        if (targetInDistance) {
            attackCD--
        }
    }

    // 攻击距离 = 2
    override fun attack(target: LivingEntity) {
        val distance = mob.pos.distanceTo(target.pos)
        val mob = mob as TestPlayerEntity
        if (distance <= 3.0) {
            targetInDistance = true
            if (attackCD <= attackDelay) {
                mob.isAttacking = true
            }
            if (attackCD <= 0) {
                mob.lookControl.lookAt(target.x, target.eyeY, target.z)
                performAttack(target)
            }
            return
        }
        attackCD = getTickCount(attackDelay * 2)
        targetInDistance = false
        mob.isAttacking = false
        mob.attackTick = 0
    }

    private fun performAttack(target: LivingEntity) {
        attackCD = getTickCount(attackDelay * 2)
        val example = ExplodeClassParticleEmitters(target.eyePos, target.world)
            .also {
                it.maxTick = 5
                it.templateData.apply {
                    maxAge = 60
                    setTextureSheet(ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT)
                    color = Math3DUtil.colorOf(255, 255, 255)
                    effect = TestEndRodEffect(uuid)
                }
            }

        ParticleEmittersManager.spawnEmitters(example)
        this.mob.swingHand(Hand.MAIN_HAND)
        this.mob.tryAttack(target)
        mob.world.playSound(
            null,
            mob.x,
            mob.y,
            mob.z,
            SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
            SoundCategory.PLAYERS,
            3f,
            1f
        )
    }
}