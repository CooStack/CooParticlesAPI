package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.DefendClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.ExampleClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.ExplodeClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.FireClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.LightningClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PhysicsParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PresetTestEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.SimpleParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.type.EmittersShootTypes
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.test.particle.emitter.TestEmitter
import cn.coostack.cooparticlesapi.test.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.particle.emitter.event.TestEntityHitEventHandler
import cn.coostack.cooparticlesapi.test.particle.emitter.event.TestOnGroundEventHandler
import cn.coostack.cooparticlesapi.test.particle.emitter.event.TestOnLiquidEventHandler
import cn.coostack.cooparticlesapi.test.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.particle.style.RotateTestStyle
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.ServerCameraUtil
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class TestParticleItem(settings: Properties) : Item(settings) {

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (world.isClientSide) {
            return InteractionResultHolder.success(user.getItemInHand(hand))
        }
//        testEvents(world, user)
        testEmitter(world as ServerLevel, user as ServerPlayer)
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
                addEventHandler(TestEntityHitEventHandler, false)
                addEventHandler(TestOnGroundEventHandler, false)
                addEventHandler(TestOnLiquidEventHandler, false)
            }
        ParticleEmittersManager.spawnEmitters(test)
    }

    private fun testEmitter(world: ServerLevel, user: ServerPlayer) {
        val emitter = TestEmitter(user.eyePosition, world)
        emitter.apply {
            templateData.apply {
                this.maxAge = 30
                this.age = 10
            }
            particleMoveDirection = user.forward.scale(-7.8)
            emitterMoveDirection = user.forward.scale(1.5)
            maxTick = -1
        }
        ParticleEmittersManager.spawnEmitters(emitter)
    }

    private fun testLargeParticles(world: Level, user: Player) {
        val emitters = SimpleParticleEmitters(user.eyePosition, world, ControlableParticleData())
        emitters.count = 4096

        ParticleEmittersManager.spawnEmitters(emitters)
    }

    private fun testRotate(world: Level, user: Player) {
        val style = RotateTestStyle(user.uuid)
        ParticleStyleManager.spawnStyle(world as ServerLevel, user.position(), style)
    }

    private fun testRomaCircle(world: Level, user: Player) {
        val style = RomaMagicTestStyle()
        ParticleStyleManager.spawnStyle(world as ServerLevel, user.position(), style)
    }

    private fun testPresets(world: Level, user: Player) {
        val example = PresetTestEmitters(user.eyePosition, world)
            .also {
                it.maxTick = 240
                it.templateData.apply {
                    setTextureSheet(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
                    color = Math3DUtil.colorOf(100, 100, 210)
                    effect = ControlableEndRodEffect(uuid)
                    size = 0.1f
                }
            }

        ParticleEmittersManager.spawnEmitters(example)
    }

    private fun testDefend(world: Level, user: Player) {
        val example = DefendClassParticleEmitters(user.uuid, user.eyePosition, world)
            .also {
                it.maxTick = 240
                it.templateData.apply {
                    maxAge = 1
                    setTextureSheet(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
                    color = Math3DUtil.colorOf(100, 100, 210)
                    effect = ControlableEndRodEffect(uuid)
                }
            }

        ParticleEmittersManager.spawnEmitters(example)
    }

    private fun testLightning(world: Level, user: Player) {
        val example = LightningClassParticleEmitters(user.eyePosition, world)
            .also {
                it.maxTick = 120
                it.templateData.apply {
                    maxAge = 10
                    setTextureSheet(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
                    color = Math3DUtil.colorOf(100, 100, 210)
                    effect = ControlableEndRodEffect(uuid)
                }
            }

        ParticleEmittersManager.spawnEmitters(example)
    }

    private fun testExplode(world: Level, user: Player) {
        val example = ExplodeClassParticleEmitters(user.eyePosition, world)
            .also {
                it.maxTick = 10
                it.templateData.apply {
                    maxAge = 60
                    setTextureSheet(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
                    color = Math3DUtil.colorOf(255, 255, 255)
                    effect = ControlableEndRodEffect(uuid)
                }
            }

        ParticleEmittersManager.spawnEmitters(example)
    }

    private fun testFire(world: Level, user: Player) {
        val part1 = FireClassParticleEmitters(
            user.uuid, user.eyePosition.add(0.0, -0.5, 0.0), world
        ).also {
            it.maxTick = -1
            it.templateData.apply {
                maxAge = 30
                setTextureSheet(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
                color = Math3DUtil.colorOf(255, 255, 255)
                effect = ControlableEndRodEffect(uuid)
            }
        }
//        ParticleEmittersManager.spawnEmitters(example)

        ParticleEmittersManager.spawnEmitters(part1)
    }


    private fun testClassEmitters(world: Level, user: Player) {
        val example = ExampleClassParticleEmitters(
            user.eyePosition, world
        ).also {
            it.moveDirection = user.forward.scale(0.5)
            it.templateData.apply {
                maxAge = 20
                setTextureSheet(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)
                color = Math3DUtil.colorOf(217, 137, 146)
                effect = ControlableCloudEffect(uuid)
            }
        }

        ParticleEmittersManager.spawnEmitters(example)

    }


    private fun testPhysicsEmitters(world: Level, user: Player, hand: InteractionHand) {
        //        val serverGroup = ScaleCircleGroupServer(user.uuid)
//        ServerParticleGroupManager.addParticleGroup(
//            serverGroup, user.position(), world as ServerLevel
//        )
        val simple = PhysicsParticleEmitters(
            user.eyePosition, user.level(),
            ControlableParticleData().apply {
//                this.velocity = user.rotationVector.normalize().multiply(0.8)
                this.maxAge = 70
                this.color = Math3DUtil.colorOf(244, 100, 244)
                this.effect = ControlableEndRodEffect(this.uuid)
            }
        )
        simple.maxTick = 240
        simple.apply {
            this.gravity = 0.0
            this.airDensity = 0.0
//            this.wind = Vec3d(0.0, 1.0, 4.0)
//            this.shootType = EmittersShootTypes.line(user.rotationVector, 0.4)
            this.shootType = EmittersShootTypes.point()
            val v = user.forward.normalize()
            evalEmittersXWithT = "(t / 10.0)*${v.x}"
            evalEmittersYWithT = "(t / 10.0)*${v.y}"
            evalEmittersZWithT = "(t / 10.0)*${v.z}"
            setup()
//            this.shootType = EmittersShootTypes.math(
//                "5 * COS(RAD(i * c * 10))",
//                "0",
//                "5 * SIN(RAD(i * c * 10))",
//                "4*(ox-x)/SQRT((ox-x)^2+(oy-z)^2)",
//                "0.1",
//                "4*(oz-z)/SQRT((ox-x)^2+(oy-z)^2)",
//            )
//            this.shootType = EmittersShootTypes.math(
//                "5 * COS(RAD(i * c * 20))",
//                "0",
//                "5 * SIN(RAD(i * c * 20))",
//                "0",
//                "0",
//                "0",
//            )

//            this.shootType = EmittersShootTypes.box(
//                HitBox.of(128.0,10.0,128.0)
//            )
            this.delay = 2
            this.count = 128
            this.countRandom = 0
        }
        ParticleEmittersManager.spawnEmitters(simple)
        CooParticlesAPI.scheduler.runTaskTimerMaxTick(5, 240) {
//            simple.templateData.size += 0.05f
            ParticleEmittersManager.updateEmitters(simple)
        }
    }

}