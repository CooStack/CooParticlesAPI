package cn.coostack.cooparticlesapi.test.block.builtin

import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.supports.TextureSheetsEnum
import cn.coostack.cooparticlesapi.test.SimpleCompositionOption
import cn.coostack.cooparticlesapi.test.SimpleDisplayEntityOption
import cn.coostack.cooparticlesapi.test.SimpleEmitterOption
import cn.coostack.cooparticlesapi.test.SimpleStyleOption
import cn.coostack.cooparticlesapi.test.api.ControlableParticleEffectBuilder
import cn.coostack.cooparticlesapi.test.api.ControlableParticleEffectTestOptionValue
import cn.coostack.cooparticlesapi.test.api.DoubleTestOptionValue
import cn.coostack.cooparticlesapi.test.api.FloatTestOptionValue
import cn.coostack.cooparticlesapi.test.api.IntTestOptionValue
import cn.coostack.cooparticlesapi.test.api.RelativeLocationTestOptionValue
import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestGroupBuilder
import cn.coostack.cooparticlesapi.test.api.TextureSheetsEnumTestOptionValue
import cn.coostack.cooparticlesapi.test.api.Vec3TestOptionValue
import cn.coostack.cooparticlesapi.test.api.Vector3fTestOptionValue
import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import cn.coostack.cooparticlesapi.test.block.BlockTestPlayer
import cn.coostack.cooparticlesapi.test.options.display.TestBlockDisplayEntity
import cn.coostack.cooparticlesapi.test.options.particle.composition.DynamicCParticleComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestCParticleComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestGPURotationComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestSimpleParticleComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.UsefulMagicTestComposition
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestAlphaShaderEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestCParticleEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestCommandEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestSpreadPointEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestCollideEventHandler
import cn.coostack.cooparticlesapi.test.options.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.PI

class BlockAPITestGroupBuilder(player: Player) : TestGroupBuilder {
    private val player = player as? BlockTestPlayer ?: BlockTestPlayer(player)

    override fun groupID(): String {
        return ID
    }

