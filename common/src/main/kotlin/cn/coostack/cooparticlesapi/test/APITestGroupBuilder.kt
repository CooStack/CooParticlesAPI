package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestGroupBuilder
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestCollideEventHandler
import cn.coostack.cooparticlesapi.test.options.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.options.renderer.TestRendererEntity
import net.minecraft.world.entity.player.Player

class APITestGroupBuilder(val player: Player) : TestGroupBuilder {
    companion object {
        const val ID = "api-test-group-builder"
    }

    override fun groupID(): String {
        return ID
    }

    override fun build(): TestGroup {
        return GamingTestGroup(player, groupID())
            .appendOption {
                SimpleEmitterOption(
                    TestEventEmitter(player.eyePosition, player.level())
                        .apply {
                            gravity = PhysicConstant.EARTH_GRAVITY
                            shootDirection = player.forward.scale(1.0)
                            addEventHandler(TestCollideEventHandler, false)
                        }, -1
                )
            }.appendOption {
                SimpleStyleOption(RomaMagicTestStyle(), player.level(), player.eyePosition, 100)
            }.appendOption {
                SimpleRendererEntityOption(TestRendererEntity(player.level()).apply {
                    this.setPosition(player.position())
                }, 100)
            }.appendOption {
                SimpleEventHandlerOption(player, 10)
            }
    }
}