package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.barrages.BarrageManager
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.barrages.BarrageOption
import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.test.options.display.TestDisplayerStyle
import cn.coostack.cooparticlesapi.test.options.display.TestShapeDisplayEntity
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestPlusBlendEmitter
import cn.coostack.cooparticlesapi.test.options.particle.server.BarrierSwordGroupServer
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.function.Predicate

/** 提供单项粒子效果的服务端测试入口，构造时使用固定注册身份。 */
class SingleTesting : Item(createProperties()) {

    companion object {
        /** 注册声明与构造属性共用的稳定物品路径。 */
        const val ID = "single_testing"

        /** 为无参构造器创建带注册键的独立属性。 */
        private fun createProperties(): Properties = Properties()
            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, ID)))
            .stacksTo(1)
    }

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResult {
        val res = InteractionResult.SUCCESS
        if (world.isClientSide) {
            return res
        }
        testPlusBlendEmitter(user, world)
        return res
    }

    fun testPlusBlendEmitter(user: Player, world: Level) {
        val emitter = TestPlusBlendEmitter(user.eyePosition, world)
            .apply {
                maxTick = 1000
                shootMovement = user.forward * 0.8
                template.apply {
                    setTextureSheet(CooParticleTextureSheet.ADDITION_BLEND)
                    color = Math3DUtil.colorOf(210, 80, 255)
                    size = 1.8F
                }
            }
        ParticleEmittersManager.spawnEmitters(emitter)
    }


    fun testDisplayStyle(user: Player, world: Level) {
        val style = TestDisplayerStyle()
        ParticleStyleManager.spawnStyle(world, user.eyePosition, style)
    }

    fun testDisplayEntity(user: Player, world: Level) {
        DisplayEntityManager.spawn(
            TestShapeDisplayEntity(user.eyePosition, world)
        )
    }


}
