package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.barrages.BarrageManager
import cn.coostack.cooparticlesapi.barrages.BarrageOption
import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.test.barrier.SwordBarrage
import cn.coostack.cooparticlesapi.test.particle.server.BarrierSwordGroupServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.function.Predicate

class TestBarrierItem : Item(Properties().stacksTo(1)) {

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val res = InteractionResultHolder.success(user.getItemInHand(hand))
        if (world.isClientSide) {
            return res
        }
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
        return res
    }
}