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
import cn.coostack.cooparticlesapi.test.options.particle.composition.GenNewComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestFourierPhotoComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestGlowingAnimationComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestModelComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestNoiseLightComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestSeqComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestShapedComposition
import cn.coostack.cooparticlesapi.test.options.particle.emitter.InterpolatorTestEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestAlphaShaderEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestCommandEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestRespawnEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestWaveEmitters
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestCollideEventHandler
import cn.coostack.cooparticlesapi.test.options.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.options.renderer.ExampleRendererEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestAccretionDiskEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestBillboardSmokeEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestBlackHoleEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestRendererEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestTexturedBeamEntity
import cn.coostack.cooparticlesapi.test.options.renderer.cases.CaseBlackHoleLensEntity
import cn.coostack.cooparticlesapi.test.options.renderer.cases.CaseGlowAmbientShowcaseEntity
import cn.coostack.cooparticlesapi.test.options.renderer.cases.CaseLaserBeamShowcaseEntity
import cn.coostack.cooparticlesapi.test.options.renderer.cases.CaseMirrorShowcaseEntity
import cn.coostack.cooparticlesapi.test.options.renderer.cases.CaseRenderEntityApiOverviewEntity
import cn.coostack.cooparticlesapi.test.options.renderer.cases.CaseWaterOrbRefractionEntity
import cn.coostack.cooparticlesapi.test.options.renderer.combat.CombatChargeEntity
import cn.coostack.cooparticlesapi.test.options.renderer.combat.CombatEnergyShieldEntity
import cn.coostack.cooparticlesapi.test.options.renderer.combat.CombatExplosionEntity
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
                    TestAlphaShaderEmitter(player.eyePosition, player.level()).apply {
                        maxTick = -1
                        delay = 25
                    }, -1
                )
            }
            .appendOption {
                SimpleRendererEntityOption(ExampleRendererEntity(player.level(), player.eyePosition), -1)
            }
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
                    this.setPosition(player.eyePosition + player.forward * 12.0)
                }, -1)
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
                }, -1)
            }.appendOption {
                SimpleRendererEntityOption(TestRendererEntity(player.level()).apply {
                    this.setPosition(player.eyePosition + player.forward * 18.0)
                }, -1)
            }.appendOption {
                SimpleRendererEntityOption(TestTexturedBeamEntity(player.level()).apply {
                    this.setPosition(player.position())
                }, -1)
            }.appendOption {
                SimpleRendererEntityOption(TestAccretionDiskEntity(player.level()).apply {
                    this.setPosition(player.eyePosition + player.forward * 8.0)
                    radius = 12.8f
                    schwarzschildRadius = 0.92f
                    diskInnerRadius = 2.9f
                    diskOuterRadius = 7.8f
                    diskHalfThickness = 0.30f
                    diskTemperatureScale = 9600.0f
                    lensingStrength = 1.08f
                    spinSpeed = 0.92f
                    stepCount = 168
                    maxDistance = 34.0f
                    diskDensity = 4.6f
                    diskEmissionStrength = 2.35f
                    diskNormal = org.joml.Vector3f(0.0f, 0.42f, 0.91f)
                    diskColor = org.joml.Vector3f(1.18f, 1.06f, 0.78f)
                }, -1)
            }.appendOption {
                SimpleRendererEntityOption(TestBlackHoleEntity(player.level()).apply {
                    this.setPosition(player.eyePosition + player.forward * 8.0)
                    radius = 3.6f
                    distortionStrength = 1.34f
                    diskNormal = org.joml.Vector3f(0.0f, 0.42f, 0.91f)
                    diskThickness = 0.05f
                    diskWidth = 0.82f
                    spinSpeed = 0.76f
                    coreRadius = 0.22f
                    ringColor = org.joml.Vector3f(0.92f, 0.66f, 0.28f)
                }, -1)
            }.appendOption {
                SimpleRendererEntityOption(
                    CaseRenderEntityApiOverviewEntity(player.level()).apply {
                        this.setPosition(player.eyePosition + player.forward * 10.0)
                        radius = 0.95f
                        emission = 1.8f
                    },
                    -1,
                    "案例 / RenderEntity V2 API 总览"
                )
            }.appendOption {
                SimpleRendererEntityOption(
                    CaseGlowAmbientShowcaseEntity(player.level()).apply {
                        this.setPosition(player.eyePosition + player.forward * 12.0)
                        radius = 1.5f
                        glowIntensity = 4.4f
                        lightIntensity = 2.3f
                    },
                    -1,
                    "案例 / 内容驱动 Glow + 环境光"
                )
            }.appendOption {
                SimpleRendererEntityOption(
                    CaseLaserBeamShowcaseEntity(player.level()).apply {
                        this.setPosition(player.eyePosition + player.forward * 8.0)
                        beamWidth = 0.34f
                        beamLength = 12.0f
                        beamDirection = player.forward.add(0.0, 0.18, 0.0).normalize().toVector3f()
                    },
                    -1,
                    "案例 / 程序化激光 + 屏幕亮边"
                )
            }.appendOption {
                SimpleRendererEntityOption(
                    CaseWaterOrbRefractionEntity(player.level()).apply {
                        this.setPosition(player.eyePosition + player.forward * 12.0)
                        radius = 1.9f
                        waveStrength = 0.95f
                        rimStrength = 2.5f
                    },
                    -1,
                    "案例 / 水球 + 折射"
                )
            }.appendOption {
                SimpleRendererEntityOption(
                    CaseMirrorShowcaseEntity(player.level()).apply {
                        this.setPosition(player.eyePosition + player.forward * 12.0)
                        radius = 1.8f
                        reflectivity = 0.98f
                        rimStrength = 2.3f
                    },
                    -1,
                    "案例 / 镜面反射（镜子）"
                )
            }.appendOption {
                SimpleRendererEntityOption(
                    CaseBlackHoleLensEntity(player.level()).apply {
                        this.setPosition(player.eyePosition + player.forward * 8.0)
                        radius = 3.2f
                        distortionStrength = 1.28f
                        diskNormal = org.joml.Vector3f(0.0f, 0.42f, 0.91f)
                        diskThickness = 0.05f
                        diskWidth = 0.78f
                        spinSpeed = 0.80f
                        coreRadius = 0.24f
                        ringColor = org.joml.Vector3f(0.96f, 0.70f, 0.32f)
                    },
                    -1,
                    "案例 / 黑洞引力透镜"
                )
            }.appendOption {
                SimpleRendererEntityOption(
                    CombatExplosionEntity(player.level()).apply {
                        this.setPosition(player.eyePosition + player.forward * 9.0)
                        blastRadius = 0.6f
                        shockRadius = 0.9f
                    },
                    -1,
                    "实战 / 爆炸冲击波"
                )
            }.appendOption {
                SimpleRendererEntityOption(
                    CombatChargeEntity(player.level()).apply {
                        this.setPosition(player.eyePosition + player.forward * 10.0)
                        radius = 0.82f
                        coreColor = org.joml.Vector3f(1.18f, 0.88f, 0.36f)
                    },
                    -1,
                    "实战 / 充能核心"
                )
            }.appendOption {
                SimpleRendererEntityOption(
                    CombatEnergyShieldEntity(player.level()).apply {
                        this.setPosition(player.eyePosition + player.forward * 12.0)
                        radius = 3.1f
                        shieldColor = org.joml.Vector3f(0.34f, 0.82f, 1.18f)
                    },
                    -1,
                    "实战 / 能量护盾"
                )
            }.appendOption {
                SimpleEmitterOption(TestRespawnEmitter(player.eyePosition, player.level()).apply {
                    maxTick = 200
                }, -1)
            }.appendOption {
                SimpleCompositionOption(GenNewComposition(player.eyePosition, player.level()), -1)
            }
    }
}
