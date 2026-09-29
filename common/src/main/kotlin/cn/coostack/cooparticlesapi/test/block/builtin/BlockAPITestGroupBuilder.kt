package cn.coostack.cooparticlesapi.test.block.builtin

import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathEndMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathForwardAxis
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathPlayMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathProgressMode
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneMode
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.ofID
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.supports.TextureSheetsEnum
import cn.coostack.cooparticlesapi.test.SimpleCompositionOption
import cn.coostack.cooparticlesapi.test.SimpleDisplayEntityOption
import cn.coostack.cooparticlesapi.test.SimpleEmitterOption
import cn.coostack.cooparticlesapi.test.SimpleStyleOption
import cn.coostack.cooparticlesapi.test.api.BooleanTestOptionValue
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
import cn.coostack.cooparticlesapi.test.block.BlockTexturePropagationTestOption
import cn.coostack.cooparticlesapi.test.block.CooFxModelBlockTestOption
import cn.coostack.cooparticlesapi.test.block.OrbitalRailgunBlockTestOption
import cn.coostack.cooparticlesapi.test.block.PathConstraintBlockTestOption
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathOffsetMode
import cn.coostack.cooparticlesapi.test.block.ProceduralTerrainMappingBlockTestOption
import cn.coostack.cooparticlesapi.test.block.StarfieldFboBlockTypeTestOption
import cn.coostack.cooparticlesapi.test.options.display.TestBlockDisplayEntity
import cn.coostack.cooparticlesapi.test.options.particle.composition.DynamicCParticleComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.SequenceTestGPUComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestCParticleComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestGPURotationComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestSimpleParticleComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.UsefulMagicTestComposition
import cn.coostack.cooparticlesapi.test.options.particle.emitter.PathConstraintTestPaths
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestAlphaShaderEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestCParticleEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestCommandEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestGPUEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestPathCommandEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestPathGPUCParticleEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestSpreadPointEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestTransformGPUEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.event.TestCollideEventHandler
import cn.coostack.cooparticlesapi.test.options.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.options.renderer.world.DemoWorldRenderEffectOptions
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.PI

class BlockAPITestGroupBuilder(private val player: Player) : TestGroupBuilder {
    override fun groupID(): ResourceLocation {
        return ID
    }

