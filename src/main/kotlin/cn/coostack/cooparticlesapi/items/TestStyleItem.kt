package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.test.particle.style.TestShapeUtilStyle
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World
class TestStyleItem : Item(Settings()) {
    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack?>? {
        val res = super.use(world, user, hand)
        if (world.isClient) {
            return res
        }
        val style = TestShapeUtilStyle()
        ParticleStyleManager.spawnStyle(world, user.pos, style)
        return res
    }
}