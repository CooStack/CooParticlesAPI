package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.test.options.particle.style.PointStyle
import cn.coostack.cooparticlesapi.test.options.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.options.particle.style.TestShapeUtilStyle
import net.minecraft.world.InteractionHand
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/** 触发粒子样式测试，构造时保持与注册声明一致的物品身份。 */
class TestStyleItem : Item(createProperties()) {
    companion object {
        /** 注册声明与构造属性共用的稳定物品路径。 */
        const val ID = "test_style"

        /** 为无参构造器创建带注册键的独立属性。 */
        private fun createProperties(): Properties = Properties()
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, ID)))
            .stacksTo(1)
            .durability(120)
    }

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResult {
        val res = super.use(world, user, hand)
        if (world.isClientSide) {
            return res
        }
        val style = RomaMagicTestStyle()
        ParticleStyleManager.spawnStyle(world, user.eyePosition.add(user.forward), style)
        return res
    }
}
