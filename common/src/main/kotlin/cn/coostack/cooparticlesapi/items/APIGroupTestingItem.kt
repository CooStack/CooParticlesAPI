package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.network.particle.emitters.type.EmittersShootTypes
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.test.APITestGroupBuilder
import cn.coostack.cooparticlesapi.test.TestManager
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestCollideEventHandler
import cn.coostack.cooparticlesapi.test.options.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.options.particle.style.RotateTestStyle
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.ServerCameraUtil
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class APIGroupTestingItem(settings: Properties) : Item(settings) {

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (world.isClientSide) {
            return InteractionResultHolder.success(user.getItemInHand(hand))
        }

        val test = TestManager.getTestFromServer(user)
        if (test == null) {
            user.sendSystemMessage(Component.literal("开始测试"))
            TestManager.startTest(APITestGroupBuilder.ID, user)
        } else {
            val old = test.skipCurrent()
            user.sendSystemMessage(Component.literal("跳过测试选项: ${old?.optionID()}"))
        }

//        testEvents(world, user)
//        testEmitter(world as ServerLevel, user as ServerPlayer)
//        CameraUtil.startShakeCamera(240, 0.25)
//        testRomaCircle(world, user)
        // 线性阻力
        return super.use(world, user, hand)
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