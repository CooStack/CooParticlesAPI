package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroupManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.test.particle.server.SequencedMagicCircleServer
import cn.coostack.cooparticlesapi.test.particle.style.ExampleSequencedStyle
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class TestSequencedItem : Item(Settings().maxCount(1).maxDamage(120)) {
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        if (world.isClient) {
            return super.use(world, user, hand)
        }

//        ServerParticleGroupManager.addParticleGroup(
//            SequencedMagicCircleServer(user.uuid), user.eyePos, user.world as ServerWorld
//        )

        ParticleStyleManager.spawnStyle(
            user.world as ServerWorld,
            user.eyePos,
            ExampleSequencedStyle(user.uuid)
        )
        return super.use(world, user, hand)
    }
}