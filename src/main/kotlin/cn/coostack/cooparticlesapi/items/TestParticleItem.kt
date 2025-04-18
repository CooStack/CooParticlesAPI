package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroupManager
import cn.coostack.cooparticlesapi.test.particle.server.ScaleCircleGroupServer
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class TestParticleItem(settings: Settings) : Item(settings) {

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        if (world.isClient) {
            return TypedActionResult.success(user.getStackInHand(hand))
        }
        val serverGroup = ScaleCircleGroupServer(user.uuid)
        ServerParticleGroupManager.addParticleGroup(
            serverGroup, user.pos, world as ServerWorld
        )
        return super.use(world, user, hand)
    }

}