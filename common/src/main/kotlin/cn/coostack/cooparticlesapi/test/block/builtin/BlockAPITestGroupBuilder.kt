package cn.coostack.cooparticlesapi.test.block.builtin

import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.supports.TextureSheetsEnum
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.test.SimpleCompositionOption
import cn.coostack.cooparticlesapi.test.SimpleDisplayEntityOption
import cn.coostack.cooparticlesapi.test.SimpleEmitterOption
import cn.coostack.cooparticlesapi.test.SimpleStyleOption
import cn.coostack.cooparticlesapi.test.api.ControlableParticleEffectBuilder
import cn.coostack.cooparticlesapi.test.api.ControlableParticleEffectTestOptionValue
import cn.coostack.cooparticlesapi.test.api.DoubleTestOptionValue
import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestGroupBuilder
import cn.coostack.cooparticlesapi.test.api.TestOptionParamSupport.applyParam
import cn.coostack.cooparticlesapi.test.api.TestOptionParamSupport.getParam
import cn.coostack.cooparticlesapi.test.api.TextureSheetsEnumTestOptionValue
import cn.coostack.cooparticlesapi.test.api.Vec3TestOptionValue
import cn.coostack.cooparticlesapi.test.api.Vector3fTestOptionValue
import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import cn.coostack.cooparticlesapi.test.block.BlockTestPlayer
import cn.coostack.cooparticlesapi.test.options.display.TestBlockDisplayEntity
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestSimpleParticleComposition
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestAlphaShaderEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestCommandEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestSpreadPointEmitter
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
                SimpleCompositionOption(
                    TestSimpleParticleComposition(player.position.add(player.forward.scale(3.0)), player.level),
                    160
                )
            }
            .appendOption {
                SimpleDisplayEntityOption(
                    TestBlockDisplayEntity(player.position.add(Vec3(0.0, 1.0, 0.0)), player.level),
                    160
                )
            }
            .appendOption {
                SimpleCompositionOption(
                    TestComposition(player.position.add(player.forward.scale(2.0)), player.level).apply {
                        movement = player.forward.asRelative()
                    },
                    140
                )
            }
            .appendOption {
                SimpleEmitterOption(
                    TestAlphaShaderEmitter(player.position.add(Vec3(0.0, 1.0, 0.0)), player.level).apply {
                        maxTick = -1
                        delay = 25
                    },
                    140
                )
            }
            .appendOption {
                SimpleEmitterOption(
                    TestEventEmitter(player.position.add(Vec3(0.0, 1.0, 0.0)), player.level).apply {
                        gravity = PhysicConstant.EARTH_GRAVITY
                        shootDirection = player.forward.scale(1.0)
                        templateData.color = Math3DUtil.colorOf(255, 0, 0)
                        addEventHandler(TestCollideEventHandler, false)
                    },
                    140
                )
            }
            .appendOption {
                SimpleStyleOption(
                    RomaMagicTestStyle(),
                    player.level,
                    player.position.add(Vec3(0.0, 1.0, 0.0)),
                    120
                )
            }
            .appendOption {
                SimpleEmitterOption(
                    TestSpreadPointEmitter(player.position.add(Vec3(0.0, 1.0, 0.0)), player.level).apply {
                        maxTick = -1
                        delay = 1
                    },
                    80
                )
                    .applyParam(
                        TextureSheetsEnumTestOptionValue("texture-sheet", "渲染效果"),
                        TextureSheetsEnum.ADDITION_BLEND_TRANSLUCENT
                    )
                    .applyParam(
                        ControlableParticleEffectTestOptionValue("effect", "粒子样式"),
                        ControlableParticleEffectTestOptionValue.defaultBuilder()
                    )
                    .applyParam(
                        Vector3fTestOptionValue("gradient_start", "渐变开始").asColor(),
                        Vector3f(0.20f, 0.72f, 1.00f)
                    )
                    .applyParam(
                        Vector3fTestOptionValue("gradient_end", "渐变结束").asColor(),
                        Vector3f(1.00f, 0.36f, 0.12f)
                    )
                    .applyTo {
                        it as TestSpreadPointEmitter
                        it.template.effect = getParam<ControlableParticleEffectBuilder>("effect")!!
                            .build(it.template.uuid)
                        it.template.setTextureSheet(getParam<TextureSheetsEnum>("texture-sheet")!!)
                        it.colorStart = getParam<Vector3f>("gradient_start")!!
                        it.colorEnd = getParam<Vector3f>("gradient_end")!!
                    }
            }
            .appendOption {
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
                    .applyParam(DoubleTestOptionValue("ball_radius", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius1", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius2", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius3", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius4", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius5", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius6", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius7", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius8", "参数大小"), 1.0)
                    .applyParam(Vec3TestOptionValue("ball_radius9", "测试位置").asPosition(), Vec3.ZERO)
                    .applyParam(Vector3fTestOptionValue("ball_radius10_color", "测试颜色").asColor(), Vector3f(1f))
                    .applyParam(
                        Vector3fTestOptionValue("ball_radius10_position", "测试其他").asPosition(),
                        Vector3f(1f)
                    )
                    .applyParam(DoubleTestOptionValue("ball_radius11", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius12", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius13", "参数大小"), 1.0)
                    .applyParam(
                        TextureSheetsEnumTestOptionValue("ball_radius14", "测试Enum"),
                        TextureSheetsEnum.ADDITION_BLEND_TRANSLUCENT
                    )
                    .applyTo {
                        it as TestCommandEmitter
                        it.ballRadius = getParam<Double>("ball_radius")!!
                    }
            }
    }

    companion object {
        const val ID = "block-api-test-group-builder"
    }
}
