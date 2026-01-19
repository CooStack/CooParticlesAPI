package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.animation.Animate
import cn.coostack.cooparticlesapi.animation.AnimateNode
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.particles.impl.ControlableSplashEffect
import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestGroupBuilder
import cn.coostack.cooparticlesapi.test.options.animate.TestEmitterAction
import cn.coostack.cooparticlesapi.test.options.animate.TestStyleAction
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestComposition
import cn.coostack.cooparticlesapi.test.options.display.TestBlockDisplayEntity
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestFourierPhotoComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestSeqComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestShapedComposition
import cn.coostack.cooparticlesapi.test.options.particle.emitter.InterpolatorTestEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestCollideEventHandler
import cn.coostack.cooparticlesapi.test.options.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.options.renderer.TestRendererEntity
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

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
                SimpleEmitterOption(
                    TestEventEmitter(player.eyePosition, player.level())
                        .apply {
                            gravity = PhysicConstant.EARTH_GRAVITY
                            shootDirection = player.forward.scale(1.0)
                            templateData.effect = ControlableSplashEffect(templateData.uuid)
                            templateData.color = Math3DUtil.colorOf(255, 0, 0)
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
                SimpleDisplayEntityOption(
                    TestBlockDisplayEntity(player.eyePosition, player.level()), 200
                )
            }.appendOption {
                var styleTick = 0
                SimpleAnimateOption(
                    Animate()
                        .addNode(
                            AnimateNode()
                                .addAction(
                                    TestEmitterAction(
                                        TestEventEmitter(player.eyePosition, player.level())
                                            .apply {
                                                gravity = PhysicConstant.EARTH_GRAVITY
                                                shootDirection = player.forward.scale(1.0)
                                                addEventHandler(TestCollideEventHandler, false)
                                            }
                                    ) {}
                                ).addAction(
                                    TestEmitterAction(
                                        TestEventEmitter(player.eyePosition, player.level())
                                            .apply {
                                                gravity = PhysicConstant.EARTH_GRAVITY
                                                shootDirection = player.forward.scale(-1.0)
                                                addEventHandler(TestCollideEventHandler, false)
                                            }
                                    ) {}
                                ), 20
                        )
                        .addNode(
                            AnimateNode()
                                .addAction(
                                    TestStyleAction(
                                        RomaMagicTestStyle(), player.level(), player.eyePosition + Vec3(0.0, 2.0, 0.0)
                                    ) {
                                        if (styleTick++ > 100) {
                                            this.cancel()
                                        }
                                    }
                                ), 10
                        ), -1)
            }.appendOption {
                ShakeOption(100, player)
            }.appendOption {
                SimpleCompositionOption(TestComposition(player.eyePosition, player.level()).apply {
                    movement = player.forward.asRelative()
                }, 1000)
            }
            .appendOption {
                SimpleCompositionOption(TestSeqComposition(player.eyePosition, player.level()), 1000)
            }
            .appendOption {
                SimpleEmitterOption(
                    InterpolatorTestEmitter(player.eyePosition, player.level())
                        .apply {
                            movement = player.forward * 10.0
                            maxTick = -1
                        }, -1
                )
            }
            .appendOption {
                SimpleCompositionOption(TestFourierPhotoComposition(player.eyePosition, player.level()), 1000)
            }.appendOption {
                SimpleCompositionOption(TestShapedComposition(player.eyePosition, player.level()), 2000)
            }
    }
}