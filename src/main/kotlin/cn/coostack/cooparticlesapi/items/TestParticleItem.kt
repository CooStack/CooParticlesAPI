package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.CooParticleAPI
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
import cn.coostack.cooparticlesapi.particles.impl.TestEndRodEffect
import cn.coostack.cooparticlesapi.test.particle.emitter.TestEventEmitter
import cn.coostack.cooparticlesapi.test.particle.emitter.event.TestEntityHitEventHandler
import cn.coostack.cooparticlesapi.test.particle.emitter.event.TestOnGroundEventHandler
import cn.coostack.cooparticlesapi.test.particle.emitter.event.TestOnLiquidEventHandler
import cn.coostack.cooparticlesapi.test.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.particle.style.RotateTestStyle
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import net.minecraft.client.particle.ParticleTextureSheet
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class TestParticleItem(settings: Settings) : Item(settings) {

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        if (world.isClient) {
            return TypedActionResult.success(user.getStackInHand(hand))
        }
        testEvents(world, user)
//        CameraUtil.startShakeCamera(240, 0.25)
//        testRomaCircle(world, user)
        // 线性阻力
        return super.use(world, user, hand)
    }

    private fun testEvents(world: World, user: PlayerEntity) {
        val test = TestEventEmitter(user.eyePos, world)
            .apply {
                gravity = PhysicConstant.EARTH_GRAVITY
                shootDirection = user.rotationVector.multiply(1.0)
                addEventHandler(TestEntityHitEventHandler, false)
                addEventHandler(TestOnGroundEventHandler, false)
                addEventHandler(TestOnLiquidEventHandler, false)
            }
        ParticleEmittersManager.spawnEmitters(test)
    }

    private fun testLargeParticles(world: World, user: PlayerEntity) {
        val emitters = SimpleParticleEmitters(user.eyePos, world, ControlableParticleData())
        emitters.count = 4096

        ParticleEmittersManager.spawnEmitters(emitters)
    }

    private fun testRotate(world: World, user: PlayerEntity) {
        val style = RotateTestStyle(user.uuid)
        ParticleStyleManager.spawnStyle(world as ServerWorld, user.pos, style)
    }

    private fun testRomaCircle(world: World, user: PlayerEntity) {
        val style = RomaMagicTestStyle()
        ParticleStyleManager.spawnStyle(world as ServerWorld, user.pos, style)
    }

    private fun testPresets(world: World, user: PlayerEntity) {
        val example = PresetTestEmitters(user.eyePos, world)
            .also {
                it.maxTick = 240
                it.templateData.apply {
                    setTextureSheet(ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT)
                    color = Math3DUtil.colorOf(100, 100, 210)
                    effect = TestEndRodEffect(uuid)
                    size = 0.1f
                }
            }

        ParticleEmittersManager.spawnEmitters(example)
    }

    private fun testDefend(world: World, user: PlayerEntity) {
        val example = DefendClassParticleEmitters(user.uuid, user.eyePos, world)
            .also {
                it.maxTick = 240
                it.templateData.apply {
                    maxAge = 1
                    setTextureSheet(ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT)
                    color = Math3DUtil.colorOf(100, 100, 210)
                    effect = TestEndRodEffect(uuid)
                }
            }

        ParticleEmittersManager.spawnEmitters(example)
    }

    private fun testLightning(world: World, user: PlayerEntity) {
        val example = LightningClassParticleEmitters(user.eyePos, world)
            .also {
                it.maxTick = 120
                it.templateData.apply {
                    maxAge = 20
                    setTextureSheet(ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT)
                    color = Math3DUtil.colorOf(100, 100, 210)
                    effect = TestEndRodEffect(uuid)
                }
            }

        ParticleEmittersManager.spawnEmitters(example)
    }

    private fun testExplode(world: World, user: PlayerEntity) {
        val example = ExplodeClassParticleEmitters(user.eyePos, world)
            .also {
                it.maxTick = 10
                it.templateData.apply {
                    maxAge = 60
                    setTextureSheet(ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT)
                    color = Math3DUtil.colorOf(255, 255, 255)
                    effect = TestEndRodEffect(uuid)
                }
            }

        ParticleEmittersManager.spawnEmitters(example)
    }

    private fun testFire(world: World, user: PlayerEntity) {
        val part1 = FireClassParticleEmitters(
            user.uuid, user.eyePos.add(0.0, -0.5, 0.0), world
        ).also {
            it.maxTick = -1
            it.templateData.apply {
                maxAge = 30
                setTextureSheet(ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT)
                color = Math3DUtil.colorOf(255, 255, 255)
                effect = TestEndRodEffect(uuid)
            }
        }
//        ParticleEmittersManager.spawnEmitters(example)

        ParticleEmittersManager.spawnEmitters(part1)
    }


    private fun testClassEmitters(world: World, user: PlayerEntity) {
        val example = ExampleClassParticleEmitters(
            user.eyePos, world
        ).also {
            it.moveDirection = user.rotationVector.normalize().multiply(0.5)
            it.templateData.apply {
                maxAge = 20
                setTextureSheet(ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT)
                color = Math3DUtil.colorOf(217, 137, 146)
                effect = ControlableCloudEffect(uuid)
            }
        }

        ParticleEmittersManager.spawnEmitters(example)

    }


    private fun testPhysicsEmitters(world: World, user: PlayerEntity, hand: Hand) {
        //        val serverGroup = ScaleCircleGroupServer(user.uuid)
//        ServerParticleGroupManager.addParticleGroup(
//            serverGroup, user.pos, world as ServerWorld
//        )
        val simple = PhysicsParticleEmitters(
            user.eyePos, user.world,
            ControlableParticleData().apply {
//                this.velocity = user.rotationVector.normalize().multiply(0.8)
                this.maxAge = 70
                this.color = Math3DUtil.colorOf(244, 100, 244)
                this.effect = TestEndRodEffect(this.uuid)
            }
        )
        simple.maxTick = 240
        simple.apply {
            this.gravity = 0.0
            this.airDensity = 0.0
//            this.wind = Vec3d(0.0, 1.0, 4.0)
//            this.shootType = EmittersShootTypes.line(user.rotationVector, 0.4)
            this.shootType = EmittersShootTypes.point()
            val v = user.rotationVector.normalize()
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
        CooParticleAPI.scheduler.runTaskTimerMaxTick(5, 240) {
//            simple.templateData.size += 0.05f
            ParticleEmittersManager.updateEmitters(simple)
        }
    }

}