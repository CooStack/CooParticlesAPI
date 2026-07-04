package cn.coostack.cooparticlesapi.test.block.builtin

import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.test.SimpleCompositionOption
import cn.coostack.cooparticlesapi.test.SimpleDisplayEntityOption
import cn.coostack.cooparticlesapi.test.SimpleEmitterOption
import cn.coostack.cooparticlesapi.test.SimpleStyleOption
import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestGroupBuilder
import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import cn.coostack.cooparticlesapi.test.block.BlockTestPlayer
import cn.coostack.cooparticlesapi.test.block.BlockWrappedTestOption
import cn.coostack.cooparticlesapi.test.options.display.TestBlockDisplayEntity
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestSimpleParticleComposition
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestAlphaShaderEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestCommandEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestCollideEventHandler
import cn.coostack.cooparticlesapi.test.options.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

class BlockAPITestGroupBuilder(player: Player) : TestGroupBuilder {
    private val player = player as? BlockTestPlayer ?: BlockTestPlayer(player)

    override fun groupID(): String {
        return ID
    }

    override fun build(): TestGroup {
        return BlockTestGroup(player, groupID())
            .appendOption {
                BlockWrappedTestOption(
                    SimpleCompositionOption(
                        TestSimpleParticleComposition(player.position.add(player.forward.scale(3.0)), player.level),
                        160
                    )
                )
            }
            .appendOption {
                BlockWrappedTestOption(
                    SimpleDisplayEntityOption(
                        TestBlockDisplayEntity(player.position.add(Vec3(0.0, 1.0, 0.0)), player.level),
                        160
                    )
                )
            }
            .appendOption {
                BlockWrappedTestOption(
                    SimpleCompositionOption(
                        TestComposition(player.position.add(player.forward.scale(2.0)), player.level).apply {
                            movement = player.forward.asRelative()
                        },
                        140
                    )
                )
            }
            .appendOption {
                BlockWrappedTestOption(
                    SimpleEmitterOption(
                        TestAlphaShaderEmitter(player.position.add(Vec3(0.0, 1.0, 0.0)), player.level).apply {
                            maxTick = -1
                            delay = 25
                        },
                        140
                    )
                )
            }
            .appendOption {
                BlockWrappedTestOption(
                    SimpleEmitterOption(
                        TestEventEmitter(player.position.add(Vec3(0.0, 1.0, 0.0)), player.level).apply {
                            gravity = PhysicConstant.EARTH_GRAVITY
                            shootDirection = player.forward.scale(1.0)
                            templateData.color = Math3DUtil.colorOf(255, 0, 0)
                            addEventHandler(TestCollideEventHandler, false)
                        },
                        140
                    )
                )
            }
            .appendOption {
                BlockWrappedTestOption(
                    SimpleStyleOption(
                        RomaMagicTestStyle(),
                        player.level,
                        player.position.add(Vec3(0.0, 1.0, 0.0)),
                        120
                    )
                )
            }
            .appendOption {
                BlockWrappedTestOption(
                    SimpleEmitterOption(
                        TestCommandEmitter(player.position.add(Vec3(0.0, 1.0, 0.0)), player.level).apply {
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
                        },
                        140
                    )
                )
            }
    }

    companion object {
        const val ID = "block-api-test-group-builder"
    }
}
