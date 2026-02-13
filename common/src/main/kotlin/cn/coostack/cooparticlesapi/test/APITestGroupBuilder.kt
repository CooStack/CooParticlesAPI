package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.animation.Animate
import cn.coostack.cooparticlesapi.animation.AnimateNode
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.random
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.impl.ControlableSplashEffect
import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestGroupBuilder
import cn.coostack.cooparticlesapi.test.options.animate.TestEmitterAction
import cn.coostack.cooparticlesapi.test.options.animate.TestStyleAction
import cn.coostack.cooparticlesapi.test.options.display.BarrageItemDisplayEntity
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestComposition
import cn.coostack.cooparticlesapi.test.options.display.TestBlockDisplayEntity
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestFourierPhotoComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestGlowingAnimationComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestModelComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestNoiseLightComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestSeqComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestShapedComposition
import cn.coostack.cooparticlesapi.test.options.particle.emitter.InterpolatorTestEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestCommandEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestRespawnEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestWaveEmitters
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestCollideEventHandler
import cn.coostack.cooparticlesapi.test.options.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.options.renderer.TestBillboardSmokeEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestGlowSphereEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestRendererEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestTexturedBeamEntity
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.random.Random

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
            }.appendOption {
                SimpleDisplayEntityOption(BarrageItemDisplayEntity(player.eyePosition, player.level()), -1)
            }.appendOption {
                SimpleEmitterOption(
                    TestCommandEmitter(player.eyePosition, player.level()).apply {
                        direction = player.forward
                        gravity = 0.05
                        template.setTextureSheet(CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT)
                        template.color = Vector3f(0.35f, 0.70f, 1.00f)
                        maxTick = -1
                        ballRadius = 1.0
                        ballOption.apply {
                            minAge = 80
                            maxAge = 100
                        }
                    }, -1
                ).apply {
                    ticking = {
                        testEmitters as TestCommandEmitter
                    }
                }
            }
            .appendOption {
                SimpleEmitterOption(TestWaveEmitters(player.eyePosition, player.level()).apply {
                }, -1)
            }
            .appendOption {
                SimpleCompositionOption(TestNoiseLightComposition(player.eyePosition, player.level()).apply {
                    end = Vec3.ZERO.random() * Random.nextDouble(10.0, 60.0) + player.eyePosition
                    nodeCount = 32
                }, -1)
            }.appendOption {
                SimpleCompositionOption(
                    TestGlowingAnimationComposition(
                        player.position() + player.forward * 10,
                        player.level()
                    ).apply {
                        glowingTick = 20
                        glowedCount = 32
                    }, -1
                )
            }.appendOption {
                SimpleCompositionOption(TestModelComposition(player.position(), player.level()), -1)
            }.appendOption {
                SimpleRendererEntityOption(TestBillboardSmokeEntity(player.level()).apply {
                    this.setPosition(player.position())
                }, 100)
            }.appendOption {
                SimpleRendererEntityOption(TestGlowSphereEntity(player.level()).apply {
                    this.setPosition(player.position())
                }, 100)
            }.appendOption {
                SimpleRendererEntityOption(TestTexturedBeamEntity(player.level()).apply {
                    this.setPosition(player.position())
                }, 100)
            }.appendOption {
                SimpleEmitterOption(TestRespawnEmitter(player.eyePosition, player.level()).apply {
                    maxTick = 200
                }, -1)
            }
    }
}