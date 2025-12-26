package cn.coostack.cooparticlesapi.test.options.particle.client

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroupProvider
import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudParticle
import cn.coostack.cooparticlesapi.particles.impl.ControlableEnchantmentEffect
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.client.particle.ParticleRenderType
import org.joml.Vector3f
import java.util.*
import kotlin.math.PI

class ScaleCircleGroupClient(uuid: UUID, val bindPlayer: UUID) : ControlableParticleGroup(uuid) {
    internal var anTick = 0
    internal var anMaxTick = 30

    class Provider : ControlableParticleGroupProvider {
        override fun createGroup(
            uuid: UUID,
            args: Map<String, ParticleControlerDataBuffer<*>>
        ): ControlableParticleGroup {
            val bindPlayer = args["bind_player"]!!.loadedValue as UUID
            val group = ScaleCircleGroupClient(uuid, bindPlayer)
            group.anTick = args["an_tick"]!!.loadedValue as Int
            group.maxTick = args["max_tick"]!!.loadedValue as Int
            group.tick = args["tick"]!!.loadedValue as Int
            return group
        }

        override fun changeGroup(group: ControlableParticleGroup, args: Map<String, ParticleControlerDataBuffer<*>>) {
        }
    }

    override fun loadParticleLocations(): Map<ParticleRelativeData, RelativeLocation> {
        val res = mutableMapOf<ParticleRelativeData, RelativeLocation>()
//        val points = Math3DUtil.getCycloidGraphic(3.0, 5.0, -2, 3, 120, .5)
//        val points = Math3DUtil.getDiscreteCircleXZ(6.0,1080,3.0)
        val points = ArrayList<RelativeLocation>()
        points.addAll(
            Math3DUtil.getCircleXZ(4.0, 360)
        )
//        val p3 = ArrayList<RelativeLocation>()
//        p3.addAll(
//            PointsBuilder()
//                .addPointsWith {
//                    getPolygonInCircleLocations(3, 120, 4.0)
//                }
//                .rotateAsAxis(PI / 3)
//                .addPointsWithPolygonInCircle(3, 120, 4.0)
//                .create()
//        )
//        p3.forEach {
//            val withData = withEffect({ ParticleDisplayer.withSingle(TestEndRodEffect(it)) }) {
//                color = Math3DUtil.colorOf(1, 120, 243)
////                color = Math3DUtil.colorOf(100, 255, 243)
//                maxAge = 100
//                size = 0.1f
//                particleAlpha = 0.8f
//                textureSheet = ParticleTextureSheet.PARTICLE_SHEET_OPAQUE
//            }
//            res[withData] = it
//        }
        res.putAll(
            PointsBuilder()
                .addPolygonInCircle(3, 120, 4.0)
                .rotateAsAxis(PI / 3)
                .addPolygonInCircle(3, 120, 4.0)
                .addWith {
                    getCircleXZ(5.0, 360).onEach { it.y++ }
                }
                .addWith {
                    getCircleXZ(5.5, 360).onEach { it.y += 1.5 }
                }
                .addWith {
                    connectLines(
                        PointsBuilder.of(
                            getCircleXZ(5.0, 16).onEach { it.y++ }
                        ).rotateAsAxis(-PI / 32).create(),
                        getCircleXZ(5.5, 32).onEach { it.y += 1.5 }, 15
                    ).flatten()
                }
                .createWithParticleEffects {
                    withEffect({
                        ParticleDisplayer.withSingle(ControlableEndRodEffect(it))
                    }) {
                        color = Math3DUtil.colorOf(255, 0, 255)
                        lifetime = 100
                        size = 0.2f
                    }
                }
        )

        res.putAll(
            PointsBuilder()
                .addCircle(4.0, 360)
                .addBall(5.0, 16)
                .createWithParticleEffects {
                    withEffect({
                        ParticleDisplayer.withSingle(ControlableCloudEffect(it))
                    }) {
                        color = Math3DUtil.colorOf(255, 142, 169)
                        lifetime = 100
                    }
                }
        )
        val random = Random(System.currentTimeMillis())

        res.putAll(
            PointsBuilder()
                .addDiscreteCircleXZ(6.0, 720, 7.0)
                .pointsOnEach {
                    it.y += 1
                }
                .createWithParticleEffects {
                    withEffect({ ParticleDisplayer.withSingle(ControlableEnchantmentEffect(it)) }) {
                        color = Vector3f(100 / 255f, 100 / 255f, 255 / 255f)
                        lifetime = 120
                        size = 0.2f
                        particleAlpha = 0.8f
                        textureSheet = ParticleRenderType.PARTICLE_SHEET_LIT
                    }.withControler { c ->
                        c.controlAction {
                            currentAge = random.nextInt(lifetime)
                        }
                    }
                }
        )
        return res
    }


    override fun beforeDisplay(locations: Map<ParticleRelativeData, RelativeLocation>) {
        scale = 1.0 / anMaxTick
    }

    override fun onGroupDisplay() {
        addPreTickAction {
            val player = world!!.getPlayerByUUID(bindPlayer) ?: return@addPreTickAction
            // 同步位置
            teleportTo(player.position())
            // 在变大的过程中也能旋转
            rotateParticlesAsAxis(Math.toRadians(10.0))
            if (tick++ >= maxTick - anMaxTick) {
                scale(scale - 1.0 / anMaxTick)
                return@addPreTickAction
            }
            // 旋转时间设定
            if (anTick++ >= anMaxTick) {
                anTick = anMaxTick
                return@addPreTickAction
            }
            scale(scale + 1.0 / anMaxTick)
        }
    }

}