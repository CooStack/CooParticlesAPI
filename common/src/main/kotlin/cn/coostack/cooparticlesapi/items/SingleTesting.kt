package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.barrages.BarrageManager
import cn.coostack.cooparticlesapi.barrages.BarrageOption
import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.impl.ControlableFlashEffect
import cn.coostack.cooparticlesapi.test.options.barrier.SwordBarrage
import cn.coostack.cooparticlesapi.test.options.display.TestBlockDisplayEntity
import cn.coostack.cooparticlesapi.test.options.display.TestDisplayerStyle
import cn.coostack.cooparticlesapi.test.options.display.TestShapeDisplayEntity
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestPlusBlendEmitter
import cn.coostack.cooparticlesapi.test.options.particle.server.BarrierSwordGroupServer
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.function.Predicate

class SingleTesting : Item(Properties().stacksTo(1)) {

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val res = InteractionResultHolder.success(user.getItemInHand(hand))
        if (world.isClientSide) {
            return res
        }
        testPlusBlendEmitter(user, world)
        return res
    }

    fun testPlusBlendEmitter(user: Player, world: Level) {
        val emitter = TestPlusBlendEmitter(user.eyePosition, world)
            .apply {
                maxTick = 1000
                shootMovement = user.forward * 0.8
                template.apply {
                    setTextureSheet(CooParticleTextureSheet.ADDITION_BLEND)
                    color = Math3DUtil.colorOf(210, 80, 255)
                    size = 1.8f
                }
            }
        ParticleEmittersManager.spawnEmitters(emitter)
    }


    fun testDisplayStyle(user: Player, world: Level) {
        val style = TestDisplayerStyle()
        ParticleStyleManager.spawnStyle(world, user.eyePosition, style)
    }

    fun testDisplayEntity(user: Player, world: Level) {
        DisplayEntityManager.spawn(
            TestShapeDisplayEntity(user.eyePosition, world)
        )
    }

    fun testBarrage(user: Player, world: Level) {
        val box = HitBox.of(2.0, 2.0, 2.0)
        val search = HitBox.of(50.0, 50.0, 50.0)
        val filter = Predicate<LivingEntity> {
            return@Predicate it.uuid != user.uuid
        }
        val barrier = SwordBarrage(
            user.eyePosition, world as ServerLevel,
            box, BarrierSwordGroupServer(search, filter, user.forward),
            BarrageOption().apply {
                maxLivingTick = 150
                enableSpeed = true
                speed = 1.5
            }, filter, search
        ).apply {
            shooter = user
        }
        barrier.direction = user.forward
        BarrageManager.spawn(barrier)
    }

}