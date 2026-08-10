package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.extend.ofID
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.test.APITestGroupBuilder
import cn.coostack.cooparticlesapi.test.TestManager
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestCollideEventHandler
import cn.coostack.cooparticlesapi.test.options.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.options.particle.style.RotateTestStyle
import cn.coostack.cooparticlesapi.utils.ServerCameraUtil
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.ChatFormatting

class APIGroupTestingItem(settings: Properties) : Item(settings) {
    override fun getName(stack: ItemStack): Component {
        return Component.literal("API 测试控制器").withStyle(ChatFormatting.GOLD)
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        tooltipComponents.add(
            Component.literal("用途: 启动并控制 APITestGroupBuilder 测试序列").withStyle(ChatFormatting.YELLOW)
        )
        tooltipComponents.add(
            Component.literal("右键: 当前项通过并继续").withStyle(ChatFormatting.GRAY)
        )
        tooltipComponents.add(
            Component.literal("潜行右键: 快进 +5").withStyle(ChatFormatting.GRAY)
        )
        tooltipComponents.add(
            Component.literal("冲刺右键: 跳到最后").withStyle(ChatFormatting.GRAY)
        )
        tooltipComponents.add(
            Component.literal("PageDown/PageUp: 前进或回退当前测试项").withStyle(ChatFormatting.DARK_GRAY)
        )
    }

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = user.getItemInHand(hand)
        if (world.isClientSide) {
            return InteractionResultHolder.success(stack)
        }

        val test = TestManager.getGamingTestFromServer(user)
        if (test == null) {
            user.sendSystemMessage(Component.literal("开始测试"))
            TestManager.startTest(ofID(APITestGroupBuilder.ID), user)
            user.sendSystemMessage(
                Component.literal(
                    "控制: 右键=通过并继续, 潜行右键=快进+5, 冲刺右键=跳到最后, PageUp/PageDown 可回退/前进"
                )
            )
        } else if (user.isSprinting) {
            TestManager.jumpToLast(user)
        } else if (user.isShiftKeyDown) {
            TestManager.jumpRelative(user, 5)
        } else {
            TestManager.completeCurrent(user)
        }

//        testEvents(world, user)
//        testEmitter(world as ServerLevel, user as ServerPlayer)
//        CameraUtil.startShakeCamera(240, 0.25)
//        testRomaCircle(world, user)
        // 线性阻力
        return InteractionResultHolder.success(stack)
    }

    private fun testShake(world: Level, user: Player) {
        if (world.isClientSide) return
        ServerCameraUtil.sendShake(world as ServerLevel, user.position(), 128.0, 0.5, 10)
    }

    private fun testEvents(world: Level, user: Player) {
        val test = TestEventEmitter(user.eyePosition, world)
            .apply {
                gravity = PhysicConstant.EARTH_GRAVITY
                shootDirection = user.forward.scale(1.0)
                addEventHandler(TestCollideEventHandler, false)
            }
        ParticleEmittersManager.spawnEmitters(test)
    }

    private fun testRotate(world: Level, user: Player) {
        val style = RotateTestStyle(user.uuid)
        ParticleStyleManager.spawnStyle(world as ServerLevel, user.position(), style)
    }

    private fun testRomaCircle(world: Level, user: Player) {
        val style = RomaMagicTestStyle()
        ParticleStyleManager.spawnStyle(world as ServerLevel, user.position(), style)
    }


}