    override fun build(): TestGroup {
        return BlockTestGroup(player, groupID())
            .appendOption {
                // GPU 粒子发射器: 默认稳态 ≈ 600 × 170 ≈ 10.2 万粒子
                SimpleEmitterOption(
                    TestCParticleEmitter(player.position.add(Vec3(0.0, 0.2, 0.0)), player.level).apply {
                        maxTick = -1
                        delay = 1
                    },
                    200
                )
                    .applyParam(IntTestOptionValue("cp_spawn_per_tick", "每tick生成数"), 600)
                    .applyParam(IntTestOptionValue("cp_max_age", "粒子存活tick"), 170)
                    .applyParam(FloatTestOptionValue("cp_size", "粒子大小"), 0.10f)
                    .applyParam(DoubleTestOptionValue("cp_emit_radius", "生成圆盘半径"), 2.4)
                    .applyParam(DoubleTestOptionValue("cp_spread_speed", "初速度"), 0.10)
                    .applyParam(
                        Vector3fTestOptionValue("cp_color_start", "渐变开始").asColor(),
                        Vector3f(0.20f, 0.72f, 1.00f)
                    )
                    .applyParam(
                        Vector3fTestOptionValue("cp_color_end", "渐变结束").asColor(),
                        Vector3f(1.00f, 0.36f, 0.12f)
                    )
                    .applyParam(DoubleTestOptionValue("cp_vortex_swirl", "漩涡切向强度"), 0.55)
                    .applyParam(DoubleTestOptionValue("cp_vortex_pull", "漩涡吸入强度"), 0.16)
                    .applyParam(DoubleTestOptionValue("cp_vortex_lift", "漩涡升力"), 0.05)
                    .applyParam(DoubleTestOptionValue("cp_noise", "噪声扰动"), 0.030)
                    .applyParam(DoubleTestOptionValue("cp_drag", "指数阻尼"), 0.045)
                    .applyParam(
                        TextureSheetsEnumTestOptionValue("cp_texture_sheet", "渲染效果"),
                        TextureSheetsEnum.ADDITION_BLEND_TRANSLUCENT
                    )
                    .applyParam(
                        ControlableParticleEffectTestOptionValue("effect", "粒子类型"),
                        ControlableParticleEffectBuilder("effect") { uuid, face ->
                            ControlableEndRodEffect(uuid, face)
                        }
                    )
                    .applyTo {
                        it as TestCParticleEmitter
                        it.spawnPerTick = getParam<Int>("cp_spawn_per_tick")!!
                        it.particleMaxAge = getParam<Int>("cp_max_age")!!
                        it.particleSize = getParam<Float>("cp_size")!!
                        it.emitRadius = getParam<Double>("cp_emit_radius")!!
                        it.spreadSpeed = getParam<Double>("cp_spread_speed")!!
                        it.colorStart = getParam<Vector3f>("cp_color_start")!!
                        it.colorEnd = getParam<Vector3f>("cp_color_end")!!
                        it.vortexSwirl = getParam<Double>("cp_vortex_swirl")!!
                        it.vortexRadialPull = getParam<Double>("cp_vortex_pull")!!
                        it.vortexLift = getParam<Double>("cp_vortex_lift")!!
                        it.noiseStrength = getParam<Double>("cp_noise")!!
                        it.dragDamping = getParam<Double>("cp_drag")!!
                        it.template.setTextureSheet(getParam<TextureSheetsEnum>("cp_texture_sheet")!!)
                        it.template.effect = getParam<ControlableParticleEffectBuilder>("effect")!!.build(it.uuid)
                    }
            }
            .appendOption {
                // GPU 粒子 composition: 多层旋转法阵, 验证 composition 控制语义仍然生效
                SimpleCompositionOption(
                    TestCParticleComposition(player.position.add(player.forward.scale(3.0)), player.level),
                    200
                )
                    .applyParam(IntTestOptionValue("cpc_ring_count", "圆环层数"), 4)
                    .applyParam(IntTestOptionValue("cpc_points_per_ring", "每层粒子数"), 320)
                    .applyParam(DoubleTestOptionValue("cpc_radius", "最外层半径"), 2.6)
                    .applyParam(DoubleTestOptionValue("cpc_ring_spacing", "层间距"), 0.35)
                    .applyParam(FloatTestOptionValue("cpc_size", "粒子大小"), 0.16f)
                    .applyParam(
                        Vector3fTestOptionValue("cpc_color_inner", "内层颜色").asColor(),
                        Vector3f(0.35f, 0.85f, 1.00f)
                    )
                    .applyParam(
                        Vector3fTestOptionValue("cpc_color_outer", "外层颜色").asColor(),
                        Vector3f(0.85f, 0.30f, 1.00f)
                    )
                    .applyParam(DoubleTestOptionValue("cpc_rotate_speed", "每tick自转弧度"), Math.PI / 90.0)
                    .applyTo {
                        it as TestCParticleComposition
                        it.ringCount = getParam<Int>("cpc_ring_count")!!
                        it.pointsPerRing = getParam<Int>("cpc_points_per_ring")!!
                        it.radius = getParam<Double>("cpc_radius")!!
                        it.ringSpacing = getParam<Double>("cpc_ring_spacing")!!
                        it.particleSize = getParam<Float>("cpc_size")!!
                        it.colorInner = getParam<Vector3f>("cpc_color_inner")!!
                        it.colorOuter = getParam<Vector3f>("cpc_color_outer")!!
                        it.rotateSpeed = getParam<Double>("cpc_rotate_speed")!!
                    }
            }
            .appendOption {
                SimpleCompositionOption(TestGPURotationComposition(player.position, player.level))
                    .applyParam(RelativeLocationTestOptionValue("to", "相对向量"), RelativeLocation(0, 0, 1))
                    .applyTo {
                        it as TestGPURotationComposition
                        it.to = getParamOrThrow("to")
                    }
            }
            .appendOption {
                SimpleCompositionOption(
                    UsefulMagicTestComposition(player.position, player.level)
                )
            }
            .appendOption {
                SimpleCompositionOption(
                    DynamicCParticleComposition(player.position.add(player.forward.scale(3.0)), player.level),
                    400
                )
            }
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
                        movement = RelativeLocation.of(player.forward)
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
