package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroupManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.test.options.particle.server.SequencedMagicCircleServer
import cn.coostack.cooparticlesapi.test.options.particle.style.ExampleSequencedStyle
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class TestSequencedItem : Item(Item.Properties().stacksTo(1).durability(120)) {
    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (world.isClientSide) {
            return super.use(world, user, hand)
        }

//        ServerParticleGroupManager.addParticleGroup(
//            SequencedMagicCircleServer(user.uuid), user.eyePos, user.world as ServerWorld
//        )

        ParticleStyleManager.spawnStyle(
            user.level() as ServerLevel,
            user.eyePosition,
            ExampleSequencedStyle(user.uuid)
        )
        return super.use(world, user, hand)
    }
}