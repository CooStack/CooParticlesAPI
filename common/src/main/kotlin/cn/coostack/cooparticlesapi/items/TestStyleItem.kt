package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.test.options.particle.style.PointStyle
import cn.coostack.cooparticlesapi.test.options.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.options.particle.style.TestShapeUtilStyle
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class TestStyleItem : Item(Item.Properties().stacksTo(1).durability(120)) {
    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val res = super.use(world, user, hand)
        if (world.isClientSide) {
            return res
        }
        val style = RomaMagicTestStyle()
        ParticleStyleManager.spawnStyle(world, user.eyePosition.add(user.forward), style)
        return res
    }
}