    override fun build(): TestGroup {
        return BlockTestGroup(player, groupID())
            .appendOption {
                SimpleEmitterOption(
                    TestTransformGPUEmitter(player.position(), player.level()).apply {
                        maxTick = -1
                    }, -1, "位移粒子"
                )
                    .onPlayerUpdate { player, emitter ->
                        emitter.pos = player.position()
                    }
            }
            .appendOption {
                DemoWorldRenderEffectOptions.maskBloomStraightLaser(player)
            }
            .appendOption {
                OrbitalRailgunBlockTestOption(player)
            }
            .appendOption {
                ProceduralTerrainMappingBlockTestOption(player)
            }
            .appendOption {
                SimpleCompositionOption(
                    SequenceTestGPUComposition(player.position(), player.level())
                ).onPlayerUpdate { player, composition ->
                    composition.direction = player.forward
                    composition.teleportTo(player.position())
                }
            }
            .appendOption {
                SimpleEmitterOption(
                    TestGPUEmitter(player.position(), player.level())
                )
            }
            .appendOption {
                // GPU 粒子发射器: 默认稳态 ≈ 600 × 170 ≈ 10.2 万粒子
                SimpleEmitterOption(
                    TestCParticleEmitter(player.position() + Vec3(0.0, 0.2, 0.0), player.level()).apply {
                        maxTick = -1
                        delay = 1
                    },
                    200
                )
                    .applyParam(IntTestOptionValue("cp_spawn_per_tick", "每tick生成数"), 600)
                    .applyParam(IntTestOptionValue("cp_max_age", "粒子存活tick"), 170)
                    .applyParam(BooleanTestOptionValue(CP_BLOCK_COLLISION_ID, "方块碰撞"), true)
                    .applyParam(FloatTestOptionValue("cp_size", "粒子大小"), 0.10F)
                    .applyParam(DoubleTestOptionValue("cp_emit_radius", "生成圆盘半径"), 2.4)
                    .applyParam(DoubleTestOptionValue("cp_spread_speed", "初速度"), 0.10)
                    .applyParam(
                        Vector3fTestOptionValue("cp_color_start", "渐变开始").asColor(),
                        Vector3f(0.20F, 0.72F, 1.00F)
                    )
                    .applyParam(
                        Vector3fTestOptionValue("cp_color_end", "渐变结束").asColor(),
                        Vector3f(1.00F, 0.36F, 0.12F)
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
                        it.spawnPerTick = getParam<Int>("cp_spawn_per_tick")!!
                        it.particleMaxAge = getParam<Int>("cp_max_age")!!
                        it.template.blockCollision = getParam<Boolean>(CP_BLOCK_COLLISION_ID)!!
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
            // ===== 路径位置约束：公共 GPU 数据图层 + 路径约束 ---------------------------------
            // 路径几何作为发射器自己的字段随网络同步，每一侧用它建立本地路径；构造选项不分配路径。
            // 这几条用例覆盖：沿路径前进、绕路径环绕、朝向对齐实际运动方向、拐角参考基、
            // 闭合接缝、动态改点、到达消失的槽位回收、PingPong 掉头。
            .appendOption {
                PathConstraintBlockTestOption(
                    player,
                    PathConstraintTestPaths.orbitArc(),
                    "路径效果: 随机出生分散螺旋 (GPU)",
                ) { emitter ->
                    (emitter as? TestPathGPUCParticleEmitter)?.apply {
                        spawnPerTick = 8
                        playPeriodTicks = 120.0
                        offsetMode = CParticlePathOffsetMode.BIRTH_FRAME
                        spawnSpreadRadius = 0.9
                        orbitRadius = 0.0
                        orbitTurns = 3.0
                        forwardAxis = CParticlePathForwardAxis.MODEL_POSITIVE_Y
                        template.maxAge = 120
                        template.weightSize = 0.035F
                        template.heightSize = 0.16F
                    }
                }
            }
            .appendOption {
                PathConstraintBlockTestOption(
                    player,
                    PathConstraintTestPaths.orbitArc(),
                    "路径效果: 随机出生分散螺旋 (ParticleCommand)",
                    useCommandPath = true,
                ) { emitter ->
                    (emitter as? TestPathCommandEmitter)?.apply {
                        spawnPerTick = 8
                        playPeriodTicks = 120.0
                        offsetMode = CParticlePathOffsetMode.BIRTH_FRAME
                        spawnSpreadRadius = 0.9
                        orbitRadius = 0.0
                        orbitTurns = 3.0
                        template.maxAge = 120
                        template.weightSize = 0.035F
                        template.heightSize = 0.16F
                    }
                }
            }
            .appendOption {
                PathConstraintBlockTestOption(
                    player,
                    PathConstraintTestPaths.bezierArc(),
                    "路径约束: 贝塞尔环绕 (GPU)",
                ) { emitter ->
                    (emitter as? TestPathGPUCParticleEmitter)?.apply {
                        spawnPerTick = 24
                        template.maxAge = 240
                        orbitRadius = 0.65
                        orbitTurns = 3.0
                        playMode = CParticlePathPlayMode.LOOP
                        progressMode = CParticlePathProgressMode.ARC_LENGTH
                        endMode = CParticlePathEndMode.HOLD
                        forwardAxis = CParticlePathForwardAxis.MODEL_POSITIVE_Y
                    }
                }
            }
            // 传统 ControlableParticle 走 ParticleCommand，与上面同一套路径定义与播放规则。
            .appendOption {
                PathConstraintBlockTestOption(
                    player,
                    PathConstraintTestPaths.bezierArc(),
                    "路径约束: 贝塞尔环绕 (传统命令)",
                    useCommandPath = true,
                ) { emitter ->
                    (emitter as? TestPathCommandEmitter)?.apply {
                        spawnPerTick = 6
                        template.maxAge = 200
                        orbitRadius = 0.65
                        orbitTurns = 3.0
                        playMode = CParticlePathPlayMode.LOOP
                        progressMode = CParticlePathProgressMode.ARC_LENGTH
                        endMode = CParticlePathEndMode.HOLD
                        forwardAxis = CParticlePathForwardAxis.MODEL_POSITIVE_Y
                    }
                }
            }
            // 折线急拐角：关闭环绕，检查拐角处横截面参考基是否突然翻转。
            .appendOption {
                PathConstraintBlockTestOption(
                    player,
                    PathConstraintTestPaths.linearZigzag(),
                    "路径约束: 折线急拐角 (无环绕)",
                ) { emitter ->
                    (emitter as? TestPathGPUCParticleEmitter)?.apply {
                        spawnPerTick = 30
                        template.maxAge = 160
                        // 关闭环绕：只看沿程与拐角，避免环绕掩蔽参考基问题。
                        orbitRadius = 0.0
                        orbitTurns = 0.0
                    }
                }
            }
            // 闭合圆环：检查接缝处是否连续推进，而不是从终点跳回起点。
            .appendOption {
                PathConstraintBlockTestOption(
                    player,
                    PathConstraintTestPaths.closedRing(),
                    "路径约束: 闭合圆环",
                ) { emitter ->
                    (emitter as? TestPathGPUCParticleEmitter)?.apply {
                        spawnPerTick = 20
                        template.maxAge = 180
                        orbitRadius = 0.45
                        orbitTurns = 2.0
                    }
                }
            }
            // 动态改点：位置应当立刻跟随新几何，不应有平滑追赶。
            .appendOption {
                PathConstraintBlockTestOption(
                    player,
                    PathConstraintTestPaths.bezierArc(),
                    "路径约束: 动态改点",
                ) { emitter ->
                    (emitter as? TestPathGPUCParticleEmitter)?.apply {
                        spawnPerTick = 24
                        template.maxAge = 240
                        orbitRadius = 0.65
                        orbitTurns = 3.0
                        // 摆动基准由发射器按同步过来的几何自动记录，这里只需打开开关。
                        dynamicPoints = true
                        dynamicAmplitude = 1.2
                        dynamicPeriodTicks = 160
                    }
                }
            }
            // 单程 + 到达消失：路径可以在 maxAge 之前结束粒子，槽位应当被回收。
            .appendOption {
                PathConstraintBlockTestOption(
                    player,
                    PathConstraintTestPaths.bezierArc(),
                    "路径约束: 到达消失",
                ) { emitter ->
                    (emitter as? TestPathGPUCParticleEmitter)?.apply {
                        spawnPerTick = 30
                        template.maxAge = 400
                        orbitRadius = 0.5
                        orbitTurns = 1.0
                        playMode = CParticlePathPlayMode.ONCE
                        endMode = CParticlePathEndMode.DISAPPEAR
                    }
                }
            }
            // PingPong：端点立即掉头，回程朝向与去程相反。
            .appendOption {
                PathConstraintBlockTestOption(
                    player,
                    PathConstraintTestPaths.bezierArc(),
                    "路径约束: PingPong 往返",
                ) { emitter ->
                    (emitter as? TestPathGPUCParticleEmitter)?.apply {
                        spawnPerTick = 24
                        template.maxAge = 300
                        orbitRadius = 0.5
                        orbitTurns = 2.0
                        playMode = CParticlePathPlayMode.PING_PONG
                        progressMode = CParticlePathProgressMode.ARC_LENGTH
                    }
                }
            }
            .appendOption {
                // GPU 粒子 composition: 多层旋转法阵, 验证 composition 控制语义仍然生效
                SimpleCompositionOption(
                    TestCParticleComposition(player.position() + player.forward * 3.0, player.level()),
                    200
                )
                    .applyParam(IntTestOptionValue("cpc_ring_count", "圆环层数"), 4)
                    .applyParam(IntTestOptionValue("cpc_points_per_ring", "每层粒子数"), 320)
                    .applyParam(DoubleTestOptionValue("cpc_radius", "最外层半径"), 2.6)
                    .applyParam(DoubleTestOptionValue("cpc_ring_spacing", "层间距"), 0.35)
                    .applyParam(FloatTestOptionValue("cpc_size", "粒子大小"), 0.16F)
                    .applyParam(
                        Vector3fTestOptionValue("cpc_color_inner", "内层颜色").asColor(),
                        Vector3f(0.35F, 0.85F, 1.00F)
                    )
                    .applyParam(
                        Vector3fTestOptionValue("cpc_color_outer", "外层颜色").asColor(),
                        Vector3f(0.85F, 0.30F, 1.00F)
                    )
                    .applyParam(DoubleTestOptionValue("cpc_rotate_speed", "每tick自转弧度"), PI / 90.0)
                    .applyTo {
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
                SimpleCompositionOption(TestGPURotationComposition(player.position(), player.level()))
                    .applyParam(RelativeLocationTestOptionValue("to", "相对向量"), RelativeLocation(0, 0, 1))
                    .applyTo {
                        it.to = getParamOrThrow("to")
                    }
            }
            .appendOption {
                SimpleCompositionOption(
                    UsefulMagicTestComposition(player.position(), player.level())
                )
            }
            .appendOption {
                SimpleCompositionOption(
                    DynamicCParticleComposition(player.position() + player.forward * 3.0, player.level()),
                    400
                )
            }
            .appendOption {
                SimpleCompositionOption(
                    TestSimpleParticleComposition(player.position() + player.forward * 3.0, player.level()),
                    160
                )
            }
            .appendOption {
                SimpleDisplayEntityOption(
                    TestBlockDisplayEntity(player.position() + Vec3(0.0, 1.0, 0.0), player.level()),
                    160
                )
            }
            .appendOption {
                SimpleCompositionOption(
                    TestComposition(player.position() + player.forward * 2.0, player.level()).apply {
                        movement = RelativeLocation.of(player.forward)
                    },
                    140
                )
            }
            .appendOption {
                SimpleEmitterOption(
                    TestAlphaShaderEmitter(player.position() + Vec3(0.0, 1.0, 0.0), player.level()).apply {
                        maxTick = -1
                        delay = 25
                    },
                    140
                )
            }
            .appendOption {
                SimpleEmitterOption(
                    TestEventEmitter(player.position() + Vec3(0.0, 1.0, 0.0), player.level()).apply {
                        gravity = PhysicConstant.EARTH_GRAVITY
                        shootDirection = player.forward
                        templateData.color = Math3DUtil.colorOf(255, 0, 0)
                        addEventHandler(TestCollideEventHandler, false)
                    },
                    140
                )
            }
            .appendOption {
                SimpleStyleOption(
                    RomaMagicTestStyle(),
                    player.level(),
                    player.position() + Vec3(0.0, 1.0, 0.0),
                    120
                )
            }
            .appendOption {
                SimpleEmitterOption(
                    TestSpreadPointEmitter(player.position() + Vec3(0.0, 1.0, 0.0), player.level()).apply {
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
                        Vector3f(0.20F, 0.72F, 1.00F)
                    )
                    .applyParam(
                        Vector3fTestOptionValue("gradient_end", "渐变结束").asColor(),
                        Vector3f(1.00F, 0.36F, 0.12F)
                    )
                    .applyTo {
                        it.template.effect = getParam<ControlableParticleEffectBuilder>("effect")!!
                            .build(it.template.uuid)
                        it.template.setTextureSheet(getParam<TextureSheetsEnum>("texture-sheet")!!)
                        it.colorStart = getParam<Vector3f>("gradient_start")!!
                        it.colorEnd = getParam<Vector3f>("gradient_end")!!

                    }
            }
            .appendOption {
                SimpleEmitterOption(
                    TestCommandEmitter(player.position() + Vec3(0.0, 1.0, 0.0), player.level()).apply {
                        direction = player.forward
                        gravity = 0.05
                        template.setTextureSheet(CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT)
                        template.color = Vector3f(0.35F, 0.70F, 1.00F)
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
                    .applyParam(Vector3fTestOptionValue("ball_radius10_color", "测试颜色").asColor(), Vector3f(1F))
                    .applyParam(
                        Vector3fTestOptionValue("ball_radius10_position", "测试其他").asPosition(),
                        Vector3f(1F)
                    )
                    .applyParam(DoubleTestOptionValue("ball_radius11", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius12", "参数大小"), 1.0)
                    .applyParam(DoubleTestOptionValue("ball_radius13", "参数大小"), 1.0)
                    .applyParam(
                        TextureSheetsEnumTestOptionValue("ball_radius14", "测试Enum"),
                        TextureSheetsEnum.ADDITION_BLEND_TRANSLUCENT
                    )
                    .applyTo {
                        it.ballRadius = getParam<Double>("ball_radius")!!
                    }
            }
            .appendOption {
                BlockTexturePropagationTestOption(player)
            }
            .appendOption {
                StarfieldFboBlockTypeTestOption(player)
            }
            .appendOption {
                CooFxModelBlockTestOption(player)
            }
            .appendOption {
                CooFxModelBlockTestOption(
                    player = player,
                    sceneMode = CooFxSceneMode.MODEL_AND_EMITTER,
                    testOptionId = CooFxModelBlockTestOption.CAMERA_OPTION_ID,
                )
            }

    }

    companion object {
        /** CParticle 压测中方块碰撞开关的参数 ID。 */
        private const val CP_BLOCK_COLLISION_ID = "cp_block_collision"

        /** 注册与构建结果共同使用的方块测试组 ID。 */
        @JvmField
        val ID: ResourceLocation = ofID("block-api-test-group-builder")
    }
}
