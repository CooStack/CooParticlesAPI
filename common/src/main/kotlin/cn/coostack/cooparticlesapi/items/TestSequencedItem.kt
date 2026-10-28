package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroupManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.test.options.particle.server.SequencedMagicCircleServer
import cn.coostack.cooparticlesapi.test.options.particle.style.ExampleSequencedStyle
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/** 触发有序粒子样式测试，保留平台注册使用的无参构造器。 */
class TestSequencedItem : Item(createProperties()) {
    companion object {
        /** 注册声明与构造属性共用的稳定物品路径。 */
        const val ID = "sequenced_test_item"

        /** 为无参构造器创建带注册键的独立属性。 */
        private fun createProperties(): Properties = Properties()
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, ID)))
            .stacksTo(1)
            .durability(120)
    }

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResult {
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
