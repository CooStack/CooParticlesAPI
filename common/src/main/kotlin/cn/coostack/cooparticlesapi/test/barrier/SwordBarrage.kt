package cn.coostack.cooparticlesapi.test.barrier

import cn.coostack.cooparticlesapi.barrages.AbstractBarrage
import cn.coostack.cooparticlesapi.barrages.BarrageHitResult
import cn.coostack.cooparticlesapi.barrages.BarrageOption
import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroup
import net.minecraft.client.gui.screens.social.PlayerEntry
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ExplosionDamageCalculator
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.function.Predicate

class SwordBarrage(
    loc: Vec3,
    world: ServerLevel,
    hitBox: HitBox,
    bindControl: ServerParticleGroup,
    options: BarrageOption,
    val filter: Predicate<LivingEntity>,
    val searchBox: HitBox
) : AbstractBarrage(loc, world, hitBox, bindControl, options) {
    override fun filterHitEntity(livingEntity: LivingEntity): Boolean {
        return filter.test(livingEntity)
    }

    override fun tick() {
        super.tick()
        val entities = world.getEntitiesOfClass(LivingEntity::class.java, searchBox.ofBox(loc), filter)
        var closestEntity: LivingEntity? = null

        for (entity in entities) {
            if (closestEntity == null) {
                closestEntity = entity
                continue
            }
            if (loc.distanceTo(closestEntity.position()) > loc.distanceTo(entity.position())) {
                closestEntity = entity
            }
        }
        if (closestEntity == null) {
            return
        }
        direction = closestEntity.position().subtract(loc).normalize().scale(0.5)
    }

    override fun onHit(result: BarrageHitResult) {
        val hitBlock = result.hitBlockState
        if (hitBlock != null) {
            handleBlock(hitBlock)
        }

        if (result.entities.isNotEmpty()) {
            handleEntities(result.entities)
        }

    }

    private fun handleBlock(hit: BlockState) {
        world.explode(
            shooter,
            shooter?.lastDamageSource,
            ExplosionDamageCalculator(),
            loc.x,
            loc.y,
            loc.z,
            3f,
            false,
            Level.ExplosionInteraction.TRIGGER
        )
    }

    private fun handleEntities(entities: List<LivingEntity>) {
        val attackAmount = 10f
        entities.forEach { entity ->
            entity.lastHurtByMob = shooter
            if (shooter != null) {
                val sources = if (shooter is Player) {
                    entity.damageSources().playerAttack(shooter as Player)
                } else {
                    entity.damageSources().mobAttack(shooter!!)
                }
                entity.hurt(sources, attackAmount)
            } else {
                entity.hurt(
                    world.damageSources().generic(), attackAmount
                )
            }
        }
        world.explode(
            shooter,
            shooter?.lastDamageSource,
            ExplosionDamageCalculator(),
            loc.x,
            loc.y,
            loc.z,
            3f,
            false,
            Level.ExplosionInteraction.TRIGGER
        )
    }
}